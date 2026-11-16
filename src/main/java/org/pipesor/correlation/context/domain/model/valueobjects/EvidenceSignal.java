package org.pipesor.correlation.context.domain.model.valueobjects;

import java.util.Set;

/** Minimal protected text; callers must minimize and sanitize it before construction. */
public record EvidenceSignal(String stage, String summary, String content) {
    private static final Set<String> STAGES = Set.of(
            "BUILD", "TESTING", "QUALITY_GATE", "DEPLOYMENT", "CONTAINER", "INFRASTRUCTURE");

    public EvidenceSignal {
        if (stage == null || !STAGES.contains(stage) || summary == null || summary.isBlank()
                || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Valid protected evidence signal is required");
        }
    }

    @Override
    public String toString() { return "EvidenceSignal[redacted]"; }
}
