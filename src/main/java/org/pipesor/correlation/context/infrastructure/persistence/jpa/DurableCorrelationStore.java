package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.pipesor.correlation.context.application.queries.CorrelatedRevision;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision.IntroducedSignal;
import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup;
import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup.Context;
import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Own-store transactions only. Callers must authenticate scope/producer, validate references and sanitize all text. */
final class DurableCorrelationStore {
    private final DataSource dataSource;
    private final Clock clock;

    DurableCorrelationStore(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.clock = Objects.requireNonNull(clock);
    }

    record Contribution(Context context, String producerKey, UUID messageId, UUID evidenceId,
                        String correlationId, String inputFingerprint, String snapshotSummary, EvidenceSignal signal, ScopeEnvelope scope) {
        Contribution {
            Objects.requireNonNull(context, "Context is required");
            Objects.requireNonNull(messageId, "Message ID is required");
            Objects.requireNonNull(evidenceId, "Evidence ID is required");
            Objects.requireNonNull(signal, "Evidence signal is required");
            Objects.requireNonNull(scope, "Historical scope is required");
            if (producerKey == null || producerKey.isBlank() || correlationId == null || correlationId.isBlank()
                    || inputFingerprint == null || !inputFingerprint.matches("v1:sha256:[0-9a-f]{64}")
                    || snapshotSummary == null) {
                throw new IllegalArgumentException("Valid protected contribution metadata is required");
            }
        }
    }

    record Prepared(CorrelatedContextEntity snapshot, int revision, boolean confirmed) { }

    record MemberReference(UUID evidenceId, ScopeEnvelope scope) {
        MemberReference {
            Objects.requireNonNull(evidenceId, "Evidence reference is required");
            Objects.requireNonNull(scope, "Evidence scope is required");
        }
    }

    /** Resolve only the immutable members that the requested operation will expose. No authority I/O occurs here. */
    Set<MemberReference> membersForPreparation(Contribution input) {
        try (var connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var replay = receipt(connection, input);
                CorrelatedContextEntity target = replay == null ? null : replay.snapshot();
                boolean addition = false;
                if (target == null) {
                    try (var statement = connection.prepareStatement("""
                            SELECT id FROM evidence.correlation_groups
                            WHERE scope_key = ? AND execution_id = ? AND incident_id = ?
                            """)) {
                        bind(statement, input.context().scope(), input.context().executionId(), input.context().incidentId());
                        try (var rows = statement.executeQuery()) {
                            if (rows.next()) {
                                UUID group = rows.getObject("id", UUID.class);
                                target = originalMember(connection, group, input.evidenceId());
                                if (target == null) {
                                    target = latest(connection, group);
                                    addition = true;
                                }
                            }
                        }
                    }
                }
                var members = new HashSet<MemberReference>();
                if (target != null) {
                    if (!target.scope().sameResource(input.scope())) {
                        throw new IllegalArgumentException("Correlation group conflicts with its resource scope");
                    }
                    readRevision(connection, input.context(), target.id(), members::add);
                }
                if (target == null || addition) members.add(new MemberReference(input.evidenceId(), input.scope()));
                connection.commit();
                return Set.copyOf(members);
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                if (failure instanceof IllegalArgumentException conflict) throw conflict;
                throw new IllegalStateException("Correlation member references are unavailable");
            }
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Correlation member references are unavailable");
        }
    }

    Prepared prepare(Contribution input) {
        return prepareInternal(input, null, null);
    }

    /** Pure local capacity check only; it runs before commit and never for an existing operation. */
    Prepared prepare(Contribution input, Consumer<CorrelatedRevision> capacity) {
        return prepareInternal(input, Objects.requireNonNull(capacity, "Capacity check is required"), null);
    }

    /** Authority checks finish before SQL. Exact membership is compared again in the preparation transaction. */
    Prepared prepare(Contribution input, Consumer<CorrelatedRevision> capacity, Set<MemberReference> verifiedMembers) {
        return prepareInternal(input, Objects.requireNonNull(capacity, "Capacity check is required"),
                Set.copyOf(Objects.requireNonNull(verifiedMembers, "Verified members are required")));
    }

    private Prepared prepareInternal(Contribution input, Consumer<CorrelatedRevision> capacity, Set<MemberReference> verifiedMembers) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var replay = receipt(connection, input);
                if (replay != null) {
                    verifyMembers(connection, input.context(), replay.snapshot().id(), verifiedMembers);
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
                    verifyMembers(connection, input.context(), replay.snapshot().id(), verifiedMembers);
                    connection.commit();
                    return replay;
                }
                var snapshot = originalMember(connection, groupId, input.evidenceId());
                boolean newSnapshot = snapshot == null;
                if (snapshot != null && !snapshot.signal().equals(input.signal())) {
                    throw new IllegalArgumentException("Correlation member conflicts with its original signal");
                }
                if (snapshot != null && !snapshot.scope().equals(input.scope())) {
                    throw new IllegalArgumentException("Correlation member conflicts with its original scope");
                }
                if (snapshot == null) {
                    var previous = latest(connection, groupId);
                    if (previous != null && !previous.id().equals(confirmedId)) {
                        throw new IllegalStateException("Correlation group has an unconfirmed snapshot");
                    }
                    if (previous != null && !previous.scope().sameResource(input.scope())) {
                        throw new IllegalArgumentException("Correlation group conflicts with its resource scope");
                    }
                    var group = previous == null ? CorrelationGroup.start(groupId, input.context())
                            : CorrelationGroup.restore(groupId, input.context(), Set.of(previous.evidenceIds()));
                    group = group.include(input.context(), input.evidenceId());
                    snapshot = new CorrelatedContextEntity(UUID.randomUUID(), input.context().incidentId(),
                            input.context().executionId(), group.evidenceIds().stream().sorted().toArray(UUID[]::new),
                            input.snapshotSummary(), input.correlationId(), now, groupId, input.signal(), input.scope());
                    execute(connection, """
                            INSERT INTO evidence.correlated_contexts
                            (id, incident_id, execution_id, evidence_ids, summary, correlation_id, created_at, group_id,
                             signal_stage, signal_summary, signal_content,
                             scope_tenant_id, scope_github_installation_id, scope_github_repository_id, scope_pipeline_id, scope_revision)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, snapshot.id(), snapshot.incidentId(), snapshot.executionId(),
                            snapshot.evidenceIds(), snapshot.summary(), snapshot.correlationId(), now, groupId,
                            input.signal().stage(), input.signal().summary(), input.signal().content(), input.scope().tenantId(),
                            input.scope().installationId(), input.scope().repositoryId(), input.scope().pipelineId(), input.scope().revision());
                }
                execute(connection, """
                        INSERT INTO evidence.correlation_receipts
                        (scope_key, producer_key, message_id, evidence_id, correlation_id, context_id, input_fingerprint, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, input.context().scope(), input.producerKey(), input.messageId(), input.evidenceId(),
                        input.correlationId(), snapshot.id(), input.inputFingerprint(), now);
                var verifiedRevision = verifyMembers(connection, input.context(), snapshot.id(), verifiedMembers);
                if (newSnapshot && capacity != null) {
                    CorrelatedRevision candidate;
                    try {
                        candidate = verifiedRevision == null
                                ? readRevision(connection, input.context(), snapshot.id()) : verifiedRevision;
                    } catch (RuntimeException unavailable) {
                        throw new IllegalStateException("Correlation revision is unavailable");
                    }
                    capacity.accept(candidate);
                }
                var result = prepared(connection, snapshot, confirmedId);
                connection.commit();
                return result;
            } catch (SQLException failure) {
                connection.rollback();
                if ("23505".equals(failure.getSQLState())) {
                    // Only a scoped message-key race may recover; no generic transaction retry.
                    try {
                        var replay = receipt(connection, input);
                        if (replay != null) {
                            verifyMembers(connection, input.context(), replay.snapshot().id(), verifiedMembers);
                            return replay;
                        }
                    } finally {
                        connection.rollback();
                    }
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

    /** Internal projection only. Expected context must come from a future authorized boundary. */
    CorrelatedRevision readRevision(Context expectedContext, UUID contextId) {
        Objects.requireNonNull(expectedContext, "Context is required");
        Objects.requireNonNull(contextId, "Context ID is required");
        try (var connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var result = readRevision(connection, expectedContext, contextId);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw new IllegalStateException("Correlation revision is unavailable");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Correlation revision is unavailable");
        }
    }

    private static CorrelatedRevision verifyMembers(Connection connection, Context expectedContext, UUID contextId,
            Set<MemberReference> verifiedMembers) throws SQLException {
        if (verifiedMembers == null) return null;
        var actual = new HashSet<MemberReference>();
        var revision = readRevision(connection, expectedContext, contextId, actual::add);
        if (!actual.equals(verifiedMembers)) {
            throw new IllegalStateException("Correlation membership changed during verification");
        }
        return revision;
    }

    private static CorrelatedRevision readRevision(Connection connection, Context expectedContext, UUID contextId)
            throws SQLException {
        return readRevision(connection, expectedContext, contextId, null);
    }

    private static CorrelatedRevision readRevision(Connection connection, Context expectedContext, UUID contextId,
            Consumer<MemberReference> members) throws SQLException {
        CorrelatedContextEntity target;
        try (var statement = connection.prepareStatement("""
                SELECT c.* FROM evidence.correlated_contexts c
                JOIN evidence.correlation_groups g ON g.id = c.group_id
                WHERE c.id = ? AND g.scope_key = ? AND g.execution_id = ? AND g.incident_id = ?
                AND cardinality(c.evidence_ids) > 0 AND array_ndims(c.evidence_ids) = 1
                AND array_lower(c.evidence_ids, 1) = 1
                """)) {
            bind(statement, contextId, expectedContext.scope(), expectedContext.executionId(), expectedContext.incidentId());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException();
                target = readSnapshot(rows);
            }
        }
        var expectedIds = Set.of(target.evidenceIds());
        var seen = new HashSet<UUID>();
        var signals = new ArrayList<IntroducedSignal>();
        try (var statement = connection.prepareStatement("""
                SELECT * FROM evidence.correlated_contexts WHERE group_id = ?
                AND cardinality(evidence_ids) <= ? AND array_ndims(evidence_ids) = 1
                AND array_lower(evidence_ids, 1) = 1 ORDER BY cardinality(evidence_ids)
                """)) {
            bind(statement, target.groupId(), target.evidenceIds().length);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var origin = readSnapshot(rows);
                    if (!origin.executionId().equals(expectedContext.executionId())
                            || !origin.incidentId().equals(expectedContext.incidentId())
                            || origin.evidenceIds().length != signals.size() + 1
                            || !origin.scope().sameResource(target.scope())) {
                        throw new IllegalStateException();
                    }
                    UUID evidenceId = null;
                    try (var receipts = connection.prepareStatement("""
                            SELECT DISTINCT evidence_id, scope_key FROM evidence.correlation_receipts WHERE context_id = ?
                            """)) {
                        bind(receipts, origin.id());
                        try (var sources = receipts.executeQuery()) {
                            while (sources.next()) {
                                if (evidenceId != null || !expectedContext.scope().equals(sources.getString("scope_key"))) {
                                    throw new IllegalStateException();
                                }
                                evidenceId = sources.getObject("evidence_id", UUID.class);
                                if (evidenceId == null) throw new IllegalStateException();
                            }
                        }
                    }
                    if (evidenceId == null || !expectedIds.contains(evidenceId) || !seen.add(evidenceId)
                            || !Set.of(origin.evidenceIds()).equals(seen)
                            || origin.correlationId() == null || origin.correlationId().isBlank()) {
                        throw new IllegalStateException();
                    }
                    signals.add(new IntroducedSignal(evidenceId, origin.id(), origin.correlationId(), origin.signal()));
                    if (members != null) members.accept(new MemberReference(evidenceId, origin.scope()));
                }
            }
        }
        if (!seen.equals(expectedIds) || signals.size() != target.evidenceIds().length) throw new IllegalStateException();
        return new CorrelatedRevision(target.id(), target.groupId(), signals.size(), signals, target.scope(),
                target.incidentId(), target.executionId(), target.summary(), target.correlationId(), target.createdAt());
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
                        || !rows.getString("input_fingerprint").equals(input.inputFingerprint())
                        || !stored.signal().equals(input.signal())
                        || !stored.scope().equals(input.scope())) {
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
                    rows.getObject("created_at", OffsetDateTime.class).toInstant(), rows.getObject("group_id", UUID.class),
                    new EvidenceSignal(rows.getString("signal_stage"), rows.getString("signal_summary"), rows.getString("signal_content")),
                    new ScopeEnvelope(rows.getObject("scope_tenant_id", UUID.class), rows.getString("scope_github_installation_id"),
                            rows.getString("scope_github_repository_id"), rows.getObject("scope_pipeline_id", UUID.class), rows.getString("scope_revision")));
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
