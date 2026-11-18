package org.pipesor.correlation.context.interfaces.rest;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Decision;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Token;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Outcome;
import org.pipesor.correlation.context.interfaces.rest.ProcessedEvidenceHttpHandler.PendingResponses;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessedEvidenceHttpHandlerTest {
    private static final URI ISSUER = URI.create("https://identity.test/workloads/v2.0");
    private static final UUID DIRECTORY = UUID.fromString("287f56c0-255d-45f4-a49c-7ea7e3bec987");
    private static final UUID CLIENT = UUID.fromString("e6edf15b-7744-4921-8fd3-6c8d6d08505e");
    private static final UUID PRINCIPAL = UUID.fromString("a8c7f0bb-71d8-4fd8-9dc7-bd8a25f8f073");
    private static final String ROLE = "Nlp.ProcessedEvidence.Send";
    private static final String TRACE = "ci-investigation-204";
    private static final String MESSAGE = "cf6b69cf-a28d-49f3-bceb-000000000001";
    private static final KeyPair KEYS = keys();
    private static final Caller CALLER = new Caller("nlp-producer", "approved-workflow-scope",
            UUID.fromString("d91d1df6-ae89-40c2-99b5-20edfb3e9ed0"), "6802100000000000000000000",
            "7710600000000000000000000", "92233720368547758081234567", "184467440737095516170");

    @Test
    void rejectsInvalidOrUnauthorizedWorkloadTokensBeforeProcessing() throws Exception {
        try (var api = new Api()) {
            assertThat(api.post(body(), null).statusCode()).isEqualTo(401);
            for (var changed : List.<Map<String,Object>>of(Map.of("aud", "wrong-api"), Map.of("scp", "Human.Access"),
                    Map.of("ver", "1.0"), Map.of("idtyp", "user"), Map.of("exp", Date.from(Instant.now().minusSeconds(5))))) {
                assertThat(api.post(body(), token(changed)).statusCode()).isEqualTo(401);
            }
            for (var changed : List.<Map<String,Object>>of(Map.of("oid", UUID.randomUUID().toString()),
                    Map.of("azp", UUID.randomUUID().toString()), Map.of("roles", List.of("Wrong")))) {
                assertThat(api.post(body(), token(changed)).statusCode()).isEqualTo(403);
            }
            assertThat(api.calls).hasValue(0);
        }
    }

    @Test
    void repliesWithTheOriginalMessageIdentityOnlyAfterAConfirmedOutcome() throws Exception {
        try (var api = new Api()) {
            String credential = token(Map.of());
            for (var pair : Map.of(Outcome.ACCEPTED, "accepted", Outcome.DUPLICATE, "duplicate").entrySet()) {
                api.outcome.set(pair.getKey());
                var reply = api.post(body(), credential);
                assertThat(reply.statusCode()).isEqualTo(200);
                assertThat(reply.headers().firstValue("Cache-Control")).contains("no-store");
                var json = CanonicalJson.mapper().readTree(reply.body());
                assertThat(json.propertyNames()).containsExactlyInAnyOrder("status", "message_id");
                assertThat(json.get("status").asString()).isEqualTo(pair.getValue());
                assertThat(json.get("message_id").asString()).isEqualTo(MESSAGE);
            }
            assertThat(api.calls).hasValue(2);
            assertThat(api.lastCaller.get()).isEqualTo(CALLER);
        }
    }

    @Test
    void mapsFailuresWithoutTreatingResourceDenialAsCallerAuthentication() throws Exception {
        try (var api = new Api()) {
            String credential = token(Map.of());
            var statuses = Map.of(Outcome.NOT_READY, 503, Outcome.UNAVAILABLE, 503, Outcome.REFERENCE_MISMATCH, 400,
                    Outcome.MESSAGE_CONFLICT, 409, Outcome.RESOURCE_DENIED, 422, Outcome.CAPACITY_EXCEEDED, 413,
                    Outcome.INVALID, 400);
            for (var result : statuses.entrySet()) {
                api.outcome.set(result.getKey());
                var reply = api.post(body(), credential);
                assertThat(reply.statusCode()).isEqualTo(result.getValue());
                assertThat(reply.body()).doesNotContain(MESSAGE, "Trace [redacted]", CALLER.tenantId().toString());
            }
            assertThrows(IllegalArgumentException.class, () -> new PendingResponses(200, 413));
            assertThrows(IllegalArgumentException.class, () -> new PendingResponses(403, 413));
            assertThrows(IllegalArgumentException.class, () -> new PendingResponses(422, 401));
        }
    }

    @Test
    void rejectsMalformedHeadersBodyAndSizeWithoutCallingTheProcessor() throws Exception {
        try (var api = new Api()) {
            String credential = token(Map.of());
            String raw = CanonicalJson.mapper().writeValueAsString(body());
            assertThat(api.post(raw + " {}", credential).statusCode()).isEqualTo(400);
            assertThat(api.post(raw.replace("\"template\":", "\"template\":\"private-value\",\"template\":"), credential).statusCode()).isEqualTo(400);
            assertThat(api.post(body().put("message_id", UUID.randomUUID().toString()), credential).statusCode()).isEqualTo(400);
            assertThat(api.post(body().put("correlation_id", "crossed"), credential).statusCode()).isEqualTo(400);
            assertThat(api.post(raw + " ".repeat(4096), credential).statusCode()).isEqualTo(413);
            assertThat(api.calls).hasValue(0);
        }
    }

    @Test
    void timesOutTheProcessorUsingMonotonicTimeAndRecoversAfterASlowBody() throws Exception {
        Instant now = Instant.now();
        try (var api = new Api(Clock.fixed(now, java.time.ZoneOffset.UTC), Duration.ofMillis(250))) {
            api.beforeProcess.set(() -> {
                try { Thread.sleep(350); }
                catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
            });
            assertThat(api.post(body(), token(Map.of("iat", Date.from(now)))).statusCode()).isEqualTo(503);
        }
        try (var api = new Api(); var socket = new Socket("127.0.0.1", api.server.getAddress().getPort())) {
            socket.setSoTimeout(5000);
            String raw = "POST " + ProcessedEvidenceHttpHandler.PATH + " HTTP/1.1\r\nHost: localhost\r\n"
                    + "Authorization: Bearer " + token(Map.of()) + "\r\nIdempotency-Key: " + MESSAGE
                    + "\r\nX-Correlation-ID: " + TRACE + "\r\nContent-Type: application/json\r\n"
                    + "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n1\r\n{\r\n";
            socket.getOutputStream().write(raw.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reader.readLine()).contains("503");
            socket.close();
            HttpResponse<String> reply = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                reply = api.post(body(), token(Map.of()));
                if (reply.statusCode() != 429) break;
                Thread.sleep(20);
            }
            assertThat(reply.statusCode()).isEqualTo(200);
            assertThat(api.calls).hasValue(1);
        }
    }

    @Test
    void timeSpentOnACompleteSlowBodyReducesTheProcessorBudgetEvenWithAFixedClock() throws Exception {
        Instant now = Instant.now();
        var observed = new AtomicReference<Instant>();
        var nowClock = Clock.fixed(now, java.time.ZoneOffset.UTC);
        try (var api = new Api(nowClock, Duration.ofSeconds(2), (input, caller, deadline) -> {
            observed.set(deadline);
            return Outcome.NOT_READY;
        }, null); var socket = new Socket("127.0.0.1", api.server.getAddress().getPort())) {
            socket.setSoTimeout(4500);
            byte[] payload = CanonicalJson.mapper().writeValueAsBytes(body());
            var headers = "POST " + ProcessedEvidenceHttpHandler.PATH + " HTTP/1.1\r\nHost: localhost\r\n"
                    + "Authorization: Bearer " + token(Map.of("iat", Date.from(now))) + "\r\nIdempotency-Key: " + MESSAGE
                    + "\r\nX-Correlation-ID: " + TRACE + "\r\nContent-Type: application/json"
                    + "\r\nContent-Length: " + payload.length + "\r\nConnection: close\r\n\r\n";
            var wire = socket.getOutputStream();
            wire.write(headers.getBytes(StandardCharsets.US_ASCII));
            wire.write(payload, 0, 1);
            wire.flush();
            Thread.sleep(750);
            wire.write(payload, 1, payload.length - 1);
            wire.flush();
            var reply = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            assertThat(reply.readLine()).contains("503");
        }
        assertThat(observed.get()).isAfter(now).isBefore(now.plusMillis(1600));
    }

    @Test
    void authInfrastructureFailureIsRetryableAndDoesNotInvokeTheProcessor() throws Exception {
        JwtDecoder unavailable = token -> { throw new org.springframework.security.oauth2.jwt.JwtException("private key service error"); };
        try (var api = new Api(Clock.systemUTC(), Duration.ofSeconds(2), null, unavailable)) {
            var reply = api.post(body(), token(Map.of()));
            assertThat(reply.statusCode()).isEqualTo(503);
            assertThat(reply.body()).doesNotContain("private key service error");
            assertThat(api.calls).hasValue(0);
        }
    }

    @Test
    void signedHttpIntakeConfirmsOnlyAfterTheRealStoreAndMatchingRecommendationReceipt() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:17.11")) {
            database.start();
            var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
            Flyway.configure().dataSource(source).defaultSchema("evidence").load().migrate();
            var scope = new AtomicReference<>(Decision.NOT_READY);
            var counters = new org.springframework.jdbc.core.JdbcTemplate(source);
            var operation = new AtomicReference<UUID>();
            var deliveries = new AtomicInteger();
            var receiver = new ReceiveProcessedEvidence(source, Clock.systemUTC(), (input, evidenceId, scopeRevision, deadline) -> scope.get(),
                    20_000, (revision, credential, remaining) -> {
                        assertThat(credential).isEqualTo("synthetic-recipient-token");
                        if (deliveries.incrementAndGet() == 1) {
                            operation.set(revision.contextId());
                            return new RecommendationDelivery.Receipt(UUID.randomUUID(), revision.contextId(),
                                    RecommendationDelivery.Status.ACCEPTED);
                        }
                        assertThat(revision.contextId()).isEqualTo(operation.get());
                        return new RecommendationDelivery.Receipt(operation.get(), operation.get(),
                                RecommendationDelivery.Status.DUPLICATE);
                    }, remaining -> new Token("synthetic-recipient-token", Instant.now().plusSeconds(60)));
            try (var api = new Api(Clock.systemUTC(), Duration.ofSeconds(5), receiver::receive, null)) {
                String credential = token(Map.of());
                String count = "SELECT count(*) FROM evidence.correlation_groups WHERE confirmed_context_id IS NOT NULL";
                assertThat(api.post(body(), credential).statusCode()).isEqualTo(503);
                assertThat(counters.queryForObject("SELECT count(*) FROM evidence.correlated_contexts", Integer.class)).isZero();
                scope.set(Decision.AUTHORIZED);
                assertThat(api.post(body(), credential).statusCode()).isEqualTo(503);
                assertThat(counters.queryForObject(count, Integer.class)).isZero();
                var replay = body().put("template", "Reprocessed template");
                var accepted = api.post(replay, credential);
                assertThat(accepted.statusCode()).isEqualTo(200);
                assertThat(CanonicalJson.mapper().readTree(accepted.body()).get("status").asString()).isEqualTo("accepted");
                assertThat(counters.queryForObject(count, Integer.class)).isEqualTo(1);
                assertThat(counters.queryForObject("SELECT id FROM evidence.correlated_contexts", UUID.class)).isEqualTo(operation.get());
                assertThat(CanonicalJson.mapper().readTree(api.post(replay, credential).body()).get("status").asString())
                        .isEqualTo("duplicate");
                scope.set(Decision.DENIED);
                assertThat(api.post(replay, credential).statusCode()).isEqualTo(422);
                assertThat(deliveries).hasValue(2);
            }
        }
    }

    private static ObjectNode body() throws Exception {
        var json = CanonicalJson.mapper();
        return (ObjectNode) json.readTree(Files.readAllBytes(Path.of("docs/contracts/nlp-processed-evidence-v2.examples.json"))).get("with_job");
    }

    private static String token(Map<String,Object> overrides) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER.toString()).audience("correlation-api")
                .claim("tid", DIRECTORY.toString()).claim("oid", PRINCIPAL.toString()).claim("azp", CLIENT.toString())
                .claim("roles", List.of(ROLE)).claim("ver", "2.0").claim("idtyp", "app")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(120)));
        overrides.forEach(claims::claim);
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        signed.sign(new RSASSASigner(KEYS.getPrivate()));
        return signed.serialize();
    }

    private static KeyPair keys() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (Exception unavailable) { throw new IllegalStateException(unavailable); }
    }

    private static final class Api implements AutoCloseable {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Caller> lastCaller = new AtomicReference<>();
        final AtomicReference<Outcome> outcome = new AtomicReference<>(Outcome.ACCEPTED);
        final AtomicReference<Runnable> beforeProcess = new AtomicReference<>(() -> {});
        final HttpServer server;
        final java.util.concurrent.ExecutorService requests = Executors.newVirtualThreadPerTaskExecutor();
        final HttpClient client = HttpClient.newHttpClient();
        final ProcessedEvidenceHttpHandler handler;
        final URI uri;

        Api() throws Exception { this(Clock.systemUTC(), Duration.ofSeconds(1)); }
        Api(Clock clock, Duration budget) throws Exception { this(clock, budget, null, null); }

        Api(Clock clock, Duration budget, ProcessedEvidenceHttpHandler.Processor processor, JwtDecoder customDecoder) throws Exception {
            var settings = new ProcessedEvidenceHttpHandler.Settings(ISSUER, URI.create("https://identity.test/keys"),
                    "correlation-api", DIRECTORY, CLIENT, PRINCIPAL, ROLE, CALLER, Duration.ZERO, budget, 4096, 8192, 1);
            var decoder = customDecoder == null
                    ? NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build() : customDecoder;
            handler = new ProcessedEvidenceHttpHandler(settings, (input, caller, deadline) -> {
                calls.incrementAndGet();
                lastCaller.set(caller);
                beforeProcess.get().run();
                return processor == null ? outcome.get() : processor.process(input, caller, deadline);
            }, new PendingResponses(422, 413), decoder, clock);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(requests);
            server.createContext(ProcessedEvidenceHttpHandler.PATH, handler);
            server.start();
            uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + ProcessedEvidenceHttpHandler.PATH);
        }
        HttpResponse<String> post(ObjectNode body, String token) throws Exception {
            return post(CanonicalJson.mapper().writeValueAsString(body), token);
        }
        HttpResponse<String> post(String body, String token) throws Exception {
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Idempotency-Key", MESSAGE).header("X-Correlation-ID", TRACE);
            if (token != null) request.header("Authorization", "Bearer " + token);
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close() { server.stop(0); handler.close(); requests.shutdownNow(); client.close(); }
    }
}
