package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.flywaydb.core.Flyway;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup.Context;
import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrozenSignalsTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");
    private static DriverManagerDataSource dataSource;
    private static DurableCorrelationStore store;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).defaultSchema("evidence").load().migrate();
        store = new DurableCorrelationStore(dataSource, Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @Test
    void protectedSignalChangesConflictEvenWithTheSameFingerprintAcrossMessageAndProducerKeys() throws SQLException {
        var input = input(context(), "BUILD");
        var first = store.prepare(input);
        var before = counts(dataSource);
        for (var signal : List.of(new EvidenceSignal("TESTING", input.signal().summary(), input.signal().content()),
                new EvidenceSignal("BUILD", "Synthetic sensitive candidate summary", input.signal().content()),
                new EvidenceSignal("BUILD", input.signal().summary(), "Synthetic sensitive candidate content"))) {
            for (boolean alias : List.of(false, true)) {
                var changed = new DurableCorrelationStore.Contribution(input.context(),
                        alias ? "other-authenticated-producer" : input.producerKey(),
                        alias ? UUID.randomUUID() : input.messageId(), input.evidenceId(), input.correlationId(),
                        input.inputFingerprint(), "Changed aggregate derivation", signal, TestScopes.SCOPE);
                var failure = assertThrows(IllegalArgumentException.class, () -> store.prepare(changed));
                assertThat(failure.getMessage()).isIn("Correlation message conflicts with its original contribution",
                        "Correlation member conflicts with its original signal");
                assertThat(failure.getCause()).isNull();
                assertThat(counts(dataSource)).isEqualTo(before);
            }
        }
        assertThat(store.prepare(input).snapshot().id()).isEqualTo(first.snapshot().id());
        assertThat(store.prepare(input).snapshot().signal()).isEqualTo(input.signal());
    }

    @Test
    void reconstructsExactHistoricalRevisionWithOriginalTracesDespiteLaterMembersAndAliases() {
        var a = input(context(), "BUILD");
        var first = store.prepare(a);
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var b = input(a.context(), "TESTING");
        var second = store.prepare(b);
        store.confirm(second.snapshot().groupId(), second.snapshot().id());
        var c = input(a.context(), "DEPLOYMENT");
        var third = store.prepare(c);
        var alias = new DurableCorrelationStore.Contribution(a.context(), "alias-producer", UUID.randomUUID(), a.evidenceId(),
                "alias-safe-trace", a.inputFingerprint(), "Different aggregate derivation", a.signal(), TestScopes.SCOPE);
        store.prepare(alias);

        var revision = store.readRevision(a.context(), second.snapshot().id());
        assertThat(revision.contextId()).isEqualTo(second.snapshot().id());
        assertThat(revision.groupId()).isEqualTo(first.snapshot().groupId());
        assertThat(revision.revision()).isEqualTo(2);
        assertThat(revision.signals()).containsExactly(
                new CorrelatedRevision.IntroducedSignal(a.evidenceId(), first.snapshot().id(), a.correlationId(), a.signal()),
                new CorrelatedRevision.IntroducedSignal(b.evidenceId(), second.snapshot().id(), b.correlationId(), b.signal()));
        assertThat(store.readRevision(a.context(), second.snapshot().id())).isEqualTo(revision);
        assertThat(store.readRevision(a.context(), third.snapshot().id()).signals())
                .extracting(CorrelatedRevision.IntroducedSignal::evidenceId).containsExactly(a.evidenceId(), b.evidenceId(), c.evidenceId());
        assertThrows(UnsupportedOperationException.class, () -> revision.signals().clear());
        var source = new ArrayList<>(revision.signals());
        var copy = new CorrelatedRevision(revision.contextId(), revision.groupId(), revision.revision(), source, TestScopes.SCOPE);
        source.clear();
        assertThat(copy).isEqualTo(revision);
    }

    @Test
    void revisionLookupRequiresExactScopeExecutionAndIncident() {
        var input = input(context(), "BUILD");
        var first = store.prepare(input);
        for (var expected : List.of(new Context("other-scope", input.context().executionId(), input.context().incidentId()),
                new Context(input.context().scope(), UUID.randomUUID(), input.context().incidentId()),
                new Context(input.context().scope(), input.context().executionId(), UUID.randomUUID()))) {
            var failure = assertThrows(IllegalStateException.class, () -> store.readRevision(expected, first.snapshot().id()));
            assertThat(failure.getMessage()).isEqualTo("Correlation revision is unavailable");
            assertThat(failure.getCause()).isNull();
        }
        assertThrows(IllegalStateException.class, () -> store.readRevision(input.context(), UUID.randomUUID()));
    }

    @Test
    void damagedFixturesNeverProducePartialOrCrossedRevisionSignals() throws SQLException {
        for (var damage : List.of("missing", "duplicate", "crossed", "multiple", "malformed", "scope")) {
            var a = input(context(), "BUILD");
            var first = store.prepare(a);
            store.confirm(first.snapshot().groupId(), first.snapshot().id());
            var b = input(a.context(), "TESTING");
            var second = store.prepare(b);
            // Isolated synthetic corruption with test-owner privileges; not an application permission.
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.executeUpdate("ALTER TABLE evidence.correlation_receipts DISABLE TRIGGER ALL");
                statement.executeUpdate("ALTER TABLE evidence.correlated_contexts DISABLE TRIGGER ALL");
                switch (damage) {
                    case "scope" -> statement.executeUpdate("UPDATE evidence.correlated_contexts SET scope_github_repository_id = '789' WHERE id = '" + first.snapshot().id() + "'");
                    case "missing" -> statement.executeUpdate("DELETE FROM evidence.correlation_receipts WHERE context_id = '" + first.snapshot().id() + "'");
                    case "duplicate" -> statement.executeUpdate("UPDATE evidence.correlation_receipts SET evidence_id = '" + a.evidenceId() + "' WHERE context_id = '" + second.snapshot().id() + "'");
                    case "crossed" -> statement.executeUpdate("UPDATE evidence.correlated_contexts SET execution_id = '" + UUID.randomUUID() + "' WHERE id = '" + first.snapshot().id() + "'");
                    case "multiple" -> statement.executeUpdate("INSERT INTO evidence.correlation_receipts VALUES ('" + a.context().scope()
                            + "','synthetic-damaged-alias','" + UUID.randomUUID() + "','" + b.evidenceId() + "','safe-trace','"
                            + first.snapshot().id() + "','" + a.inputFingerprint() + "',now())");
                    case "malformed" -> statement.executeUpdate("UPDATE evidence.correlated_contexts SET evidence_ids = ARRAY['" + a.evidenceId() + "','" + a.evidenceId() + "']::uuid[] WHERE id = '" + second.snapshot().id() + "'");
                    default -> throw new AssertionError();
                }
                statement.executeUpdate("ALTER TABLE evidence.correlation_receipts ENABLE TRIGGER ALL");
                statement.executeUpdate("ALTER TABLE evidence.correlated_contexts ENABLE TRIGGER ALL");
                connection.commit();
            }
            var failure = assertThrows(IllegalStateException.class, () -> store.readRevision(a.context(), second.snapshot().id()));
            assertThat(failure.getMessage()).isEqualTo("Correlation revision is unavailable");
            assertThat(failure.getCause()).isNull();
        }
    }

    @Test
    void deferredFailureAfterReceiptRollsBackSignalSnapshotAndReceipt() throws SQLException {
        var input = input(context(), "BUILD");
        var before = counts(dataSource);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE FUNCTION evidence.synthetic_receipt_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN RAISE EXCEPTION 'Synthetic deferred failure' USING ERRCODE = '23514'; END; $$
                    """);
            statement.executeUpdate("""
                    CREATE CONSTRAINT TRIGGER synthetic_receipt_commit_failure AFTER INSERT ON evidence.correlation_receipts
                    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION evidence.synthetic_receipt_commit_failure()
                    """);
            try {
                var failure = assertThrows(IllegalStateException.class, () -> store.prepare(input));
                assertThat(failure.getMessage()).isEqualTo("Correlation preparation could not be persisted");
                assertThat(failure.getCause()).isNull();
                assertThat(counts(dataSource)).isEqualTo(before);
            } finally {
                statement.executeUpdate("DROP TRIGGER synthetic_receipt_commit_failure ON evidence.correlation_receipts");
                statement.executeUpdate("DROP FUNCTION evidence.synthetic_receipt_commit_failure()");
            }
        }
        assertThat(store.prepare(input).snapshot().signal()).isEqualTo(input.signal());
        assertThat(store.prepare(input).snapshot().scope()).isEqualTo(input.scope());
    }

    @Test
    void racingConflictingMemberAliasesFreezeOnlyTheFirstSignal() throws Exception {
        var input = input(context(), "BUILD");
        var alias = new DurableCorrelationStore.Contribution(input.context(), "other-producer", UUID.randomUUID(), input.evidenceId(),
                "other-safe-trace", input.inputFingerprint(), "Other aggregate", new EvidenceSignal("TESTING", "Protected alternate", "Protected alternate content"), TestScopes.SCOPE);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(input, alias).stream().map(contribution -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race did not start");
                try { return (Object) store.prepare(contribution); }
                catch (IllegalArgumentException failure) { return failure; }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            results = List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
        assertThat(results.stream().filter(DurableCorrelationStore.Prepared.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(IllegalArgumentException.class::isInstance).count()).isEqualTo(1);
        var winner = (DurableCorrelationStore.Prepared) results.stream().filter(DurableCorrelationStore.Prepared.class::isInstance).findFirst().orElseThrow();
        assertThat(store.readRevision(input.context(), winner.snapshot().id()).signals())
                .extracting(CorrelatedRevision.IntroducedSignal::signal).containsExactly(winner.snapshot().signal());
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM evidence.correlation_receipts WHERE context_id = '" + winner.snapshot().id() + "'")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void v3RefusesPopulatedV2AndLeavesSchemaDataFunctionsAndHistoryUnchanged() throws SQLException {
        String database = "legacy_signals_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + database);
        }
        var legacySource = new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        var legacy = Flyway.configure().dataSource(legacySource).defaultSchema("evidence").target("2").load();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(2);
        var id = UUID.randomUUID();
        var evidenceId = UUID.randomUUID();
        var group = UUID.randomUUID();
        var execution = UUID.randomUUID();
        var incident = UUID.randomUUID();
        try (var connection = legacySource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO evidence.correlation_groups VALUES ('" + group + "','synthetic-legacy','" + execution + "','" + incident + "',NULL,now(),now())");
            statement.executeUpdate("INSERT INTO evidence.correlated_contexts VALUES ('" + id + "','" + incident + "','" + execution + "',ARRAY['" + evidenceId + "']::uuid[],'Protected legacy summary','safe-legacy-trace',now(),'" + group + "')");
            statement.executeUpdate("INSERT INTO evidence.correlation_receipts VALUES ('synthetic-legacy','synthetic-producer','" + UUID.randomUUID() + "','" + evidenceId + "','safe-legacy-trace','" + id + "','v1:sha256:" + "a".repeat(64) + "',now())");
            connection.commit();
        }
        var before = schemaAndData(legacySource);
        var failure = assertThrows(org.flywaydb.core.api.FlywayException.class,
                () -> Flyway.configure().dataSource(legacySource).defaultSchema("evidence").load().migrate());
        assertThat(failure.getMessage()).contains("requires approved original signal migration mapping").doesNotContain(id.toString());
        assertThat(schemaAndData(legacySource)).isEqualTo(before);
        assertThat(counts(legacySource)).containsExactly(1L, 1L, 1L);
        legacy.validate();
    }

    @Test
    void frozenScopeRejectsReplayAndAliasChangesEvenWithMatchingFingerprint() throws SQLException {
        var input = input(context(), "BUILD");
        var original = store.prepare(input);
        var before = counts(dataSource);
        var scope = input.scope();
        for (var changed : List.of(new ScopeEnvelope(UUID.randomUUID(), "123", "456", null, scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "789", "456", null, scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "123", "789", null, scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "123", "456", UUID.randomUUID(), scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "123", "456", null, "scope-v13"))) {
            for (boolean alias : List.of(false, true)) {
                var candidate = new DurableCorrelationStore.Contribution(input.context(),
                        alias ? "alias-producer" : input.producerKey(), alias ? UUID.randomUUID() : input.messageId(),
                        input.evidenceId(), input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal(), changed);
                var failure = assertThrows(IllegalArgumentException.class, () -> store.prepare(candidate));
                assertThat(failure.getMessage()).isIn("Correlation message conflicts with its original contribution",
                        "Correlation member conflicts with its original scope");
                assertThat(failure.getCause()).isNull();
                assertThat(counts(dataSource)).isEqualTo(before);
            }
        }
        assertThat(store.readRevision(input.context(), original.snapshot().id()).scope()).isEqualTo(scope);
    }

    @Test
    void newRevisionCanAdvanceHistoricalRevisionButCannotCrossResourceBinding() throws SQLException {
        var a = input(context(), "BUILD");
        var first = store.prepare(a);
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var base = input(a.context(), "TESTING");
        var scope = a.scope();
        var before = counts(dataSource);
        for (var changed : List.of(new ScopeEnvelope(UUID.randomUUID(), "123", "456", null, scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "789", "456", null, scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "123", "789", null, scope.revision()),
                new ScopeEnvelope(scope.tenantId(), "123", "456", UUID.randomUUID(), scope.revision()))) {
            assertThrows(IllegalArgumentException.class, () -> store.prepare(withScope(base, changed)));
            assertThat(counts(dataSource)).isEqualTo(before);
        }
        var nextScope = new ScopeEnvelope(scope.tenantId(), scope.installationId(), scope.repositoryId(), null, "scope-v13");
        var second = store.prepare(withScope(base, nextScope));
        var restarted = new DurableCorrelationStore(dataSource, Clock.systemUTC());
        assertThat(restarted.readRevision(a.context(), first.snapshot().id()).scope()).isEqualTo(scope);
        assertThat(restarted.readRevision(a.context(), second.snapshot().id()).scope()).isEqualTo(nextScope);
        assertThat(restarted.readRevision(a.context(), second.snapshot().id()).signals()).hasSize(2);
    }

    @Test
    void nativeGuardRejectsCrossedStableScopeEvenWhenTheOpaqueGroupKeyMatches() throws SQLException {
        var input = input(context(), "BUILD");
        var first = store.prepare(input);
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var before = counts(dataSource);
        for (var changed : List.of("'22222222-2222-2222-2222-222222222222','123','456',NULL,'scope-v12'",
                "'11111111-1111-1111-1111-111111111111','789','456',NULL,'scope-v12'",
                "'11111111-1111-1111-1111-111111111111','123','789',NULL,'scope-v12'",
                "'11111111-1111-1111-1111-111111111111','123','456','22222222-2222-2222-2222-222222222222','scope-v12'")) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                var failure = assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO evidence.correlated_contexts VALUES ('" + UUID.randomUUID() + "','" + input.context().incidentId()
                        + "','" + input.context().executionId() + "',ARRAY['" + input.evidenceId() + "','" + UUID.randomUUID()
                        + "']::uuid[],'Safe summary','safe-trace',now(),'" + first.snapshot().groupId()
                        + "','BUILD','Safe summary','Safe content'," + changed + ")"));
                assertThat(failure.getSQLState()).isEqualTo("23514");
                assertThat(((org.postgresql.util.PSQLException) failure).getServerErrorMessage().getConstraint())
                        .isEqualTo("correlated_contexts_scope_binding_ck");
                connection.rollback();
            }
        }
        assertThat(counts(dataSource)).isEqualTo(before);
    }

    @Test
    void optionalPipelineSurvivesRoundTripAndNativeChecksRejectMalformedScope() throws SQLException {
        var base = input(context(), "BUILD");
        var pipeline = UUID.randomUUID();
        var scope = new ScopeEnvelope(base.scope().tenantId(), "123", "456", pipeline, "scope-v12");
        var prepared = store.prepare(withScope(base, scope));
        assertThat(store.readRevision(base.context(), prepared.snapshot().id()).scope()).isEqualTo(scope);
        assertThat(store.prepare(withScope(base, scope)).snapshot().scope()).isEqualTo(scope);
        for (var invalid : List.of("'11111111-1111-1111-1111-111111111111','01','456',NULL,'scope-v12'",
                "'11111111-1111-1111-1111-111111111111','123','0',NULL,'scope-v12'",
                "'11111111-1111-1111-1111-111111111111','123','456',NULL,'scope revision'")) {
            var candidate = input(context(), "BUILD");
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                var group = UUID.randomUUID();
                statement.executeUpdate("INSERT INTO evidence.correlation_groups VALUES ('" + group + "','" + candidate.context().scope()
                        + "','" + candidate.context().executionId() + "','" + candidate.context().incidentId() + "',NULL,now(),now())");
                var failure = assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO evidence.correlated_contexts VALUES ('" + UUID.randomUUID() + "','" + candidate.context().incidentId()
                        + "','" + candidate.context().executionId() + "',ARRAY['" + candidate.evidenceId() + "']::uuid[],'Safe summary','safe-trace',now(),'"
                        + group + "','BUILD','Safe summary','Safe content'," + invalid + ")"));
                assertThat(failure.getSQLState()).isEqualTo("23514");
                connection.rollback();
            }
        }
    }

    @Test
    void racingAliasesWithDifferentHistoricalScopeFreezeOneOriginalEnvelope() throws Exception {
        var first = input(context(), "BUILD");
        var changed = new ScopeEnvelope(first.scope().tenantId(), "123", "456", null, "scope-v13");
        var alias = new DurableCorrelationStore.Contribution(first.context(), "other-producer", UUID.randomUUID(),
                first.evidenceId(), first.correlationId(), first.inputFingerprint(), first.snapshotSummary(), first.signal(), changed);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, alias).stream().map(candidate -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race did not start");
                try { return (Object) store.prepare(candidate); }
                catch (IllegalArgumentException failure) { return failure; }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var results = List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(DurableCorrelationStore.Prepared.class::isInstance).count()).isEqualTo(1);
            assertThat(results.stream().filter(IllegalArgumentException.class::isInstance).count()).isEqualTo(1);
            var winner = (DurableCorrelationStore.Prepared) results.stream().filter(DurableCorrelationStore.Prepared.class::isInstance).findFirst().orElseThrow();
            assertThat(store.readRevision(first.context(), winner.snapshot().id()).scope()).isEqualTo(winner.snapshot().scope());
        }
    }

    @Test
    void v4RefusesPopulatedV3WithoutChangingSchemaDataGuardOrHistory() throws SQLException {
        var database = "legacy_scope_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + database);
        }
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        var legacy = Flyway.configure().dataSource(source).defaultSchema("evidence").target("3").load();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(3);
        var id = UUID.randomUUID();
        var member = UUID.randomUUID();
        var group = UUID.randomUUID();
        var execution = UUID.randomUUID();
        var incident = UUID.randomUUID();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT INTO evidence.correlation_groups VALUES ('" + group + "','synthetic-legacy','" + execution + "','" + incident + "',NULL,now(),now())");
            statement.executeUpdate("INSERT INTO evidence.correlated_contexts VALUES ('" + id + "','" + incident + "','" + execution + "',ARRAY['" + member
                    + "']::uuid[],'Safe summary','safe-trace',now(),'" + group + "','BUILD','Safe signal summary','Safe signal content')");
            statement.executeUpdate("INSERT INTO evidence.correlation_receipts VALUES ('synthetic-legacy','synthetic-producer','" + UUID.randomUUID()
                    + "','" + member + "','safe-trace','" + id + "','v1:sha256:" + "a".repeat(64) + "',now())");
            connection.commit();
        }
        var before = schemaAndData(source);
        var failure = assertThrows(org.flywaydb.core.api.FlywayException.class,
                () -> Flyway.configure().dataSource(source).defaultSchema("evidence").load().migrate());
        assertThat(failure.getMessage()).contains("requires approved original scope migration mapping").doesNotContain(id.toString());
        assertThat(schemaAndData(source)).isEqualTo(before);
        legacy.validate();
    }

    private static DurableCorrelationStore.Contribution withScope(DurableCorrelationStore.Contribution input, ScopeEnvelope scope) {
        return new DurableCorrelationStore.Contribution(input.context(), input.producerKey(), input.messageId(), input.evidenceId(),
                input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal(), scope);
    }

    private static List<String> schemaAndData(DriverManagerDataSource source) throws SQLException {
        var values = new ArrayList<String>();
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            for (var query : List.of("SELECT row_to_json(c)::text FROM evidence.correlated_contexts c",
                    "SELECT row_to_json(g)::text FROM evidence.correlation_groups g",
                    "SELECT row_to_json(r)::text FROM evidence.correlation_receipts r",
                    "SELECT row_to_json(h)::text FROM evidence.flyway_schema_history h ORDER BY installed_rank",
                    "SELECT column_name || ':' || udt_name || ':' || is_nullable FROM information_schema.columns WHERE table_schema = 'evidence' AND table_name = 'correlated_contexts' ORDER BY ordinal_position",
                    "SELECT pg_get_functiondef('evidence.guard_correlation_snapshot()'::regprocedure)")) {
                try (var rows = statement.executeQuery(query)) { while (rows.next()) values.add(rows.getString(1)); }
            }
        }
        return values;
    }

    private static Context context() { return new Context("synthetic-scope-" + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()); }

    private static DurableCorrelationStore.Contribution input(Context context, String stage) {
        return new DurableCorrelationStore.Contribution(context, "synthetic-producer", UUID.randomUUID(), UUID.randomUUID(),
                "safe-trace-" + UUID.randomUUID(), "v1:sha256:" + "a".repeat(64), "Aggregate operational summary",
                new EvidenceSignal(stage, "Protected résumé α " + stage, "Protected content 🧪 " + stage), TestScopes.SCOPE);
    }

    private static List<Long> counts(DriverManagerDataSource source) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT (SELECT count(*) FROM evidence.correlation_groups),
                            (SELECT count(*) FROM evidence.correlated_contexts),
                            (SELECT count(*) FROM evidence.correlation_receipts)
                     """)) {
            rows.next();
            return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3));
        }
    }
}
