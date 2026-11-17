package org.pipesor.correlation.context.application.commandhandlers;

import org.pipesor.correlation.context.application.ports.RecommendationDelivery;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Caller must authorize scope/references before supplying an already prepared revision and current token. */
public final class ConfirmPreparedDelivery {
    private final RecommendationDelivery delivery;
    private final BiConsumer<UUID, UUID> confirmation;

    public ConfirmPreparedDelivery(RecommendationDelivery delivery, BiConsumer<UUID, UUID> confirmation) {
        this.delivery = Objects.requireNonNull(delivery);
        this.confirmation = Objects.requireNonNull(confirmation);
    }

    public RecommendationDelivery.Receipt execute(CorrelatedRevision revision, String accessToken) {
        Objects.requireNonNull(revision, "Prepared revision is required");
        Objects.requireNonNull(revision.contextId(), "Prepared context ID is required");
        Objects.requireNonNull(revision.groupId(), "Prepared group ID is required");
        RecommendationDelivery.Receipt receipt;
        try {
            receipt = delivery.deliver(revision, accessToken);
        } catch (RecommendationDelivery.Failure failure) {
            throw new RecommendationDelivery.Failure(failure.kind());
        } catch (RuntimeException unavailable) {
            throw new RecommendationDelivery.Failure(RecommendationDelivery.Failure.Kind.UNKNOWN);
        }
        if (receipt == null || !revision.contextId().equals(receipt.operationId())
                || !revision.contextId().equals(receipt.contextId())
                || (receipt.status() != RecommendationDelivery.Status.ACCEPTED
                    && receipt.status() != RecommendationDelivery.Status.DUPLICATE)) {
            throw new RecommendationDelivery.Failure(RecommendationDelivery.Failure.Kind.UNKNOWN);
        }
        try {
            confirmation.accept(revision.groupId(), revision.contextId());
        } catch (RuntimeException unavailable) {
            throw new IllegalStateException("Correlation delivery confirmation is unavailable");
        }
        return receipt;
    }
}
