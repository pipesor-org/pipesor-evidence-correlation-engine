package org.pipesor.correlation.context.infrastructure.validation;

import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import org.pipesor.correlation.shared.security.TransportPolicy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Explicit composition. Only call this after authenticating the NLP caller and its resource scope. */
public final class HttpOwnerReferences implements AutoCloseable {
    private static final String OPERATIONAL_PATH = "/internal/v1/evidence/operational-reference-checks";
    private static final String MEMBERSHIP_PATH = "/internal/v1/evidence/membership-checks";
    private static final Set<String> RESPONSE_FIELDS = Set.of("requestId", "correlationId", "decision");

    public enum Decision { AUTHORIZED, NOT_READY, MISMATCH, DENIED, UNAVAILABLE }
    public enum Owner { PIPELINE, LOG_EVENT }

    public record Token(String value, Instant expiresAt) {
        @Override public String toString() { return "Token[redacted]"; }
    }

    @FunctionalInterface
    public interface TokenSource {
        /** Acquire a current token addressed to this owner within the supplied remaining budget. */
        Token acquire(Owner owner, Duration remaining);
    }

    private record Reply(Decision decision, Instant deadline, long budget) { }

    private final URI pipeline;
    private final URI logEvent;
    private final TokenSource tokens;
    private final Clock clock;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final Duration timeout;
    private final HttpClient http;

    public HttpOwnerReferences(URI pipeline, URI logEvent, TokenSource tokens, Duration timeout,
            int maxRequestBytes, int maxResponseBytes, TransportPolicy transport) {
        this(pipeline, logEvent, tokens, timeout, maxRequestBytes, maxResponseBytes, Clock.systemUTC(), transport);
    }

    HttpOwnerReferences(URI pipeline, URI logEvent, TokenSource tokens, Duration timeout,
            int maxRequestBytes, int maxResponseBytes, Clock clock, TransportPolicy transport) {
        Objects.requireNonNull(transport);
        this.pipeline = endpoint(pipeline, OPERATIONAL_PATH, transport);
        this.logEvent = endpoint(logEvent, MEMBERSHIP_PATH, transport);
        this.tokens = Objects.requireNonNull(tokens);
        this.clock = Objects.requireNonNull(clock);
        if (timeout == null || timeout.isZero() || timeout.isNegative() || maxRequestBytes < 1 || maxResponseBytes < 1) {
            throw new IllegalArgumentException("Explicit owner-validation budgets are required");
        }
        this.timeout = timeout;
        timeout.toNanos();
        this.maxRequestBytes = maxRequestBytes;
        this.maxResponseBytes = maxResponseBytes;
        http = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Decision verify(NlpEvidenceV2 input, Instant attemptDeadline) {
        if (input == null) return Decision.UNAVAILABLE;
        return verify(input, input.evidenceId(), input.captureContext().scopeRevision(), attemptDeadline);
    }

    /** Revalidate a frozen member of the same trusted attempt and stable capture resource. */
    public Decision verify(NlpEvidenceV2 input, UUID evidenceId, String captureRevision, Instant attemptDeadline) {
        long started = System.nanoTime();
        try {
            if (evidenceId == null || captureRevision == null || !captureRevision.matches("[A-Za-z0-9._:-]{1,128}")) {
                return Decision.UNAVAILABLE;
            }
            Instant limit = earlier(attemptDeadline, clock.instant().plus(timeout));
            long budget = remaining(limit, started, timeout.toNanos());
            var operational = operationalFields(input, captureRevision);
            var operationalReply = check(pipeline, Owner.PIPELINE, operational, input.correlationId(), limit, started, budget);
            if (operationalReply == null) return Decision.UNAVAILABLE;
            if (operationalReply.decision() != Decision.AUTHORIZED) return operationalReply.decision();
            var membershipReply = check(logEvent, Owner.LOG_EVENT, membershipFields(input, evidenceId, captureRevision), input.correlationId(),
                    operationalReply.deadline(), started, operationalReply.budget());
            return membershipReply == null ? Decision.UNAVAILABLE : membershipReply.decision();
        } catch (RuntimeException unavailable) {
            return Decision.UNAVAILABLE;
        }
    }

    private Reply check(URI endpoint, Owner owner, ObjectNode body, String trace,
            Instant limit, long started, long budget) {
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var token = tokens.acquire(owner, Duration.ofNanos(remaining(limit, started, budget)));
            if (token == null || token.value() == null || token.value().isEmpty()
                    || token.value().chars().anyMatch(c -> c <= 0x20 || c >= 0x7f)) {
                return null;
            }
            var deadline = earlier(earlier(limit, token.expiresAt()),
                    clock.instant().plusNanos(remaining(limit, started, budget)));
            long peerBudget = Math.min(budget, System.nanoTime() - started + remaining(deadline, started, budget));
            String requestId = UUID.randomUUID().toString();
            body.put("requestId", requestId).put("correlationId", trace).put("deadlineAt", deadline.toString());
            byte[] payload = CanonicalJson.mapper().writeValueAsBytes(body);
            if (payload.length > maxRequestBytes) return null;

            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofNanos(remaining(deadline, started, peerBudget)))
                    .header("Authorization", "Bearer " + token.value())
                    .header("X-Correlation-Id", trace)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
            pending = http.sendAsync(request, info -> {
                if (info.statusCode() != 200) throw unavailable();
                return HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(), maxResponseBytes);
            });
            var response = pending.get(remaining(deadline, started, peerBudget), TimeUnit.NANOSECONDS);
            if (!response.headers().allValues("Cache-Control").equals(List.of("no-store"))
                    || !jsonContentType(response.headers().allValues("Content-Type"))) return null;
            String decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(response.body())).toString();
            JsonNode result = CanonicalJson.mapper().readTree(decoded);
            if (result == null || !result.isObject() || !result.propertyNames().equals(RESPONSE_FIELDS)
                    || !requestId.equals(text(result, "requestId")) || !trace.equals(text(result, "correlationId"))) {
                return null;
            }
            var decision = Decision.valueOf(text(result, "decision"));
            remaining(deadline, started, peerBudget);
            return new Reply(decision, deadline, peerBudget);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception unavailable) {
            return null;
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private static ObjectNode scopeFields(NlpEvidenceV2 input, String captureRevision) {
        return CanonicalJson.mapper().createObjectNode()
                .put("tenantId", input.captureContext().tenantId().toString())
                .put("githubInstallationId", input.captureContext().githubInstallationId())
                .put("scopeRevision", captureRevision)
                .put("executionId", input.executionId().toString())
                .put("incidentId", input.incidentId().toString());
    }

    private static ObjectNode operationalFields(NlpEvidenceV2 input, String captureRevision) {
        return scopeFields(input, captureRevision).put("githubAccountId", input.captureContext().githubAccountId())
                .put("githubRepositoryId", input.pipeline().githubRepositoryId())
                .put("githubWorkflowId", input.pipeline().githubWorkflowId())
                .put("githubRunId", input.execution().githubRunId())
                .put("runAttempt", input.execution().runAttempt())
                .put("pipelineId", input.pipelineId().toString());
    }

    private static ObjectNode membershipFields(NlpEvidenceV2 input, UUID evidenceId, String captureRevision) {
        return scopeFields(input, captureRevision).put("evidenceId", evidenceId.toString()).put("source", input.source());
    }

    private static URI endpoint(URI origin, String path, TransportPolicy transport) {
        try {
            transport.require(origin);
        } catch (IllegalArgumentException rejected) {
            throw new IllegalArgumentException("Explicit owner HTTPS origin required");
        }
        if (!List.of("", "/").contains(origin.getRawPath())) throw new IllegalArgumentException("Explicit owner HTTPS origin required");
        return origin.resolve(path);
    }

    private long remaining(Instant deadline, long started, long budget) {
        long nanos = budget - (System.nanoTime() - started);
        Duration wall = Duration.between(clock.instant(), deadline);
        if (nanos <= 0 || wall.isNegative() || wall.isZero()) throw unavailable();
        return wall.compareTo(Duration.ofNanos(nanos)) < 0 ? wall.toNanos() : nanos;
    }

    private static boolean jsonContentType(List<String> values) {
        if (values.size() != 1) return false;
        var parts = values.getFirst().toLowerCase(Locale.ROOT).split(";", -1);
        return parts[0].trim().equals("application/json") && (parts.length == 1
                || parts.length == 2 && List.of("charset=utf-8", "charset=\"utf-8\"").contains(parts[1].trim()));
    }

    private static String text(JsonNode result, String field) {
        var value = result.get(field);
        return value != null && value.isString() ? value.asString() : "";
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static IllegalStateException unavailable() { return new IllegalStateException("Owner validation unavailable"); }

    @Override public void close() { http.close(); }
}
