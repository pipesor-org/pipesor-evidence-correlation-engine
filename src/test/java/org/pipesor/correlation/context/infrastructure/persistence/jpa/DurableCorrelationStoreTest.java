package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup.Context;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurableCorrelationStoreTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00.123456789Z");
    private static DriverManagerDataSource dataSource;
    private static DurableCorrelationStore store;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).defaultSchema("evidence").load().migrate();
        store = new DurableCorrelationStore(dataSource, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterAll
    static void stopDatabase() { POSTGRES.stop(); }

    @Test
    void matchingReplayRecoversTheFirstFrozenSnapshotAndItsTechnicalState() {
        var input = input(context());
        var first = store.prepare(input);
        var replay = store.prepare(withSummary(input, "Changed derived synthetic summary"));
        assertThat(replay.snapshot().id()).isEqualTo(first.snapshot().id());
        assertThat(replay.snapshot().summary()).isEqualTo(input.snapshotSummary());
        assertThat(replay.snapshot().signal()).isEqualTo(input.signal());
        assertThat(replay.snapshot().evidenceIds()).containsExactly(input.evidenceId());
        assertThat(replay.snapshot().correlationId()).isEqualTo(input.correlationId());
        assertThat(replay.snapshot().createdAt()).isEqualTo(Instant.parse("2026-11-11T00:00:00.123456Z"));
        assertThat(replay.revision()).isEqualTo(1);
        assertThat(replay.confirmed()).isFalse();
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        assertThat(store.prepare(input).confirmed()).isTrue();
    }

    @Test
    void conflictingReplayCannotCreateGroupsSnapshotsOrReceipts() throws SQLException {
        var input = input(context());
        var original = store.prepare(input);
        var before = counts();
        var conflicting = List.of(
                new DurableCorrelationStore.Contribution(new Context(input.context().scope(), UUID.randomUUID(),
                        input.context().incidentId()), input.producerKey(), input.messageId(), input.evidenceId(),
                        input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal()),
                new DurableCorrelationStore.Contribution(new Context(input.context().scope(), input.context().executionId(),
                        UUID.randomUUID()), input.producerKey(), input.messageId(), input.evidenceId(),
                        input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal()),
                new DurableCorrelationStore.Contribution(input.context(), input.producerKey(), input.messageId(), UUID.randomUUID(),
                        input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal()),
                new DurableCorrelationStore.Contribution(input.context(), input.producerKey(), input.messageId(), input.evidenceId(),
                        "other-safe-correlation", input.inputFingerprint(), input.snapshotSummary(), input.signal()),
                new DurableCorrelationStore.Contribution(input.context(), input.producerKey(), input.messageId(), input.evidenceId(),
                        input.correlationId(), "v1:sha256:" + "b".repeat(64), input.snapshotSummary(), input.signal()));
        for (var changed : conflicting) {
            assertThrows(IllegalArgumentException.class, () -> store.prepare(changed));
            assertThat(counts()).isEqualTo(before);
        }
        assertThat(store.prepare(input).snapshot().id()).isEqualTo(original.snapshot().id());
    }

    @Test
    void existingMemberUnderNewMessageAndProducerKeepsItsOriginalRevisionAndProvenance() throws SQLException {
        var input = input(context());
        var first = store.prepare(input);
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var secondInput = input(input.context());
        var second = store.prepare(secondInput);
        var alias = new DurableCorrelationStore.Contribution(input.context(), "other-authenticated-producer", UUID.randomUUID(),
                input.evidenceId(), "alias-safe-correlation", "v1:sha256:" + "c".repeat(64), "Changed derived summary", input.signal());
        var recovered = store.prepare(alias);
        assertThat(recovered.snapshot().id()).isEqualTo(first.snapshot().id());
        assertThat(recovered.snapshot().summary()).isEqualTo(first.snapshot().summary());
        assertThat(recovered.revision()).isEqualTo(1);
        assertThat(recovered.confirmed()).isTrue();
        assertThat(second.snapshot().evidenceIds()).containsExactlyInAnyOrder(input.evidenceId(), secondInput.evidenceId());
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
                SELECT evidence_id, correlation_id, context_id FROM evidence.correlation_receipts
                WHERE scope_key = ? AND producer_key = ? AND message_id = ?
                """)) {
            statement.setString(1, alias.context().scope());
            statement.setString(2, alias.producerKey());
            statement.setObject(3, alias.messageId());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1, UUID.class)).isEqualTo(alias.evidenceId());
                assertThat(rows.getString(2)).isEqualTo(alias.correlationId());
                assertThat(rows.getObject(3, UUID.class)).isEqualTo(first.snapshot().id());
            }
        }
        assertThat(snapshotCount(first.snapshot().groupId())).isEqualTo(2);
    }

    @Test
    void identicalSignalsAndMessageKeysRemainIsolatedByTrustedScopeAndExecution() {
        var input = input(context());
        var first = store.prepare(input);
        var otherScope = new DurableCorrelationStore.Contribution(
                new Context("other-scope-" + UUID.randomUUID(), input.context().executionId(), input.context().incidentId()),
                input.producerKey(), input.messageId(), input.evidenceId(), input.correlationId(), input.inputFingerprint(),
                input.snapshotSummary(), input.signal());
        var other = store.prepare(otherScope);
        assertThat(other.snapshot().groupId()).isNotEqualTo(first.snapshot().groupId());
        var execution = new Context(input.context().scope(), UUID.randomUUID(), input.context().incidentId());
        var third = store.prepare(new DurableCorrelationStore.Contribution(execution, input.producerKey(), UUID.randomUUID(),
                input.evidenceId(), input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal()));
        assertThat(third.snapshot().groupId()).isNotIn(first.snapshot().groupId(), other.snapshot().groupId());
    }

    @Test
    void pendingPreparationBlocksGrowthUntilLocalAcknowledgement() throws SQLException {
        var firstInput = input(context());
        var first = store.prepare(firstInput);
        var secondInput = input(firstInput.context());
        var before = counts();
        assertThrows(IllegalStateException.class, () -> store.prepare(secondInput));
        assertThat(counts()).isEqualTo(before);
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var second = store.prepare(secondInput);
        assertThat(second.snapshot().groupId()).isEqualTo(first.snapshot().groupId());
        assertThat(second.revision()).isEqualTo(2);
        assertThat(second.snapshot().evidenceIds()).containsExactlyInAnyOrder(firstInput.evidenceId(), secondInput.evidenceId());
        assertThat(first.snapshot().evidenceIds()).containsExactly(firstInput.evidenceId());
    }

    @Test
    void concurrentSameMessageRecoversOneDurableOperation() throws Exception {
        var input = input(context());
        var results = race(() -> store.prepare(input), () -> store.prepare(withSummary(input, "Other synthetic derivation")));
        assertThat(results.get(0).snapshot().id()).isEqualTo(results.get(1).snapshot().id());
        assertThat(results.get(0).snapshot().summary()).isEqualTo(results.get(1).snapshot().summary());
        assertThat(snapshotCount(results.get(0).snapshot().groupId())).isEqualTo(1);
        assertThat(receiptCount(input.context().scope())).isEqualTo(1);
    }

    @Test
    void concurrentNewMembersProduceOnePendingWinnerAndRetryGrowsWithoutFork() throws Exception {
        var firstInput = input(context());
        var secondInput = input(firstInput.context());
        var results = race(() -> attempt(firstInput), () -> attempt(secondInput));
        assertThat(results.stream().filter(DurableCorrelationStore.Prepared.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(IllegalStateException.class::isInstance).count()).isEqualTo(1);
        var winner = (DurableCorrelationStore.Prepared) results.stream()
                .filter(DurableCorrelationStore.Prepared.class::isInstance).findFirst().orElseThrow();
        assertThat(snapshotCount(winner.snapshot().groupId())).isEqualTo(1);
        store.confirm(winner.snapshot().groupId(), winner.snapshot().id());
        var loserInput = winner.snapshot().evidenceIds()[0].equals(firstInput.evidenceId()) ? secondInput : firstInput;
        var grown = store.prepare(loserInput);
        assertThat(grown.snapshot().groupId()).isEqualTo(winner.snapshot().groupId());
        assertThat(grown.snapshot().evidenceIds()).containsExactlyInAnyOrder(firstInput.evidenceId(), secondInput.evidenceId());
        assertThat(snapshotCount(grown.snapshot().groupId())).isEqualTo(2);
    }

    @Test
    void concurrentDifferentMessagesForSameMemberShareOneOriginalSnapshot() throws Exception {
        var firstInput = input(context());
        var alias = new DurableCorrelationStore.Contribution(firstInput.context(), firstInput.producerKey(), UUID.randomUUID(),
                firstInput.evidenceId(), "another-safe-correlation", firstInput.inputFingerprint(), "Changed summary", firstInput.signal());
        var results = race(() -> store.prepare(firstInput), () -> store.prepare(alias));
        assertThat(results.get(0).snapshot().id()).isEqualTo(results.get(1).snapshot().id());
        assertThat(snapshotCount(results.get(0).snapshot().groupId())).isEqualTo(1);
        assertThat(receiptCount(firstInput.context().scope())).isEqualTo(2);
    }

    @Test
    void concurrentScopedMessageInDifferentContextsConflictsWithoutLeavingAnEmptyGroup() throws Exception {
        var firstInput = input(context());
        var other = new DurableCorrelationStore.Contribution(new Context(firstInput.context().scope(), UUID.randomUUID(),
                firstInput.context().incidentId()), firstInput.producerKey(), firstInput.messageId(), firstInput.evidenceId(),
                firstInput.correlationId(), firstInput.inputFingerprint(), firstInput.snapshotSummary(), firstInput.signal());
        var before = counts();
        var coordinated = new ReceiptRaceDataSource();
        var racingStore = new DurableCorrelationStore(coordinated, Clock.fixed(NOW, ZoneOffset.UTC));
        List<Object> results;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> attemptConflict(racingStore, firstInput));
            var second = executor.submit(() -> attemptConflict(racingStore, other));
            try {
                assertThat(coordinated.emptyReads.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                coordinated.release.countDown();
            }
            results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertThat(coordinated.uniqueConflicts.get()).isEqualTo(1);
        assertThat(results.stream().filter(DurableCorrelationStore.Prepared.class::isInstance).count()).isEqualTo(1);
        assertThat(results.stream().filter(IllegalArgumentException.class::isInstance).count()).isEqualTo(1);
        assertThat(counts()).containsExactly(before.get(0) + 1, before.get(1) + 1, before.get(2) + 1);
    }

    @Test
    void acknowledgementsNeverRegressOrTouchUpdatedTimeAndRejectForeignGroups() throws SQLException {
        var firstInput = input(context());
        var first = store.prepare(firstInput);
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var second = store.prepare(input(firstInput.context()));
        var laterStore = new DurableCorrelationStore(dataSource, Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));
        laterStore.confirm(second.snapshot().groupId(), second.snapshot().id());
        var before = groupProgress(first.snapshot().groupId());
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        store.confirm(second.snapshot().groupId(), second.snapshot().id());
        assertThat(groupProgress(first.snapshot().groupId())).isEqualTo(before);
        var foreign = store.prepare(input(context()));
        assertThrows(IllegalArgumentException.class,
                () -> store.confirm(first.snapshot().groupId(), foreign.snapshot().id()));
        assertThat(groupProgress(first.snapshot().groupId())).isEqualTo(before);
    }

    @Test
    void receiptInsertFailureRollsBackGroupAndSnapshotTogether() throws SQLException {
        var input = input(context());
        var rejected = new DurableCorrelationStore.Contribution(input.context(), "synthetic-rejected-producer", input.messageId(),
                input.evidenceId(), input.correlationId(), input.inputFingerprint(), input.snapshotSummary(), input.signal());
        var before = counts();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE evidence.correlation_receipts ADD CONSTRAINT synthetic_failure CHECK (producer_key <> 'synthetic-rejected-producer')");
            try {
                var failure = assertThrows(IllegalStateException.class, () -> store.prepare(rejected));
                assertThat(failure.getMessage()).isEqualTo("Correlation preparation could not be persisted");
                assertThat(failure.getCause()).isNull();
                assertThat(counts()).isEqualTo(before);
            } finally {
                statement.executeUpdate("ALTER TABLE evidence.correlation_receipts DROP CONSTRAINT synthetic_failure");
            }
        }
        assertThat(store.prepare(rejected).revision()).isEqualTo(1);
    }

    @Test
    void databaseRejectsInvalidEvidenceSetsAndMissingSourceReceiptAtomically() throws SQLException {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var arrays = List.of("ARRAY[]::uuid[]", "ARRAY[NULL]::uuid[]", "ARRAY['" + a + "','" + a + "']::uuid[]",
                "ARRAY[['" + a + "'],['" + b + "']]::uuid[]", "'[0:0]={" + a + "}'::uuid[]",
                "ARRAY['" + a + "','" + b + "']::uuid[]");
        for (var array : arrays) {
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                var group = rawGroup(connection);
                var failure = assertThrows(SQLException.class, () -> rawSnapshot(connection, group, UUID.randomUUID(), array));
                assertThat(failure.getSQLState()).isEqualTo("23514");
                assertThat(failure.getMessage()).doesNotContain(a.toString(), group.id().toString());
                connection.rollback();
            }
        }
        var before = counts();
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            var group = rawGroup(connection);
            rawSnapshot(connection, group, UUID.randomUUID(), "ARRAY['" + a + "']::uuid[]");
            var failure = assertThrows(SQLException.class, connection::commit);
            assertThat(failure.getSQLState()).isEqualTo("23514");
            assertThat(failure.getMessage()).contains("requires its new member receipt");
            connection.rollback();
        }
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void databaseProtectsChainOriginalReceiptMembershipAndLogicalIdentity() throws SQLException {
        var input = input(context());
        var first = store.prepare(input);
        var group = new RawGroup(first.snapshot().groupId(), input.context());
        var b = UUID.randomUUID();
        assertRejected("INSERT INTO evidence.correlated_contexts VALUES ('" + UUID.randomUUID() + "', '"
                + input.context().incidentId() + "','" + input.context().executionId() + "', ARRAY['"
                + input.evidenceId() + "','" + b + "']::uuid[], 'Safe summary', 'safe-correlation', now(), '" + group.id() + "','BUILD','Safe summary','Safe content')", "23514");
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var second = store.prepare(input(input.context()));
        var receipt = "INSERT INTO evidence.correlation_receipts VALUES ('" + input.context().scope()
                + "', 'synthetic-producer','" + UUID.randomUUID() + "','" + input.evidenceId()
                + "','safe-correlation','" + second.snapshot().id() + "','v1:sha256:" + "a".repeat(64) + "',now())";
        assertRejected(receipt, "23514");
        assertRejected(receipt.replace(input.context().scope(), "wrong-scope"), "23514");
        assertRejected(receipt.replace(input.evidenceId().toString(), UUID.randomUUID().toString()), "23514");
        assertRejected("INSERT INTO evidence.correlated_contexts VALUES ('" + UUID.randomUUID() + "', '"
                + UUID.randomUUID() + "','" + input.context().executionId() + "', ARRAY['" + b
                + "']::uuid[], 'Safe summary', 'safe-correlation', now(), '" + group.id() + "','BUILD','Safe summary','Safe content')", "23514");
        assertThat(snapshotCount(group.id())).isEqualTo(2);
    }

    @Test
    void databaseRejectsHistoryMutationAndGroupKeyTimeOrPointerChanges() throws SQLException {
        var firstInput = input(context());
        var first = store.prepare(firstInput);
        for (var table : List.of("correlated_contexts", "correlation_receipts", "correlation_groups")) {
            for (var operation : List.of("DELETE FROM evidence." + table, "TRUNCATE evidence." + table + " CASCADE")) {
                assertRejected(operation, "23514");
            }
        }
        assertRejected("UPDATE evidence.correlated_contexts SET summary = 'Changed safe summary'", "23514");
        assertRejected("UPDATE evidence.correlation_receipts SET correlation_id = 'changed-safe-correlation'", "23514");
        for (var assignment : List.of("scope_key = 'changed-scope'", "id = '" + UUID.randomUUID() + "'",
                "execution_id = '" + UUID.randomUUID() + "'", "incident_id = '" + UUID.randomUUID() + "'",
                "created_at = created_at + interval '1 second'", "updated_at = updated_at + interval '1 second'",
                "updated_at = updated_at - interval '1 second'")) {
            assertRejected("UPDATE evidence.correlation_groups SET " + assignment + " WHERE id = '" + first.snapshot().groupId() + "'", "23514");
        }
        store.confirm(first.snapshot().groupId(), first.snapshot().id());
        var second = store.prepare(input(firstInput.context()));
        store.confirm(second.snapshot().groupId(), second.snapshot().id());
        assertRejected("UPDATE evidence.correlation_groups SET confirmed_context_id = '" + first.snapshot().id()
                + "' WHERE id = '" + first.snapshot().groupId() + "'", "23514");
        var foreign = store.prepare(input(context()));
        assertRejected("UPDATE evidence.correlation_groups SET confirmed_context_id = '" + foreign.snapshot().id()
                + "' WHERE id = '" + first.snapshot().groupId() + "'", "23514");
    }

    @Test
    void migrationRefusesLegacySnapshotsWithoutChangingTheirSchemaOrData() throws SQLException {
        String database = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + database);
        }
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + database);
        var legacySource = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        var legacy = Flyway.configure().dataSource(legacySource).defaultSchema("evidence").target("1").load();
        assertThat(legacy.migrate().migrationsExecuted).isEqualTo(1);
        var id = UUID.randomUUID();
        try (var connection = legacySource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO evidence.correlated_contexts VALUES ('" + id + "','" + UUID.randomUUID()
                    + "','" + UUID.randomUUID() + "', ARRAY['" + UUID.randomUUID()
                    + "']::uuid[], 'Legacy protected summary', 'safe-legacy-correlation', now())");
        }
        var failure = assertThrows(org.flywaydb.core.api.FlywayException.class,
                () -> Flyway.configure().dataSource(legacySource).defaultSchema("evidence").load().migrate());
        assertThat(failure.getMessage()).contains("requires an approved migration mapping").doesNotContain(id.toString());
        try (var connection = legacySource.getConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT id, summary FROM evidence.correlated_contexts")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1, UUID.class)).isEqualTo(id);
                assertThat(rows.getString(2)).isEqualTo("Legacy protected summary");
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'evidence' AND table_name = 'correlated_contexts' ORDER BY ordinal_position
                    """)) {
                var columns = new ArrayList<String>();
                while (rows.next()) columns.add(rows.getString(1));
                assertThat(columns).containsExactly("id", "incident_id", "execution_id", "evidence_ids", "summary", "correlation_id", "created_at");
            }
            try (var rows = statement.executeQuery("SELECT to_regclass('evidence.correlation_groups'), to_regclass('evidence.correlation_receipts')")) {
                rows.next();
                assertThat(rows.getObject(1)).isNull();
                assertThat(rows.getObject(2)).isNull();
            }
        }
        legacy.validate();
    }

    private static Object attempt(DurableCorrelationStore.Contribution input) {
        try { return store.prepare(input); } catch (IllegalStateException failure) { return failure; }
    }

    private static Object attemptConflict(DurableCorrelationStore target, DurableCorrelationStore.Contribution input) {
        try { return target.prepare(input); } catch (IllegalArgumentException failure) { return failure; }
    }

    private static final class ReceiptRaceDataSource extends DelegatingDataSource {
        private final CountDownLatch emptyReads = new CountDownLatch(2);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger uniqueConflicts = new AtomicInteger();

        private ReceiptRaceDataSource() { super(dataSource); }

        @Override
        public Connection getConnection() throws SQLException {
            var connection = super.getConnection();
            var receiptReads = new AtomicInteger();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        var result = invoke(method, connection, args);
                        if (!method.getName().equals("prepareStatement")) return result;
                        var sql = (String) args[0];
                        boolean read = sql.contains("FROM evidence.correlation_receipts r");
                        boolean insert = sql.contains("INSERT INTO evidence.correlation_receipts");
                        if (!read && !insert) return result;
                        var statement = (PreparedStatement) result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                (statementProxy, operation, parameters) -> {
                                    Object value;
                                    try {
                                        value = invoke(operation, statement, parameters);
                                    } catch (SQLException failure) {
                                        if (insert && "23505".equals(failure.getSQLState())) uniqueConflicts.incrementAndGet();
                                        throw failure;
                                    }
                                    if (read && operation.getName().equals("executeQuery") && receiptReads.incrementAndGet() == 2) {
                                        assertThat(((ResultSet) value).next()).isFalse();
                                        emptyReads.countDown();
                                        if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Receipt race was not released");
                                    }
                                    return value;
                                });
                    });
        }
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<T>>();
            for (var action : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent test did not start");
                    return action.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private static Context context() { return new Context("synthetic-scope-" + UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()); }

    private static DurableCorrelationStore.Contribution input(Context context) {
        return new DurableCorrelationStore.Contribution(context, "synthetic-authenticated-producer", UUID.randomUUID(),
                UUID.randomUUID(), "synthetic-safe-correlation", "v1:sha256:" + "a".repeat(64), "Protected evidence references", new EvidenceSignal("BUILD", "Protected build summary", "Protected build content"));
    }

    private static DurableCorrelationStore.Contribution withSummary(DurableCorrelationStore.Contribution input, String summary) {
        return new DurableCorrelationStore.Contribution(input.context(), input.producerKey(), input.messageId(), input.evidenceId(),
                input.correlationId(), input.inputFingerprint(), summary, input.signal());
    }

    private static List<Long> counts() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("""
                     SELECT (SELECT count(*) FROM evidence.correlation_groups),
                            (SELECT count(*) FROM evidence.correlated_contexts),
                            (SELECT count(*) FROM evidence.correlation_receipts)
                     """)) {
            rows.next();
            return List.of(rows.getLong(1), rows.getLong(2), rows.getLong(3));
        }
    }

    private static int snapshotCount(UUID group) throws SQLException {
        return count("SELECT count(*) FROM evidence.correlated_contexts WHERE group_id = '" + group + "'");
    }

    private static int receiptCount(String scope) throws SQLException {
        return count("SELECT count(*) FROM evidence.correlation_receipts WHERE scope_key = '" + scope + "'");
    }

    private static int count(String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static List<Object> groupProgress(UUID group) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT confirmed_context_id, updated_at FROM evidence.correlation_groups WHERE id = '" + group + "'")) {
            rows.next();
            return List.of(rows.getObject(1), rows.getObject(2));
        }
    }

    private record RawGroup(UUID id, Context context) { }

    private static RawGroup rawGroup(Connection connection) throws SQLException {
        var group = new RawGroup(UUID.randomUUID(), context());
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO evidence.correlation_groups VALUES ('" + group.id() + "','"
                    + group.context().scope() + "','" + group.context().executionId() + "','" + group.context().incidentId()
                    + "', NULL, now(), now())");
        }
        return group;
    }

    private static void rawSnapshot(Connection connection, RawGroup group, UUID id, String array) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO evidence.correlated_contexts VALUES ('" + id + "','" + group.context().incidentId()
                    + "','" + group.context().executionId() + "'," + array
                    + ",'Safe protected summary','safe-correlation',now(),'" + group.id() + "','BUILD','Safe summary','Safe content')");
        }
    }

    private static void assertRejected(String sql, String sqlState) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            var failure = assertThrows(SQLException.class, () -> { statement.executeUpdate(sql); connection.commit(); });
            assertThat(failure.getSQLState()).isEqualTo(sqlState);
            connection.rollback();
        }
    }
}
