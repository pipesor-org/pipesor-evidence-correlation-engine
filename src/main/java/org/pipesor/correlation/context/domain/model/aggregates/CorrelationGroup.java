package org.pipesor.correlation.context.domain.model.aggregates;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Callers must authorize scope and validate execution, ORIGIN incident and evidence membership. */
public final class CorrelationGroup {
    private final UUID id;
    private final Context context;
    private final Set<UUID> evidenceIds;

    private CorrelationGroup(UUID id, Context context, Set<UUID> evidenceIds) {
        this.id = Objects.requireNonNull(id, "Group ID is required");
        this.context = Objects.requireNonNull(context, "Group context is required");
        this.evidenceIds = Set.copyOf(evidenceIds);
    }

    public static CorrelationGroup start(UUID id, Context context) {
        return new CorrelationGroup(id, context, Set.of());
    }

    public static CorrelationGroup restore(UUID id, Context context, Set<UUID> evidenceIds) {
        return new CorrelationGroup(id, context, evidenceIds);
    }

    public CorrelationGroup include(Context evidenceContext, UUID evidenceId) {
        Objects.requireNonNull(evidenceContext, "Evidence context is required");
        Objects.requireNonNull(evidenceId, "Evidence ID is required");
        if (!context.equals(evidenceContext)) {
            throw new IllegalArgumentException("Evidence context does not match the group");
        }
        if (evidenceIds.contains(evidenceId)) return this;
        var accumulated = new HashSet<>(evidenceIds);
        accumulated.add(evidenceId);
        return new CorrelationGroup(id, context, accumulated);
    }

    public UUID id() { return id; }
    public Context context() { return context; }
    public Set<UUID> evidenceIds() { return evidenceIds; }
    public int revision() { return evidenceIds.size(); }

    public record Context(String scope, UUID executionId, UUID incidentId) {
        public Context {
            if (scope == null || scope.isBlank()) {
                throw new IllegalArgumentException("Scope is required");
            }
            Objects.requireNonNull(executionId, "Execution ID is required");
            Objects.requireNonNull(incidentId, "Incident ID is required");
        }
    }
}
