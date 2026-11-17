package org.pipesor.correlation.context.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** Historical resource binding only; construction does not establish current authorization. */
public record ScopeEnvelope(UUID tenantId, String installationId, String repositoryId,
                            UUID pipelineId, String revision) {
    public ScopeEnvelope {
        if (tenantId == null || installationId == null || !installationId.matches("[1-9][0-9]{0,31}")
                || repositoryId == null || !repositoryId.matches("[1-9][0-9]{0,31}")
                || revision == null || !revision.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("Valid historical scope envelope is required");
        }
    }

    public boolean sameResource(ScopeEnvelope other) {
        return other != null && tenantId.equals(other.tenantId) && installationId.equals(other.installationId)
                && repositoryId.equals(other.repositoryId) && Objects.equals(pipelineId, other.pipelineId);
    }

    @Override
    public String toString() { return "ScopeEnvelope[redacted]"; }
}
