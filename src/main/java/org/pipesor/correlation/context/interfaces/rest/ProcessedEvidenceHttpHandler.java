package org.pipesor.correlation.context.interfaces.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Outcome;
import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Explicit internal composition. Successful replies follow durable Recommendation confirmation. */
public final class ProcessedEvidenceHttpHandler implements HttpHandler, AutoCloseable {
    public static final String PATH = "/internal/v1/processed-evidence";

    public record Settings(URI issuer, URI jwkSetUri, String audience, UUID directoryTenant,
            UUID callerClient, UUID callerPrincipal, String role, Caller caller,
            Duration clockSkew, Duration requestTimeout, int maxRequestBytes, int maxHeaderChars, int maxInFlight) {
        public Settings {
            if (!https(issuer) || !https(jwkSetUri) || audience == null || audience.isBlank()
                    || !id(directoryTenant) || !id(callerClient) || !id(callerPrincipal)
                    || role == null || role.isBlank() || caller == null
                    || clockSkew == null || clockSkew.isNegative() || requestTimeout == null
                    || requestTimeout.isNegative() || requestTimeout.isZero() || maxRequestBytes < 1
                    || maxRequestBytes == Integer.MAX_VALUE || maxHeaderChars < 1 || maxInFlight < 1) {
                throw new IllegalArgumentException("Explicit processed-evidence settings are required");
            }
            requestTimeout.toNanos();
        }
        @Override public String toString() { return "ProcessedEvidenceSettings[configured]"; }
    }

    /** The owner must supply the pending business disposition before composing a receiver. */
    public record PendingResponses(int resourceDeniedStatus, int capacityExceededStatus) {
        public PendingResponses {
            Set<Integer> failureStatuses = Set.of(400, 409, 413, 422, 429, 503);
            if (!failureStatuses.contains(resourceDeniedStatus) || !failureStatuses.contains(capacityExceededStatus)) {
                throw new IllegalArgumentException("Explicit non-success disposition is required");
            }
        }
    }

    @FunctionalInterface
    public interface Processor {
        Outcome process(NlpEvidenceV2 evidence, Caller caller, Instant deadline);
    }

    private final Settings settings;
    private final Processor processor;
    private final PendingResponses pendingResponses;
    private final JwtDecoder tokens;
    private final Clock clock;
    private final Semaphore permits;
    private final ExecutorService work = Executors.newVirtualThreadPerTaskExecutor();

    public ProcessedEvidenceHttpHandler(Settings settings, Processor processor, PendingResponses pendingResponses) {
        this(settings, processor, pendingResponses,
                NimbusJwtDecoder.withJwkSetUri(settings.jwkSetUri().toString()).build(), Clock.systemUTC());
    }

    public ProcessedEvidenceHttpHandler(Settings settings, Processor processor, PendingResponses pendingResponses,
            JwtDecoder tokens, Clock clock) {
        this.settings = Objects.requireNonNull(settings);
        this.processor = Objects.requireNonNull(processor);
        this.pendingResponses = Objects.requireNonNull(pendingResponses);
        this.tokens = Objects.requireNonNull(tokens);
        this.clock = Objects.requireNonNull(clock);
        permits = new Semaphore(settings.maxInFlight());
    }

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!permits.tryAcquire()) { send(exchange, failure(429, "OVERLOADED")); return; }
        long start = System.nanoTime();
        var result = new CompletableFuture<Result>();
        try {
            work.execute(() -> {
                try { result.complete(check(exchange, start)); }
                catch (Rejected rejected) { result.complete(failure(rejected.status, rejected.code)); }
                catch (Exception unavailable) { result.complete(failure(503, "UNAVAILABLE")); }
                finally { permits.release(); }
            });
        } catch (RuntimeException closed) {
            permits.release();
            send(exchange, failure(503, "UNAVAILABLE"));
            return;
        }
        Result reply;
        try {
            long remaining = settings.requestTimeout().toNanos() - (System.nanoTime() - start);
            if (remaining <= 0) throw new IllegalStateException();
            reply = result.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            reply = failure(503, "UNAVAILABLE");
        } catch (Exception unavailable) {
            reply = failure(503, "UNAVAILABLE");
        }
        send(exchange, reply);
    }

    private Result check(HttpExchange exchange, long started) throws IOException {
        if (!PATH.equals(exchange.getRequestURI().getPath()) || exchange.getRequestURI().getQuery() != null
                || exchange.getRequestHeaders().containsKey("Cookie")) throw rejected(400);
        if (!"POST".equals(exchange.getRequestMethod())) throw rejected(403);
        String authorization = header(exchange, "Authorization", true);
        if (!authorization.startsWith("Bearer ")) throw rejected(401);
        final Jwt jwt;
        try {
            jwt = tokens.decode(authorization.substring(7));
        } catch (BadJwtException invalid) { throw rejected(401); }
        catch (RuntimeException unavailable) { throw rejected(503); }
        try { validateIdentity(jwt); }
        catch (RuntimeException invalid) { throw rejected(401); }
        final List<String> roles;
        try { roles = jwt.getClaimAsStringList("roles"); }
        catch (RuntimeException invalidRole) { throw rejected(403); }
        if (!settings.callerPrincipal().toString().equals(jwt.getClaimAsString("oid"))
                || !settings.callerClient().toString().equals(jwt.getClaimAsString("azp"))
                || roles == null || !roles.contains(settings.role())) throw rejected(403);

        long admitted = System.nanoTime();
        Instant now = clock.instant();
        long remaining = Math.min(settings.requestTimeout().toNanos() - (admitted - started),
                Duration.between(now, jwt.getExpiresAt()).toNanos());
        if (remaining <= 0) throw rejected(503);
        Instant deadline = now.plusNanos(remaining);
        long endNanos = admitted + remaining;
        String media = header(exchange, "Content-Type", true);
        if (!media.matches("(?i)application/json(?:\\s*;\\s*charset\\s*=\\s*(?:utf-8|\"utf-8\"))?")) throw rejected(415);
        String encoding = header(exchange, "Content-Encoding", false);
        if (encoding != null && !"identity".equalsIgnoreCase(encoding)) throw rejected(415);
        String trace = header(exchange, "X-Correlation-ID", true);
        String messageId = header(exchange, "Idempotency-Key", true);
        byte[] bytes = exchange.getRequestBody().readNBytes(settings.maxRequestBytes() + 1);
        if (bytes.length > settings.maxRequestBytes()) throw rejected(413);
        final NlpEvidenceV2 evidence;
        try { evidence = NlpEvidenceV2.decode(bytes, settings.maxRequestBytes(), messageId, trace); }
        catch (IllegalArgumentException invalid) { throw rejected(400); }
        requireTime(started, deadline, endNanos);
        Instant remainingDeadline = clock.instant().plusNanos(Math.max(0, endNanos - System.nanoTime()));
        Outcome outcome = processor.process(evidence, settings.caller(),
                remainingDeadline.isBefore(deadline) ? remainingDeadline : deadline);
        requireTime(started, deadline, endNanos);
        if (outcome == null) return failure(503, "UNAVAILABLE");
        return switch (outcome) {
            case ACCEPTED -> success("accepted", evidence.messageId());
            case DUPLICATE -> success("duplicate", evidence.messageId());
            case INVALID -> failure(400, "INVALID_REQUEST");
            case MESSAGE_CONFLICT -> failure(409, "MESSAGE_CONFLICT");
            case REFERENCE_MISMATCH -> failure(400, "REFERENCE_MISMATCH");
            case RESOURCE_DENIED -> failure(pendingResponses.resourceDeniedStatus(), "RESOURCE_DENIED");
            case CAPACITY_EXCEEDED -> failure(pendingResponses.capacityExceededStatus(), "CAPACITY_EXCEEDED");
            case NOT_READY, UNAVAILABLE -> failure(503, "UNAVAILABLE");
        };
    }

    private void validateIdentity(Jwt jwt) {
        Instant now = clock.instant();
        if (!"2.0".equals(jwt.getClaimAsString("ver")) || !"app".equals(jwt.getClaimAsString("idtyp"))
                || jwt.getClaim("scp") != null || jwt.getIssuer() == null
                || !settings.issuer().toString().equals(jwt.getIssuer().toString())
                || !jwt.getAudience().contains(settings.audience())
                || !settings.directoryTenant().toString().equals(jwt.getClaimAsString("tid"))
                || jwt.getExpiresAt() == null || !now.minus(settings.clockSkew()).isBefore(jwt.getExpiresAt())
                || jwt.getNotBefore() != null && now.plus(settings.clockSkew()).isBefore(jwt.getNotBefore())
                || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now.plus(settings.clockSkew()))
                || jwt.getClaim("appid") != null && !jwt.getClaimAsString("appid").equals(jwt.getClaimAsString("azp"))) {
            throw rejected(401);
        }
    }

    private void requireTime(long started, Instant deadline, long endNanos) {
        if (System.nanoTime() - started >= settings.requestTimeout().toNanos()
                || endNanos - System.nanoTime() <= 0 || !clock.instant().isBefore(deadline)) throw rejected(503);
    }

    private String header(HttpExchange exchange, String name, boolean required) {
        var values = exchange.getRequestHeaders().get(name);
        if (values == null) {
            if (!required) return null;
            throw rejected("Authorization".equals(name) ? 401 : 400);
        }
        if (values.size() != 1 || values.getFirst().isBlank() || values.getFirst().length() > settings.maxHeaderChars()) {
            throw rejected(400);
        }
        return values.getFirst();
    }

    private static boolean id(UUID value) { return value != null && !value.equals(new UUID(0, 0)); }
    private static boolean https(URI uri) {
        return uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null
                && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
    }

    private static Result success(String status, UUID messageId) {
        return new Result(200, Map.of("status", status, "message_id", messageId.toString()));
    }

    private static Rejected rejected(int status) {
        String code = switch (status) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "CALLER_DENIED";
            case 413 -> "REQUEST_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "OVERLOADED";
            default -> "UNAVAILABLE";
        };
        return new Rejected(status, code);
    }

    private static Result failure(int status, String code) {
        String title = switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 409 -> "Conflict";
            case 413 -> "Content Too Large";
            case 415 -> "Unsupported Media Type";
            case 422 -> "Unprocessable Content";
            case 429 -> "Too Many Requests";
            default -> "Service Unavailable";
        };
        return new Result(status, Map.of("type", "about:blank", "title", title, "status", status, "code", code));
    }

    private static void send(HttpExchange exchange, Result result) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Content-Type", result.status() == 200 ? "application/json" : "application/problem+json");
            if (result.status() == 401) exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            byte[] bytes = CanonicalJson.mapper().writeValueAsBytes(result.body());
            exchange.sendResponseHeaders(result.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().flush();
        }
    }

    private record Result(int status, Map<String, ?> body) { }
    private static final class Rejected extends RuntimeException {
        private final int status;
        private final String code;
        Rejected(int status, String code) { super(null, null, false, false); this.status = status; this.code = code; }
    }
    @Override public void close() { work.shutdownNow(); }
}
