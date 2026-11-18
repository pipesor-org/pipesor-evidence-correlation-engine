package org.pipesor.correlation.context.infrastructure.delivery;

import org.pipesor.correlation.context.application.ports.RecommendationDelivery;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Manually constructed only. Caller must authorize scope/references and supply a current token. */
public final class HttpRecommendationDelivery implements RecommendationDelivery, AutoCloseable {
    private static final String PATH = "/internal/v1/correlated-contexts";
    private final URI destination;
    private final int maxRequestBytes;
    private final int maxResponseBytes;
    private final long timeoutNanos;
    private final HttpClient http;

    public HttpRecommendationDelivery(URI endpoint, int maxRequestBytes, int maxResponseBytes, Duration timeout) {
        this(endpoint, maxRequestBytes, maxResponseBytes, timeout, false);
    }

    // Only same-package synthetic HTTP tests may bypass TLS on the literal loopback address.
    HttpRecommendationDelivery(URI endpoint, int maxRequestBytes, int maxResponseBytes, Duration timeout, boolean loopbackTest) {
        if (endpoint == null || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/"))
                || !("https".equals(endpoint.getScheme()) || loopbackTest && "http".equals(endpoint.getScheme())
                && "127.0.0.1".equals(endpoint.getHost()))
                || maxRequestBytes < 1 || maxResponseBytes < 1 || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Explicit delivery endpoint and positive bounds are required");
        }
        try {
            timeoutNanos = timeout.toNanos();
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException("Representable delivery timeout is required");
        }
        this.destination = endpoint.resolve(PATH);
        this.maxRequestBytes = maxRequestBytes;
        this.maxResponseBytes = maxResponseBytes;
        this.http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(timeout).build();
    }

    @Override
    public Receipt deliver(CorrelatedRevision revision, String accessToken) {
        return deliverWithin(revision, accessToken, timeoutNanos);
    }

    /** The caller may shorten, but never extend, the configured delivery budget. */
    public Receipt deliver(CorrelatedRevision revision, String accessToken, Duration remainingBudget) {
        if (remainingBudget == null || remainingBudget.isZero() || remainingBudget.isNegative()) throw unknown();
        long limit = remainingBudget.compareTo(Duration.ofNanos(timeoutNanos)) < 0
                ? remainingBudget.toNanos() : timeoutNanos;
        return deliverWithin(revision, accessToken, limit);
    }

    private Receipt deliverWithin(CorrelatedRevision revision, String accessToken, long limit) {
        long started = System.nanoTime();
        if (accessToken == null || accessToken.isEmpty() || accessToken.chars().anyMatch(c -> c <= 0x20 || c >= 0x7f)) {
            throw new IllegalArgumentException("A current bearer token is required");
        }
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            byte[] body = RecommendationJson.encode(revision, maxRequestBytes);
            var request = HttpRequest.newBuilder(destination)
                    .timeout(Duration.ofNanos(remaining(started, limit)))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", revision.contextId().toString())
                    .header("X-Correlation-ID", revision.correlationId())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            pending = http.sendAsync(request, responseInfo -> {
                if (responseInfo.statusCode() != 200) throw rejected(responseInfo.statusCode());
                return HttpResponse.BodySubscribers.limiting(HttpResponse.BodySubscribers.ofByteArray(), maxResponseBytes);
            });
            var response = pending.get(remaining(started, limit), TimeUnit.NANOSECONDS);
            remaining(started, limit);
            if (!jsonContentType(response.headers().firstValue("Content-Type").orElse(""))) throw unknown();
            Receipt receipt;
            try {
                receipt = RecommendationJson.receipt(response.body(), revision.contextId());
            } catch (IllegalArgumentException invalidReceipt) {
                throw unknown();
            }
            remaining(started, limit);
            return receipt;
        } catch (RecommendationJson.RequestTooLarge failure) {
            throw new Failure(Failure.Kind.TOO_LARGE);
        } catch (Failure failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new Failure(Failure.Kind.REJECTED);
        } catch (ExecutionException failure) {
            for (Throwable cause = failure.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof Failure classified) throw new Failure(classified.kind());
            }
            throw unknown();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw unknown();
        } catch (Exception failure) {
            throw unknown();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    private static long remaining(long started, long limit) {
        long remaining = limit - (System.nanoTime() - started);
        if (remaining <= 0) throw unknown();
        return remaining;
    }

    private static Failure rejected(int status) {
        return new Failure(switch (status) {
            case 400 -> Failure.Kind.REJECTED;
            case 401 -> Failure.Kind.AUTHENTICATION;
            case 403 -> Failure.Kind.AUTHORIZATION;
            case 409 -> Failure.Kind.CONFLICT;
            case 413 -> Failure.Kind.TOO_LARGE;
            case 429 -> Failure.Kind.THROTTLED;
            default -> Failure.Kind.UNKNOWN;
        });
    }

    private static boolean jsonContentType(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split(";");
        if (!parts[0].trim().equals("application/json")) return false;
        for (int i = 1; i < parts.length; i++) {
            String parameter = parts[i].trim();
            String[] attribute = parameter.split("=", 2);
            if (attribute[0].trim().equals("charset") && (attribute.length != 2
                    || !attribute[1].trim().replace("\"", "").equals("utf-8"))) return false;
        }
        return true;
    }

    private static Failure unknown() { return new Failure(Failure.Kind.UNKNOWN); }

    @Override public void close() { http.close(); }
}
