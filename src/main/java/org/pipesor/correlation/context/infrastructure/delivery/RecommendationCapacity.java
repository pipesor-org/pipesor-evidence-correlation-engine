package org.pipesor.correlation.context.infrastructure.delivery;

import org.pipesor.correlation.context.application.queries.CorrelatedRevision;

import java.util.function.Consumer;

/** Local canonical-byte check with explicit bounds, suitable for the pre-commit preparation guard. */
public final class RecommendationCapacity implements Consumer<CorrelatedRevision> {
    private final int maxBytes;

    public RecommendationCapacity(int maxBytes) {
        if (maxBytes < 1) throw new IllegalArgumentException("A positive request capacity is required");
        this.maxBytes = maxBytes;
    }

    @Override
    public void accept(CorrelatedRevision revision) {
        encodedSize(revision);
    }

    public int encodedSize(CorrelatedRevision revision) {
        try {
            return RecommendationJson.encode(revision, maxBytes).length;
        } catch (RecommendationJson.RequestTooLarge exceeded) {
            throw new Exceeded();
        }
    }

    public static final class Exceeded extends RuntimeException {
        public Exceeded() { super("Prepared revision exceeds supplied request capacity"); }
    }
}
