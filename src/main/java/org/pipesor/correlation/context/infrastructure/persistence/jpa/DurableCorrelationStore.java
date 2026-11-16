package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup;
import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup.Context;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Own-store transactions only. Callers must authenticate scope/producer, validate references and sanitize summary. */
final class DurableCorrelationStore {
    private final DataSource dataSource;
    private final Clock clock;

    DurableCorrelationStore(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.clock = Objects.requireNonNull(clock);
    }

    record Contribution(Context context, String producerKey, UUID messageId, UUID evidenceId,
                        String correlationId, String inputFingerprint, String snapshotSummary) {
        Contribution {
            Objects.requireNonNull(context, "Context is required");
            Objects.requireNonNull(messageId, "Message ID is required");
            Objects.requireNonNull(evidenceId, "Evidence ID is required");
            if (producerKey == null || producerKey.isBlank() || correlationId == null || correlationId.isBlank()
                    || inputFingerprint == null || !inputFingerprint.matches("v1:sha256:[0-9a-f]{64}")
                    || snapshotSummary == null) {
                throw new IllegalArgumentException("Valid protected contribution metadata is required");
            }
        }
    }

    record Prepared(CorrelatedContextEntity snapshot, int revision, boolean confirmed) { }

    Prepared prepare(Contribution input) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var replay = receipt(connection, input);
                if (replay != null) {
                    connection.commit();
                    return replay;
                }
                var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                var groupId = UUID.randomUUID();
                execute(connection, """
                        INSERT INTO evidence.correlation_groups
                        (id, scope_key, execution_id, incident_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (scope_key, execution_id, incident_id) DO NOTHING
                        """, groupId, input.context().scope(), input.context().executionId(),
                        input.context().incidentId(), now, now);
                UUID confirmedId;
                try (var statement = connection.prepareStatement("""
                        SELECT id, confirmed_context_id FROM evidence.correlation_groups
                        WHERE scope_key = ? AND execution_id = ? AND incident_id = ? FOR UPDATE
                        """)) {
                    bind(statement, input.context().scope(), input.context().executionId(), input.context().incidentId());
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new IllegalStateException("Correlation group is unavailable");
                        groupId = rows.getObject("id", UUID.class);
                        confirmedId = rows.getObject("confirmed_context_id", UUID.class);
                    }
                }
                replay = receipt(connection, input);
                if (replay != null) {
                    connection.commit();
                    return replay;
                }
                var snapshot = originalMember(connection, groupId, input.evidenceId());
                if (snapshot == null) {
                    var previous = latest(connection, groupId);
                    if (previous != null && !previous.id().equals(confirmedId)) {
                        throw new IllegalStateException("Correlation group has an unconfirmed snapshot");
                    }
                    var group = previous == null ? CorrelationGroup.start(groupId, input.context())
                            : CorrelationGroup.restore(groupId, input.context(), Set.of(previous.evidenceIds()));
                    group = group.include(input.context(), input.evidenceId());
                    snapshot = new CorrelatedContextEntity(UUID.randomUUID(), input.context().incidentId(),
                            input.context().executionId(), group.evidenceIds().stream().sorted().toArray(UUID[]::new),
                            input.snapshotSummary(), input.correlationId(), now, groupId);
                    execute(connection, """
                            INSERT INTO evidence.correlated_contexts
                            (id, incident_id, execution_id, evidence_ids, summary, correlation_id, created_at, group_id)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """, snapshot.id(), snapshot.incidentId(), snapshot.executionId(),
                            snapshot.evidenceIds(), snapshot.summary(), snapshot.correlationId(), now, groupId);
                }
                execute(connection, """
                        INSERT INTO evidence.correlation_receipts
                        (scope_key, producer_key, message_id, evidence_id, correlation_id, context_id, input_fingerprint, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, input.context().scope(), input.producerKey(), input.messageId(), input.evidenceId(),
                        input.correlationId(), snapshot.id(), input.inputFingerprint(), now);
                var result = prepared(connection, snapshot, confirmedId);
                connection.commit();
                return result;
            } catch (SQLException failure) {
                connection.rollback();
                if ("23505".equals(failure.getSQLState())) {
                    // Only a scoped message-key race may recover; no generic transaction retry.
                    var replay = receipt(connection, input);
                    connection.rollback();
                    if (replay != null) return replay;
                }
                throw new IllegalStateException("Correlation preparation could not be persisted");
            } catch (RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Correlation persistence is unavailable");
        }
    }

    /** Technical acknowledgement only; an authoritative downstream contract must supply this later. */
    void confirm(UUID groupId, UUID contextId) {
        Objects.requireNonNull(groupId, "Group ID is required");
        Objects.requireNonNull(contextId, "Context ID is required");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant updated;
                UUID confirmed;
                try (var statement = connection.prepareStatement("""
                        SELECT confirmed_context_id, updated_at FROM evidence.correlation_groups WHERE id = ? FOR UPDATE
                        """)) {
                    bind(statement, groupId);
                    try (var rows = statement.executeQuery()) {
                        if (!rows.next()) throw new IllegalArgumentException("Correlation group is unavailable");
                        confirmed = rows.getObject(1, UUID.class);
                        updated = rows.getObject(2, OffsetDateTime.class).toInstant();
                    }
                }
                var requested = snapshot(connection, contextId);
                if (requested == null || !groupId.equals(requested.groupId())) {
                    throw new IllegalArgumentException("Correlation confirmation does not belong to this group");
                }
                int previous = confirmed == null ? 0 : snapshot(connection, confirmed).evidenceIds().length;
                if (requested.evidenceIds().length > previous) {
                    if (requested.evidenceIds().length != previous + 1) {
                        throw new IllegalStateException("Correlation confirmation must advance one revision");
                    }
                    var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    execute(connection, """
                            UPDATE evidence.correlation_groups SET confirmed_context_id = ?, updated_at = ? WHERE id = ?
                            """, contextId, now.isBefore(updated) ? updated : now, groupId);
                }
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                if (failure instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Correlation confirmation could not be persisted");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Correlation persistence is unavailable");
        }
    }

    private Prepared receipt(Connection connection, Contribution input) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT c.*, r.evidence_id AS source_evidence, r.correlation_id AS source_correlation,
                       r.input_fingerprint, g.confirmed_context_id
                FROM evidence.correlation_receipts r
                JOIN evidence.correlated_contexts c ON c.id = r.context_id
                JOIN evidence.correlation_groups g ON g.id = c.group_id
                WHERE r.scope_key = ? AND r.producer_key = ? AND r.message_id = ?
                """)) {
            bind(statement, input.context().scope(), input.producerKey(), input.messageId());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                var stored = readSnapshot(rows);
                if (!stored.executionId().equals(input.context().executionId())
                        || !stored.incidentId().equals(input.context().incidentId())
                        || !rows.getObject("source_evidence", UUID.class).equals(input.evidenceId())
                        || !rows.getString("source_correlation").equals(input.correlationId())
                        || !rows.getString("input_fingerprint").equals(input.inputFingerprint())) {
                    throw new IllegalArgumentException("Correlation message conflicts with its original contribution");
                }
                return prepared(connection, stored, rows.getObject("confirmed_context_id", UUID.class));
            }
        }
    }

    private static Prepared prepared(Connection connection, CorrelatedContextEntity snapshot, UUID confirmedId)
            throws SQLException {
        int revision = snapshot.evidenceIds().length;
        return new Prepared(snapshot, revision, confirmedId != null
                && snapshot(connection, confirmedId).evidenceIds().length >= revision);
    }

    private static CorrelatedContextEntity originalMember(Connection connection, UUID groupId, UUID member)
            throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT c.* FROM evidence.correlation_receipts r
                JOIN evidence.correlated_contexts c ON c.id = r.context_id
                WHERE c.group_id = ? AND r.evidence_id = ? ORDER BY cardinality(c.evidence_ids) LIMIT 1
                """)) {
            bind(statement, groupId, member);
            try (var rows = statement.executeQuery()) { return rows.next() ? readSnapshot(rows) : null; }
        }
    }

    private static CorrelatedContextEntity latest(Connection connection, UUID groupId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT * FROM evidence.correlated_contexts WHERE group_id = ? ORDER BY cardinality(evidence_ids) DESC LIMIT 1
                """)) {
            bind(statement, groupId);
            try (var rows = statement.executeQuery()) { return rows.next() ? readSnapshot(rows) : null; }
        }
    }

    private static CorrelatedContextEntity snapshot(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM evidence.correlated_contexts WHERE id = ?")) {
            bind(statement, id);
            try (var rows = statement.executeQuery()) { return rows.next() ? readSnapshot(rows) : null; }
        }
    }

    private static CorrelatedContextEntity readSnapshot(ResultSet rows) throws SQLException {
        var evidence = rows.getArray("evidence_ids");
        try {
            return new CorrelatedContextEntity(rows.getObject("id", UUID.class), rows.getObject("incident_id", UUID.class),
                    rows.getObject("execution_id", UUID.class), (UUID[]) evidence.getArray(),
                    rows.getString("summary"), rows.getString("correlation_id"),
                    rows.getObject("created_at", OffsetDateTime.class).toInstant(), rows.getObject("group_id", UUID.class));
        } finally {
            evidence.free();
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        var arrays = new java.util.ArrayList<java.sql.Array>();
        try {
            for (int index = 0; index < values.length; index++) {
                if (values[index] instanceof UUID[] ids) {
                    var array = connection.createArrayOf("uuid", ids);
                    arrays.add(array);
                    values[index] = array;
                }
            }
            try (var statement = connection.prepareStatement(sql)) {
                bind(statement, values);
                statement.executeUpdate();
            }
        } finally {
            for (var array : arrays) array.free();
        }
    }

    private static void bind(java.sql.PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            var value = values[index];
            if (value instanceof Instant instant) value = instant.atOffset(ZoneOffset.UTC);
            statement.setObject(index + 1, value);
        }
    }
}
