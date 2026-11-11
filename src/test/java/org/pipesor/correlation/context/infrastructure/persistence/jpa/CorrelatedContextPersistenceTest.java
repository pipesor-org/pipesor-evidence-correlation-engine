package org.pipesor.correlation.context.infrastructure.persistence.jpa;

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
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
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
                        "correlation_id:text:NO", "created_at:timestamptz:NO");
            }
            try (var rows = statement.executeQuery("""
                    SELECT table_schema || '.' || table_name FROM information_schema.tables
                    WHERE table_schema NOT IN ('pg_catalog', 'information_schema')
                    ORDER BY table_name
                    """)) {
                var tables = new ArrayList<String>();
                while (rows.next()) tables.add(rows.getString(1));
                assertThat(tables).containsExactly("evidence.correlated_contexts",
                        "evidence.flyway_history_local_correlation");
            }
            try (var rows = statement.executeQuery("""
                    SELECT count(*) FROM information_schema.table_constraints
                    WHERE table_schema = 'evidence' AND table_name = 'correlated_contexts'
                      AND constraint_type = 'FOREIGN KEY'
                    """)) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
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
            assertThat(entityManager.find(CorrelatedContextEntity.class, otherExecution.id()).executionId())
                    .isEqualTo(otherExecution.executionId());
        }
    }

    @Test
    void rollbackLeavesNoSnapshot() {
        var snapshot = snapshot(UUID.randomUUID(), UUID.randomUUID());
        try (var entityManager = entityManagers.createEntityManager()) {
            entityManager.getTransaction().begin();
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
        var values = List.of("'" + UUID.randomUUID() + "'", "'" + UUID.randomUUID() + "'",
                "'" + UUID.randomUUID() + "'", "ARRAY[]::uuid[]", "'Protected evidence references'",
                "'synthetic-correlation'", "TIMESTAMPTZ '2026-11-11 00:00:00+00'");
        try (var connection = connection(); var statement = connection.createStatement()) {
            for (int index = 0; index < values.size(); index++) {
                var invalid = new ArrayList<>(values);
                invalid.set(index, "NULL");
                var failure = assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO evidence.correlated_contexts VALUES (" + String.join(",", invalid) + ")"));
                assertThat(failure.getSQLState()).isEqualTo("23502");
            }
        }
    }

    private static CorrelatedContextEntity snapshot(UUID id, UUID executionId) {
        return new CorrelatedContextEntity(id, UUID.randomUUID(), executionId,
                new UUID[]{UUID.randomUUID(), UUID.randomUUID()},
                "Protected evidence references", "synthetic-correlation:α/01",
                Instant.parse("2026-11-11T00:00:00.123456Z"));
    }

    private static void persist(CorrelatedContextEntity snapshot) {
        try (EntityManager entityManager = entityManagers.createEntityManager()) {
            entityManager.getTransaction().begin();
            entityManager.persist(snapshot);
            entityManager.getTransaction().commit();
        }
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
