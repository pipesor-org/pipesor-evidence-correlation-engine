package org.pipesor.correlation.context.interfaces.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Outcome;
import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/**
 * Internal NLP intake. Authentication and the caller/role rule are enforced by the platform
 * filter chain; this controller validates the envelope, binds the per-request capture scope and
 * replies with success only after Recommendation confirmed the correlated context.
 */
@RestController
public final class ProcessedEvidenceController {
    public static final String PATH = "/internal/v1/processed-evidence";
    private static final Logger log = LoggerFactory.getLogger(ProcessedEvidenceController.class);
    private static final Pattern JSON = Pattern.compile("(?i)application/json(?:\\s*;\\s*charset\\s*=\\s*(?:utf-8|\"utf-8\"))?");

    /** The durable correlation use case. */
    @FunctionalInterface
    public interface Processor {
        Outcome process(NlpEvidenceV2 evidence, Caller caller, Instant deadline);
    }

    public record Limits(UUID tenant, Duration requestTimeout, int maxRequestBytes, int maxHeaderChars, int maxInFlight,
            int resourceDeniedStatus, int capacityExceededStatus) {
        public Limits { Objects.requireNonNull(tenant); Objects.requireNonNull(requestTimeout); }
    }

    private final Processor processor;
    private final Limits limits;
    private final Clock clock;
    private final Semaphore permits;

    public ProcessedEvidenceController(Processor processor, Limits limits, Clock clock) {
        this.processor = Objects.requireNonNull(processor);
        this.limits = Objects.requireNonNull(limits);
        this.clock = Objects.requireNonNull(clock);
        permits = new Semaphore(limits.maxInFlight());
    }

    @PostMapping(PATH)
    public ResponseEntity<Map<String, Object>> receive(HttpServletRequest request, @AuthenticationPrincipal Jwt caller)
            throws IOException {
        if (!permits.tryAcquire()) return failure(429, "OVERLOADED");
        try {
            return handle(request, caller);
        } finally {
            permits.release();
        }
    }

    private ResponseEntity<Map<String, Object>> handle(HttpServletRequest request, Jwt caller) throws IOException {
        long started = System.nanoTime();
        if (request.getQueryString() != null || request.getHeader(HttpHeaders.COOKIE) != null) return failure(400, "INVALID_REQUEST");
        String media = header(request, HttpHeaders.CONTENT_TYPE);
        String encoding = request.getHeader(HttpHeaders.CONTENT_ENCODING);
        if (media == null || !JSON.matcher(media).matches() || encoding != null && !"identity".equalsIgnoreCase(encoding)) {
            return failure(415, "UNSUPPORTED_MEDIA_TYPE");
        }
        String trace = header(request, "X-Correlation-ID");
        String messageId = header(request, "Idempotency-Key");
        if (trace == null || messageId == null) return failure(400, "INVALID_REQUEST");
        byte[] bytes = request.getInputStream().readNBytes(limits.maxRequestBytes() + 1);
        if (bytes.length > limits.maxRequestBytes()) return failure(413, "REQUEST_TOO_LARGE");
        final NlpEvidenceV2 evidence;
        try {
            evidence = NlpEvidenceV2.decode(bytes, limits.maxRequestBytes(), messageId, trace);
        } catch (IllegalArgumentException invalid) {
            return failure(400, "INVALID_REQUEST");
        }
        if (!limits.tenant().equals(evidence.captureContext().tenantId())) {
            log.warn("Processed evidence rejected: tenant outside this environment, message {}", evidence.messageId());
            return failure(limits.resourceDeniedStatus(), "RESOURCE_DENIED");
        }
        Instant now = clock.instant();
        Duration budget = limits.requestTimeout().minusNanos(System.nanoTime() - started);
        Duration tokenLeft = Duration.between(now, caller.getExpiresAt());
        Duration remaining = budget.compareTo(tokenLeft) < 0 ? budget : tokenLeft;
        if (remaining.isNegative() || remaining.isZero()) return failure(503, "UNAVAILABLE");
        Outcome outcome;
        try {
            outcome = processor.process(evidence, callerOf(evidence, caller), now.plus(remaining));
        } catch (RuntimeException failure) {
            log.error("Processed evidence {} failed with {}", evidence.messageId(), failure.getClass().getName());
            outcome = Outcome.UNAVAILABLE;
        }
        if (outcome == null) outcome = Outcome.UNAVAILABLE;
        if (outcome != Outcome.ACCEPTED && outcome != Outcome.DUPLICATE) {
            log.warn("Processed evidence {} not accepted: {}", evidence.messageId(), outcome);
        }
        return switch (outcome) {
            case ACCEPTED -> success("accepted", evidence.messageId());
            case DUPLICATE -> success("duplicate", evidence.messageId());
            case INVALID -> failure(400, "INVALID_REQUEST");
            case MESSAGE_CONFLICT -> failure(409, "MESSAGE_CONFLICT");
            case REFERENCE_MISMATCH -> failure(400, "REFERENCE_MISMATCH");
            case RESOURCE_DENIED -> failure(limits.resourceDeniedStatus(), "RESOURCE_DENIED");
            case CAPACITY_EXCEEDED -> failure(limits.capacityExceededStatus(), "CAPACITY_EXCEEDED");
            case NOT_READY, UNAVAILABLE -> failure(503, "UNAVAILABLE");
        };
    }

    /** The capture scope is bound per request; owner checks then confirm it against current authority. */
    private static Caller callerOf(NlpEvidenceV2 evidence, Jwt caller) {
        var capture = evidence.captureContext();
        var pipeline = evidence.pipeline();
        String scope = "github:" + capture.githubAccountId() + "/" + capture.githubInstallationId() + "/"
                + pipeline.githubRepositoryId() + "/" + pipeline.githubWorkflowId();
        return new Caller("workload:" + caller.getClaimAsString("azp"), scope, capture.tenantId(), capture.githubAccountId(),
                capture.githubInstallationId(), pipeline.githubRepositoryId(), pipeline.githubWorkflowId());
    }

    private String header(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1 || values.getFirst().isBlank() || values.getFirst().length() > limits.maxHeaderChars()) return null;
        return values.getFirst();
    }

    private static ResponseEntity<Map<String, Object>> success(String status, UUID messageId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("status", status, "message_id", messageId.toString()));
    }

    private static ResponseEntity<Map<String, Object>> failure(int status, String code) {
        String title = switch (status) {
            case 400 -> "Bad Request";
            case 409 -> "Conflict";
            case 413 -> "Content Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            default -> "Service Unavailable";
        };
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of("type", "about:blank", "title", title, "status", status, "code", code));
    }
}
