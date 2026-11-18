package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.pipesor.correlation.context.application.commandhandlers.ConfirmPreparedDelivery;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision;
import org.pipesor.correlation.context.domain.model.aggregates.CorrelationGroup.Context;
import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;
import org.pipesor.correlation.context.infrastructure.delivery.RecommendationCapacity;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences;
import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Explicit composition after authenticated NLP delivery. The caller supplies trusted partition identity. */
public final class ReceiveProcessedEvidence {
    public enum Outcome {
        ACCEPTED, DUPLICATE, RESOURCE_DENIED, NOT_READY, REFERENCE_MISMATCH,
        MESSAGE_CONFLICT, CAPACITY_EXCEEDED, INVALID, UNAVAILABLE
    }

    public record Caller(String producerKey, String scopeKey, UUID tenantId, String accountId,
            String installationId, String repositoryId, String workflowId) {
        public Caller {
            if (producerKey == null || producerKey.isBlank() || scopeKey == null || scopeKey.isBlank()
                    || tenantId == null || tenantId.equals(new UUID(0, 0))
                    || !externalId(accountId) || !externalId(installationId)
                    || !externalId(repositoryId) || !externalId(workflowId)) {
                throw new IllegalArgumentException("Trusted NLP caller binding is required");
            }
        }

        @Override public String toString() { return "NlpCaller[redacted]"; }
    }

    @FunctionalInterface
    public interface ReferenceAuthority {
        HttpOwnerReferences.Decision verify(NlpEvidenceV2 input, UUID evidenceId, String captureRevision, Instant deadline);
    }

    @FunctionalInterface
    public interface TokenSource {
        HttpOwnerReferences.Token acquire(Duration remaining);
    }

    @FunctionalInterface
    public interface BoundedDelivery {
        RecommendationDelivery.Receipt deliver(CorrelatedRevision revision, String accessToken, Duration remaining);
    }

    private final DurableCorrelationStore store;
    private final Clock clock;
    private final ReferenceAuthority references;
    private final RecommendationCapacity capacity;
    private final BoundedDelivery delivery;
    private final TokenSource tokens;

    public ReceiveProcessedEvidence(DataSource dataSource, Clock clock, ReferenceAuthority references,
            int maxRecommendationBytes, BoundedDelivery delivery, TokenSource tokens) {
        this.clock = Objects.requireNonNull(clock);
        this.store = new DurableCorrelationStore(Objects.requireNonNull(dataSource), clock);
        this.references = Objects.requireNonNull(references);
        this.capacity = new RecommendationCapacity(maxRecommendationBytes);
        this.delivery = Objects.requireNonNull(delivery);
        this.tokens = Objects.requireNonNull(tokens);
    }

    /** Input must come from NlpEvidenceV2.decode; authentication supplies caller independently of its body. */
    public Outcome receive(NlpEvidenceV2 input, Caller caller, Instant deadline) {
        if (input == null || caller == null || deadline == null) return Outcome.INVALID;
        final ScopeEnvelope scope;
        final Context context;
        final long budget;
        long started = System.nanoTime();
        try {
            if (!matches(input, caller)) return Outcome.RESOURCE_DENIED;
            scope = new ScopeEnvelope(input.captureContext().tenantId(), input.captureContext().githubInstallationId(),
                    input.pipeline().githubRepositoryId(), input.pipelineId(), input.captureContext().scopeRevision());
            context = new Context(caller.scopeKey(), input.executionId(), input.incidentId());
            budget = Duration.between(clock.instant(), deadline).toNanos();
        } catch (RuntimeException invalid) {
            return Outcome.INVALID;
        }
        if (budget <= 0) return Outcome.UNAVAILABLE;
        try {
            var permission = references.verify(input, input.evidenceId(), scope.revision(), deadline);
            remaining(deadline, started, budget);
            if (permission != HttpOwnerReferences.Decision.AUTHORIZED) return authorityOutcome(permission);
            var contribution = new DurableCorrelationStore.Contribution(context, caller.producerKey(), input.messageId(),
                    input.evidenceId(), input.correlationId(), input.fingerprint(), input.signal().summary(), input.signal(), scope);
            final DurableCorrelationStore.Prepared prepared;
            try {
                remaining(deadline, started, budget);
                var members = store.membersForPreparation(contribution);
                for (var member : members) {
                    if (member.evidenceId().equals(input.evidenceId()) && member.scope().equals(scope)) continue;
                    remaining(deadline, started, budget);
                    var memberPermission = references.verify(input, member.evidenceId(), member.scope().revision(),
                            clock.instant().plus(remaining(deadline, started, budget)));
                    remaining(deadline, started, budget);
                    if (memberPermission != HttpOwnerReferences.Decision.AUTHORIZED) return authorityOutcome(memberPermission);
                }
                remaining(deadline, started, budget);
                prepared = store.prepare(contribution, revision -> {
                    remaining(deadline, started, budget);
                    capacity.accept(revision);
                    remaining(deadline, started, budget);
                }, members);
            } catch (RecommendationCapacity.Exceeded tooLarge) {
                return Outcome.CAPACITY_EXCEEDED;
            } catch (IllegalArgumentException conflict) {
                return Outcome.MESSAGE_CONFLICT;
            }
            remaining(deadline, started, budget);
            if (prepared.confirmed()) return Outcome.DUPLICATE;
            var revision = store.readRevision(context, prepared.snapshot().id());
            var token = tokens.acquire(remaining(deadline, started, budget));
            if (token == null || token.value() == null || token.value().isEmpty() || token.expiresAt() == null
                    || token.value().chars().anyMatch(c -> c <= 0x20 || c >= 0x7f)) return Outcome.UNAVAILABLE;
            Instant deliveryDeadline = deadline.isBefore(token.expiresAt()) ? deadline : token.expiresAt();
            long elapsed = System.nanoTime() - started;
            long deliveryBudget = Math.min(budget, elapsed + remaining(deliveryDeadline, started, budget).toNanos());
            var confirmation = new ConfirmPreparedDelivery(
                    (frozen, credential) -> delivery.deliver(frozen, credential, remaining(deliveryDeadline, started, deliveryBudget)),
                    (groupId, contextId) -> {
                        remaining(deliveryDeadline, started, deliveryBudget);
                        store.confirm(groupId, contextId);
                    });
            confirmation.execute(revision, token.value());
            remaining(deliveryDeadline, started, deliveryBudget);
            return Outcome.ACCEPTED;
        } catch (RuntimeException unavailable) {
            return Outcome.UNAVAILABLE;
        }
    }

    private Duration remaining(Instant deadline, long started, long budget) {
        long nanos = budget - (System.nanoTime() - started);
        Duration wall = Duration.between(clock.instant(), deadline);
        if (nanos <= 0 || wall.isNegative() || wall.isZero()) {
            throw new IllegalStateException("Correlation processing deadline exceeded");
        }
        return wall.compareTo(Duration.ofNanos(nanos)) < 0 ? wall : Duration.ofNanos(nanos);
    }

    private static Outcome authorityOutcome(HttpOwnerReferences.Decision permission) {
        if (permission == null) return Outcome.UNAVAILABLE;
        return switch (permission) {
            case DENIED -> Outcome.RESOURCE_DENIED;
            case NOT_READY -> Outcome.NOT_READY;
            case MISMATCH -> Outcome.REFERENCE_MISMATCH;
            default -> Outcome.UNAVAILABLE;
        };
    }

    private static boolean matches(NlpEvidenceV2 input, Caller caller) {
        return caller.tenantId().equals(input.captureContext().tenantId())
                && caller.accountId().equals(input.captureContext().githubAccountId())
                && caller.installationId().equals(input.captureContext().githubInstallationId())
                && caller.repositoryId().equals(input.pipeline().githubRepositoryId())
                && caller.workflowId().equals(input.pipeline().githubWorkflowId());
    }

    private static boolean externalId(String value) {
        return value != null && value.matches("[1-9][0-9]*");
    }
}
