package org.pipesor.correlation.context.infrastructure.delivery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery.Failure;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery.Status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRecommendationDeliveryTest {
    private static final String TOKEN = "synthetic-test-token";

    @Test
    void sendsFrozenBytesAndOriginalIdentityAndAcceptsOnlyCorrespondingReceipts() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(1);
        byte[] accepted = DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("accepted_receipt"));
        byte[] duplicate = DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("duplicate_receipt"));
        try (var server = new Stub((exchange, number) -> reply(exchange, 200, "application/json", number == 1 ? accepted : duplicate));
             var client = server.client(20_000, 2_000, Duration.ofSeconds(3))) {
            var revision = DeliveryFixtures.revision(fixture);
            assertThat(client.deliver(revision, TOKEN).status()).isEqualTo(Status.ACCEPTED);
            assertThat(client.deliver(revision, TOKEN).status()).isEqualTo(Status.DUPLICATE);
            assertThat(server.calls.get()).isEqualTo(2);
            for (var request : server.requests) {
                assertThat(request.method()).isEqualTo("POST");
                assertThat(request.path()).isEqualTo("/internal/v1/correlated-contexts");
                assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer " + TOKEN);
                assertThat(request.headers().getFirst("Idempotency-Key")).isEqualTo(revision.contextId().toString());
                assertThat(request.headers().getFirst("X-Correlation-ID")).isEqualTo(revision.correlationId());
                assertThat(request.body()).isEqualTo(fixture.get("proposed_canonical_json").asString().getBytes(StandardCharsets.UTF_8));
                assertThat(new String(request.body(), StandardCharsets.UTF_8)).doesNotContain(TOKEN);
            }
        }
    }

    @Test
    void malformedSuccessLeavesReceptionUnknownWithoutLeakingTheResponse() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        String valid = DeliveryFixtures.JSON.writeValueAsString(fixture.get("accepted_receipt"));
        var bodies = new ArrayList<byte[]>();
        for (String value : List.of("", "null", "[]", "<html>private-remote-evidence</html>", valid + " {}",
                "\ufeff" + valid, valid.replace("\"accepted\"", "\"diagnosed\""),
                valid.replace("\"1.0\"", "\"2.0\""), valid.replace("000000000101", "000000000999"),
                valid.replace("\"accepted\"", "\"accepted\",\"status\":\"duplicate\""),
                valid.replace("\"accepted\"", "\"accepted\",\"private_body\":\"secret\""))) {
            bodies.add(value.getBytes(StandardCharsets.UTF_8));
        }
        bodies.add(new byte[]{(byte) 0xc3, 0x28});
        try (var server = new Stub((exchange, number) -> reply(exchange, 200, "application/json", bodies.get(number - 1)));
             var client = server.client(20_000, 2_000, Duration.ofSeconds(3))) {
            for (int i = 0; i < bodies.size(); i++) {
                failure(client, fixture, Failure.Kind.UNKNOWN);
                assertThat(server.calls.get()).isEqualTo(i + 1);
            }
        }
    }

    @Test
    void nonFinalStatusAndWrongMediaNeverProduceAReceipt() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        byte[] valid = DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("accepted_receipt"));
        List<String> media = List.of("text/html", "application/json; charset=utf-16",
                "application/json; charset = utf-16");
        try (var server = new Stub((exchange, number) -> reply(exchange, number == 1 ? 202 : 200,
                number == 1 ? "application/json" : media.get(number - 2), valid));
             var client = server.client(20_000, 2_000, Duration.ofSeconds(3))) {
            for (int i = 0; i <= media.size(); i++) failure(client, fixture, Failure.Kind.UNKNOWN);
            assertThat(server.calls.get()).isEqualTo(4);
        }
    }

    @Test
    void classifiesRejectionsAndTransientResponsesWithoutAutomaticRetry() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        int[] statuses = {400, 401, 403, 409, 413, 429, 503, 404};
        var expected = List.of(Failure.Kind.REJECTED, Failure.Kind.AUTHENTICATION, Failure.Kind.AUTHORIZATION,
                Failure.Kind.CONFLICT, Failure.Kind.TOO_LARGE, Failure.Kind.THROTTLED, Failure.Kind.UNKNOWN, Failure.Kind.UNKNOWN);
        try (var server = new Stub((exchange, number) -> reply(exchange, statuses[number - 1], "text/plain",
                ("private-remote-evidence:" + TOKEN).getBytes(StandardCharsets.UTF_8)));
             var client = server.client(20_000, 2_000, Duration.ofSeconds(3))) {
            for (int i = 0; i < statuses.length; i++) {
                failure(client, fixture, expected.get(i));
                assertThat(server.calls.get()).isEqualTo(i + 1);
            }
        }
    }

    @Test
    void definitiveStatusSurvivesOversizedOrStalledErrorBodies() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        int[] statuses = {401, 403, 409};
        var kinds = List.of(Failure.Kind.AUTHENTICATION, Failure.Kind.AUTHORIZATION, Failure.Kind.CONFLICT);
        for (boolean stalled : List.of(false, true)) {
            try (var server = new Stub((exchange, number) -> {
                exchange.sendResponseHeaders(statuses[number - 1], 4_000);
                exchange.getResponseBody().write(1);
                exchange.getResponseBody().flush();
                if (stalled) Thread.sleep(2_000);
                exchange.getResponseBody().write(new byte[3_999]);
            }); var client = server.client(20_000, 128, Duration.ofMillis(500))) {
                for (int i = 0; i < statuses.length; i++) {
                    failure(client, fixture, kinds.get(i));
                    assertThat(server.calls.get()).isEqualTo(i + 1);
                }
            }
        }
    }

    @Test
    void doesNotFollowRedirectsOrForwardCredentials() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        try (var target = new Stub((exchange, number) -> reply(exchange, 200, "application/json", new byte[0]));
             var redirect = new Stub((exchange, number) -> {
                 exchange.getResponseHeaders().set("Location", target.uri().resolve("/elsewhere").toString());
                 reply(exchange, 307, "text/plain", new byte[0]);
             }); var client = redirect.client(20_000, 2_000, Duration.ofSeconds(3))) {
            failure(client, fixture, Failure.Kind.UNKNOWN);
            assertThat(redirect.calls.get()).isEqualTo(1);
            assertThat(target.calls.get()).isZero();
        }
    }

    @Test
    void requestCapAndInvalidTokenFailBeforeAnyHttpRequest() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        try (var server = new Stub((exchange, number) -> reply(exchange, 500, "text/plain", new byte[0]));
             var client = server.client(20, 2_000, Duration.ofSeconds(3))) {
            failure(client, fixture, Failure.Kind.TOO_LARGE);
            for (String token : List.of("", "private\r\ntoken", "private token")) {
                var failure = assertThrows(IllegalArgumentException.class,
                        () -> client.deliver(DeliveryFixtures.revision(fixture), token));
                assertThat(failure.getMessage()).isEqualTo("A current bearer token is required");
                assertThat(failure.getCause()).isNull();
            }
            assertThat(server.calls.get()).isZero();
        }
    }

    @Test
    void limitsChunkedResponseBeforeUnboundedAccumulation() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        try (var server = new Stub((exchange, number) -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[4_000]);
        }); var client = server.client(20_000, 128, Duration.ofSeconds(3))) {
            failure(client, fixture, Failure.Kind.UNKNOWN);
            assertThat(server.calls.get()).isEqualTo(1);
        }
    }

    @Test
    void totalDeadlineBoundsDelayedHeadersAndBodyWithoutLateSuccess() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        byte[] valid = DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("accepted_receipt"));
        for (boolean headers : List.of(true, false)) {
            try (var server = new Stub((exchange, number) -> {
                if (headers) Thread.sleep(2_000);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(valid, 0, 1);
                exchange.getResponseBody().flush();
                if (!headers) Thread.sleep(2_000);
                exchange.getResponseBody().write(valid, 1, valid.length - 1);
            }); var client = server.client(20_000, 2_000, Duration.ofMillis(500))) {
                long started = System.nanoTime();
                failure(client, fixture, Failure.Kind.UNKNOWN);
                assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_500));
                assertThat(server.calls.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void explicitRecoveryAfterLostResponseReusesExactOperationAndBody() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        byte[] duplicate = DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("duplicate_receipt"));
        try (var server = new Stub((exchange, number) -> {
            if (number > 1) reply(exchange, 200, "application/json", duplicate);
        }); var client = server.client(20_000, 2_000, Duration.ofSeconds(3))) {
            failure(client, fixture, Failure.Kind.UNKNOWN);
            assertThat(server.calls.get()).isEqualTo(1);
            assertThat(client.deliver(DeliveryFixtures.revision(fixture), TOKEN).status()).isEqualTo(Status.DUPLICATE);
            assertThat(server.calls.get()).isEqualTo(2);
            assertThat(server.requests.get(0).body()).isEqualTo(server.requests.get(1).body());
            assertThat(server.requests.get(0).headers().getFirst("Idempotency-Key"))
                    .isEqualTo(server.requests.get(1).headers().getFirst("Idempotency-Key"));
        }
    }

    @Test
    void publicConstructionRequiresHttpsAndExplicitPositiveBounds() {
        for (String endpoint : List.of("http://127.0.0.1:8080", "https://user:private@example.invalid",
                "https://example.invalid/path", "https://example.invalid?token=private", "https://example.invalid#private")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HttpRecommendationDelivery(URI.create(endpoint), 1_000, 1_000, Duration.ofSeconds(1)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new HttpRecommendationDelivery(URI.create("https://example.invalid"), 0, 1, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpRecommendationDelivery(URI.create("https://example.invalid"), 1, 1, Duration.ZERO));
    }

    private static void failure(HttpRecommendationDelivery client, tools.jackson.databind.JsonNode fixture, Failure.Kind kind) {
        var failure = assertThrows(Failure.class, () -> client.deliver(DeliveryFixtures.revision(fixture), TOKEN));
        assertThat(failure.kind()).isEqualTo(kind);
        assertThat(failure.getCause()).isNull();
        assertThat(failure.toString()).doesNotContain(TOKEN, "private-remote-evidence", "protected summary");
    }

    private static void reply(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Reply { void handle(HttpExchange exchange, int number) throws IOException, InterruptedException; }
    private record Request(String method, String path, com.sun.net.httpserver.Headers headers, byte[] body) { }

    private static final class Stub implements AutoCloseable {
        final AtomicInteger calls = new AtomicInteger();
        final List<Request> requests = new CopyOnWriteArrayList<>();
        final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        final HttpServer server;

        Stub(Reply reply) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                            exchange.getRequestHeaders(), exchange.getRequestBody().readAllBytes()));
                    reply.handle(exchange, calls.incrementAndGet());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (IOException disconnectedTestClient) {
                    // Timeout/cancellation fixtures deliberately disconnect before their response is complete.
                }
            });
            server.start();
        }

        URI uri() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
        HttpRecommendationDelivery client(int request, int response, Duration timeout) {
            return new HttpRecommendationDelivery(uri(), request, response, timeout, true);
        }
        @Override public void close() { server.stop(0); executor.shutdownNow(); }
    }
}
