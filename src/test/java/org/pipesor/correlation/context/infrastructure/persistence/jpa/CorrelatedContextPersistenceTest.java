package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.CorrelationApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorrelatedContextPersistenceTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");
    private static ConfigurableApplicationContext context;
    private static EntityManagerFactory entityManagers;
    private static Flyway flyway;

    @BeforeAll
    static void startIsolatedDatabase() {
        POSTGRES.start();
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("evidence")
                .table("flyway_history_local_correlation")
                .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        context = new SpringApplicationBuilder(CorrelationApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword());
        entityManagers = context.getBean(EntityManagerFactory.class);
        assertThat(context.getBeansOfType(Flyway.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    @AfterAll
    static void stopIsolatedDatabase() {
        if (context != null) context.close();
        POSTGRES.stop();
    }

    @Test
    void migrationIsRepeatableAndMatchesOnlyOwnedPhysicalModel() throws SQLException {
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = connection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("""
                    SELECT column_name || ':' || udt_name || ':' || is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'evidence' AND table_name = 'correlated_contexts'
                    ORDER BY ordinal_position
                    """)) {
                var columns = new ArrayList<String>();
                while (rows.next()) columns.add(rows.getString(1));
                assertThat(columns).containsExactly("id:uuid:NO", "incident_id:uuid:NO",
                        "execution_id:uuid:NO", "evidence_ids:_uuid:NO", "summary:text:NO",
                        "correlation_id:text:NO", "created_at:timestamptz:NO", "group_id:uuid:NO",
                        "signal_stage:text:NO", "signal_summary:text:NO", "signal_content:text:NO");
            }
            try (var rows = statement.executeQuery("""
                    SELECT table_schema || '.' || table_name FROM information_schema.tables
                    WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                    ORDER BY table_name
                    """)) {
                var tables = new ArrayList<String>();
                while (rows.next()) tables.add(rows.getString(1));
                assertThat(tables).containsExactly("evidence.correlated_contexts",
                        "evidence.correlation_groups", "evidence.correlation_receipts",
                        "evidence.flyway_history_local_correlation");
            }
            try (var rows = statement.executeQuery("""
                    SELECT count(*) FROM information_schema.table_constraints
                    WHERE table_schema = 'evidence' AND constraint_type = 'FOREIGN KEY'
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(3);
            }
        }
    }

    @Test
    void roundTripPreservesProvenanceAndDoesNotMergeSimilarExecutions() {
        var first = snapshot(UUID.randomUUID(), UUID.randomUUID());
        var otherExecution = snapshot(UUID.randomUUID(), UUID.randomUUID());
        persist(first);
        persist(otherExecution);

        try (var entityManager = entityManagers.createEntityManager()) {
            var stored = entityManager.find(CorrelatedContextEntity.class, first.id());
            assertThat(stored.id()).isEqualTo(first.id());
            assertThat(stored.incidentId()).isEqualTo(first.incidentId());
            assertThat(stored.executionId()).isEqualTo(first.executionId());
            assertThat(stored.evidenceIds()).containsExactly(first.evidenceIds());
            assertThat(stored.summary()).isEqualTo(first.summary());
            assertThat(stored.correlationId()).isEqualTo(first.correlationId());
            assertThat(stored.createdAt()).isEqualTo(first.createdAt());
            assertThat(stored.groupId()).isEqualTo(first.groupId());
            assertThat(stored.signal()).isEqualTo(first.signal());
            assertThat(entityManager.find(CorrelatedContextEntity.class, otherExecution.id()).executionId())
                    .isEqualTo(otherExecution.executionId());
        }
    }

    @Test
    void evidenceArrayAliasesCannotChangeSnapshot() {
        var evidenceIds = new UUID[]{UUID.randomUUID()};
        var expectedEvidenceIds = evidenceIds.clone();
        var original = new CorrelatedContextEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                evidenceIds, "Protected evidence references", "synthetic-correlation:aliases",
                Instant.parse("2026-11-11T00:00:00.123456Z"), UUID.randomUUID(), signal());

        evidenceIds[0] = UUID.randomUUID();
        assertThat(original.evidenceIds()).containsExactly(expectedEvidenceIds);
        original.evidenceIds()[0] = UUID.randomUUID();
        assertThat(original.evidenceIds()).containsExactly(expectedEvidenceIds);
        persist(original);

        try (var entityManager = entityManagers.createEntityManager()) {
            assertThat(entityManager.find(CorrelatedContextEntity.class, original.id()).evidenceIds())
                    .containsExactly(expectedEvidenceIds);
        }
    }

    @Test
    void mergingDetachedChangesPreservesEveryStoredSnapshotField() {
        var original = snapshot(UUID.randomUUID(), UUID.randomUUID());
        persist(original);
        var changed = new CorrelatedContextEntity(original.id(), UUID.randomUUID(), UUID.randomUUID(),
                new UUID[]{UUID.randomUUID()}, "Different protected evidence references",
                "synthetic-correlation:changed", original.createdAt().plusSeconds(1), UUID.randomUUID(),
                new EvidenceSignal("TESTING", "Changed protected summary", "Changed protected content"));

        try (var entityManager = entityManagers.createEntityManager()) {
            entityManager.getTransaction().begin();
            entityManager.merge(changed);
            entityManager.getTransaction().commit();
        }

        try (var entityManager = entityManagers.createEntityManager()) {
            var stored = entityManager.find(CorrelatedContextEntity.class, original.id());
            assertThat(stored.id()).isEqualTo(original.id());
            assertThat(stored.incidentId()).isEqualTo(original.incidentId());
            assertThat(stored.executionId()).isEqualTo(original.executionId());
            assertThat(stored.evidenceIds()).containsExactly(original.evidenceIds());
            assertThat(stored.summary()).isEqualTo(original.summary());
            assertThat(stored.correlationId()).isEqualTo(original.correlationId());
            assertThat(stored.createdAt()).isEqualTo(original.createdAt());
            assertThat(stored.groupId()).isEqualTo(original.groupId());
            assertThat(stored.signal()).isEqualTo(original.signal());
        }
    }

    @Test
    void rollbackLeavesNoSnapshot() {
        var snapshot = snapshot(UUID.randomUUID(), UUID.randomUUID());
        try (var entityManager = entityManagers.createEntityManager()) {
            entityManager.getTransaction().begin();
            insertGroup(entityManager, snapshot);
            entityManager.persist(snapshot);
            entityManager.flush();
            entityManager.getTransaction().rollback();
        }
        try (var entityManager = entityManagers.createEntityManager()) {
            assertThat(entityManager.find(CorrelatedContextEntity.class, snapshot.id())).isNull();
        }
    }

    @Test
    void assignedIdCollisionFailsWithoutOverwritingExistingSnapshot() {
        var original = snapshot(UUID.randomUUID(), UUID.randomUUID());
        persist(original);
        var collision = snapshot(original.id(), UUID.randomUUID());
        try (var entityManager = entityManagers.createEntityManager()) {
            entityManager.getTransaction().begin();
            insertGroup(entityManager, collision);
            entityManager.persist(collision);
            var failure = assertThrows(PersistenceException.class, entityManager::flush);
            assertThat(sqlCause(failure).getSQLState()).isEqualTo("23505");
            entityManager.getTransaction().rollback();
        }
        try (var entityManager = entityManagers.createEntityManager()) {
            assertThat(entityManager.find(CorrelatedContextEntity.class, original.id()).executionId())
                    .isEqualTo(original.executionId());
        }
    }

    @Test
    void databaseRejectsNullInEveryRequiredColumn() throws SQLException {
        for (int index = 0; index < 11; index++) {
            var eligible = snapshot(UUID.randomUUID(), UUID.randomUUID());
            try (var connection = connection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.executeUpdate("INSERT INTO evidence.correlation_groups VALUES ('" + eligible.groupId()
                        + "', 'synthetic-scope', '" + eligible.executionId() + "', '" + eligible.incidentId()
                        + "', NULL, now(), now())");
                var values = new ArrayList<>(List.of("'" + eligible.id() + "'", "'" + eligible.incidentId() + "'",
                        "'" + eligible.executionId() + "'", "ARRAY['" + eligible.evidenceIds()[0] + "']::uuid[]",
                        "'Protected evidence references'", "'synthetic-correlation'",
                        "TIMESTAMPTZ '2026-11-11 00:00:00+00'", "'" + eligible.groupId() + "'",
                        "'BUILD'", "'Safe summary'", "'Safe content'"));
                values.set(index, "NULL");
                var failure = assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO evidence.correlated_contexts VALUES (" + String.join(",", values) + ")"));
                assertThat(failure.getSQLState()).isEqualTo("23502");
                connection.rollback();
            }
        }
    }

    @Test
    void databaseRejectsUnknownStageAndBlankSignalText() throws SQLException {
        for (var signal : List.of(List.of("UNKNOWN", "Safe summary", "Safe content"),
                List.of("BUILD", "", "Safe content"), List.of("BUILD", " \t\n", "Safe content"),
                List.of("BUILD", "Safe summary", ""), List.of("BUILD", "Safe summary", " \t\n"))) {
            var eligible = snapshot(UUID.randomUUID(), UUID.randomUUID());
            try (var connection = connection(); var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.executeUpdate("INSERT INTO evidence.correlation_groups VALUES ('" + eligible.groupId()
                        + "', 'synthetic-scope', '" + eligible.executionId() + "', '" + eligible.incidentId()
                        + "', NULL, now(), now())");
                try (var insert = connection.prepareStatement("""
                        INSERT INTO evidence.correlated_contexts
                        VALUES (?, ?, ?, ?, ?, ?, now(), ?, ?, ?, ?)
                        """)) {
                    insert.setObject(1, eligible.id());
                    insert.setObject(2, eligible.incidentId());
                    insert.setObject(3, eligible.executionId());
                    insert.setArray(4, connection.createArrayOf("uuid", eligible.evidenceIds()));
                    insert.setString(5, eligible.summary());
                    insert.setString(6, eligible.correlationId());
                    insert.setObject(7, eligible.groupId());
                    for (int index = 0; index < signal.size(); index++) insert.setString(8 + index, signal.get(index));
                    var failure = assertThrows(SQLException.class, insert::executeUpdate);
                    assertThat(failure.getSQLState()).isEqualTo("23514");
                }
                connection.rollback();
            }
        }
    }

    private static CorrelatedContextEntity snapshot(UUID id, UUID executionId) {
        return new CorrelatedContextEntity(id, UUID.randomUUID(), executionId,
                new UUID[]{UUID.randomUUID()},
                "Protected evidence references", "synthetic-correlation:α/01",
                Instant.parse("2026-11-11T00:00:00.123456Z"), UUID.randomUUID(), signal());
    }

    private static EvidenceSignal signal() { return new EvidenceSignal("BUILD", "Protected résumé α", "Protected build content — prueba 🧪"); }

    private static void persist(CorrelatedContextEntity snapshot) {
        try (EntityManager entityManager = entityManagers.createEntityManager()) {
            entityManager.getTransaction().begin();
            insertGroup(entityManager, snapshot);
            entityManager.persist(snapshot);
            entityManager.flush();
            entityManager.createNativeQuery("""
                    INSERT INTO evidence.correlation_receipts VALUES
                    (:scope, 'synthetic-producer', :message, :evidence, :correlation, :context, :fingerprint, :created)
                    """).setParameter("scope", "synthetic-scope")
                    .setParameter("message", UUID.randomUUID()).setParameter("evidence", snapshot.evidenceIds()[0])
                    .setParameter("correlation", snapshot.correlationId()).setParameter("context", snapshot.id())
                    .setParameter("fingerprint", "v1:sha256:" + "a".repeat(64))
                    .setParameter("created", snapshot.createdAt()).executeUpdate();
            entityManager.getTransaction().commit();
        }
    }

    private static void insertGroup(EntityManager entityManager, CorrelatedContextEntity snapshot) {
        entityManager.createNativeQuery("""
                INSERT INTO evidence.correlation_groups VALUES
                (:id, 'synthetic-scope', :execution, :incident, NULL, :created, :created)
                """).setParameter("id", snapshot.groupId()).setParameter("execution", snapshot.executionId())
                .setParameter("incident", snapshot.incidentId()).setParameter("created", snapshot.createdAt())
                .executeUpdate();
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static SQLException sqlCause(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql;
        }
        throw new AssertionError("Expected a PostgreSQL constraint violation", failure);
    }
}
