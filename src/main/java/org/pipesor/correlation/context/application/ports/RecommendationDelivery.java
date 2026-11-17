package org.pipesor.correlation.context.application.ports;

import org.pipesor.correlation.context.application.queries.CorrelatedRevision;

import java.util.UUID;

/** Caller supplies current authorization and a short-lived token; receipt is not diagnosis. */
public interface RecommendationDelivery {
    Receipt deliver(CorrelatedRevision revision, String accessToken);

    record Receipt(UUID operationId, UUID contextId, Status status) { }
    enum Status { ACCEPTED, DUPLICATE }

    final class Failure extends RuntimeException {
        public enum Kind { UNKNOWN, REJECTED, AUTHENTICATION, AUTHORIZATION, CONFLICT, TOO_LARGE, THROTTLED }
        private final Kind kind;

        public Failure(Kind kind) {
            super("Recommendation delivery " + kind.name().toLowerCase(java.util.Locale.ROOT));
            this.kind = kind;
        }

        public Kind kind() { return kind; }
    }
}
