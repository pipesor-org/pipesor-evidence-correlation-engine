package org.pipesor.correlation.context.domain.model.valueobjects;

import java.util.Set;

/** Minimal protected text; callers must minimize and sanitize it before construction. */
public record EvidenceSignal(String stage, String summary, String content, String sourceLocator) {
    private static final Set<String> STAGES = Set.of(
            "BUILD", "TESTING", "QUALITY_GATE", "DEPLOYMENT", "CONTAINER", "INFRASTRUCTURE");

    public EvidenceSignal {
        if (stage == null || !STAGES.contains(stage) || summary == null || summary.isBlank()
                || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Valid protected evidence signal is required");
        }
        if (sourceLocator != null && !sourceLocator.matches(
                "v2:job:[1-9][0-9]*:member:[0-9]{1,9}_verify\\.txt:lines:([1-9][0-9]{0,8})-\\1:sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Valid protected evidence signal is required");
        }
    }

    public EvidenceSignal(String stage, String summary, String content) {
        this(stage, summary, content, null);
    }

    @Override
    public String toString() { return "EvidenceSignal[redacted]"; }
}
