package org.pipesor.correlation.context.application.queries;

import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;

import java.util.List;
import java.util.UUID;

public record CorrelatedRevision(UUID contextId, UUID groupId, int revision, List<IntroducedSignal> signals) {
    public CorrelatedRevision { signals = List.copyOf(signals); }

    public record IntroducedSignal(UUID evidenceId, UUID originContextId, String correlationId, EvidenceSignal signal) { }
}
