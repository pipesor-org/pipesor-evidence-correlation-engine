package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "correlated_contexts", schema = "evidence")
class CorrelatedContextEntity {
    @Id
    private UUID id;
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;
    @Column(name = "execution_id", nullable = false)
    private UUID executionId;
    @Column(name = "evidence_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] evidenceIds;
    @Column(nullable = false, columnDefinition = "text")
    private String summary;
    @Column(name = "correlation_id", nullable = false, columnDefinition = "text")
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    protected CorrelatedContextEntity() {
    }

    CorrelatedContextEntity(UUID id, UUID incidentId, UUID executionId, UUID[] evidenceIds,
                            String summary, String correlationId, Instant createdAt, UUID groupId) {
        this.id = id;
        this.incidentId = incidentId;
        this.executionId = executionId;
        this.evidenceIds = evidenceIds.clone();
        this.summary = summary;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.groupId = groupId;
    }

    UUID id() { return id; }
    UUID incidentId() { return incidentId; }
    UUID executionId() { return executionId; }
    UUID[] evidenceIds() { return evidenceIds.clone(); }
    String summary() { return summary; }
    String correlationId() { return correlationId; }
    Instant createdAt() { return createdAt; }
    UUID groupId() { return groupId; }
}
