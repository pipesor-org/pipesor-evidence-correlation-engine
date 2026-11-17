package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;

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
    @Column(name = "signal_stage", nullable = false, columnDefinition = "text")
    private String signalStage;
    @Column(name = "signal_summary", nullable = false, columnDefinition = "text")
    private String signalSummary;
    @Column(name = "signal_content", nullable = false, columnDefinition = "text")
    private String signalContent;

    @Column(name = "scope_tenant_id", nullable = false)
    private UUID scopeTenantId;
    @Column(name = "scope_github_installation_id", nullable = false, columnDefinition = "text")
    private String scopeInstallationId;
    @Column(name = "scope_github_repository_id", nullable = false, columnDefinition = "text")
    private String scopeRepositoryId;
    @Column(name = "scope_pipeline_id")
    private UUID scopePipelineId;
    @Column(name = "scope_revision", nullable = false, columnDefinition = "text")
    private String scopeRevision;

    protected CorrelatedContextEntity() {
    }

    CorrelatedContextEntity(UUID id, UUID incidentId, UUID executionId, UUID[] evidenceIds,
                            String summary, String correlationId, Instant createdAt, UUID groupId, EvidenceSignal signal, ScopeEnvelope scope) {
        this.id = id;
        this.incidentId = incidentId;
        this.executionId = executionId;
        this.evidenceIds = evidenceIds.clone();
        this.summary = summary;
        this.correlationId = correlationId;
        this.createdAt = createdAt;
        this.groupId = groupId;
        this.signalStage = signal.stage();
        this.signalSummary = signal.summary();
        this.signalContent = signal.content();
        this.scopeTenantId = scope.tenantId();
        this.scopeInstallationId = scope.installationId();
        this.scopeRepositoryId = scope.repositoryId();
        this.scopePipelineId = scope.pipelineId();
        this.scopeRevision = scope.revision();
    }

    UUID id() { return id; }
    UUID incidentId() { return incidentId; }
    UUID executionId() { return executionId; }
    UUID[] evidenceIds() { return evidenceIds.clone(); }
    String summary() { return summary; }
    String correlationId() { return correlationId; }
    Instant createdAt() { return createdAt; }
    UUID groupId() { return groupId; }
    EvidenceSignal signal() { return new EvidenceSignal(signalStage, signalSummary, signalContent); }
    ScopeEnvelope scope() { return new ScopeEnvelope(scopeTenantId, scopeInstallationId, scopeRepositoryId, scopePipelineId, scopeRevision); }
}
