package org.pipesor.correlation.context.infrastructure.validation;

import org.pipesor.correlation.shared.security.TransportPolicy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Decision;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Owner;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Token;
import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpOwnerReferencesTest {
    @Test
    void sharesBindingAndDeadlineButUsesSeparateRequestsAndRecipientTokens() throws Exception {
        var input = fixture();
        try (var peers = new Peers(); var client = peers.client(owner -> validToken(owner))) {
            assertThat(client.verify(input, Instant.now().plusSeconds(5))).isEqualTo(Decision.AUTHORIZED);
            var pipeline = peers.pipelineRequest.get();
            var membership = peers.membershipRequest.get();
            assertThat(pipeline.propertyNames()).hasSize(14);
            assertThat(membership.propertyNames()).hasSize(10);
            assertThat(pipeline.get("githubRepositoryId").asString()).isEqualTo(input.pipeline().githubRepositoryId());
            assertThat(pipeline.get("githubRunId").asString()).isEqualTo(input.execution().githubRunId());
            assertThat(pipeline.get("runAttempt").intValue()).isEqualTo(input.execution().runAttempt());
            assertThat(membership.get("evidenceId").asString()).isEqualTo(input.evidenceId().toString());
            assertThat(pipeline.get("requestId")).isNotEqualTo(membership.get("requestId"));
            for (var field : List.of("correlationId", "tenantId", "githubInstallationId", "scopeRevision", "executionId", "incidentId")) {
                assertThat(membership.get(field)).isEqualTo(pipeline.get(field));
            }
            assertThat(Instant.parse(membership.get("deadlineAt").asString()))
                    .isBeforeOrEqualTo(Instant.parse(pipeline.get("deadlineAt").asString()));

            var historicalEvidence = java.util.UUID.randomUUID();
            assertThat(client.verify(input, historicalEvidence, "previous-scope-rev", Instant.now().plusSeconds(5)))
                    .isEqualTo(Decision.AUTHORIZED);
            assertThat(peers.pipelineRequest.get().get("scopeRevision").asString()).isEqualTo("previous-scope-rev");
            assertThat(peers.membershipRequest.get().get("scopeRevision").asString()).isEqualTo("previous-scope-rev");
            assertThat(peers.membershipRequest.get().get("evidenceId").asString()).isEqualTo(historicalEvidence.toString());
            assertThat(peers.membershipRequest.get().get("executionId")).isEqualTo(membership.get("executionId"));
        }
    }

    @Test
    void failsClosedBeforeMembershipAndDistinguishesBoundBusinessOutcomes() throws Exception {
        var input = fixture();
        try (var peers = new Peers(); var client = peers.client(HttpOwnerReferencesTest::validToken)) {
            for (var outcome : List.of("DENIED", "NOT_READY", "MISMATCH")) {
                peers.pipelineResponse.set(outcome);
                assertThat(client.verify(input, Instant.now().plusSeconds(5))).isEqualTo(Decision.valueOf(outcome));
            }
            for (var unusable : List.of("wrong-binding", "uncacheable-required", "overlarge", "service-refusal")) {
                peers.pipelineResponse.set(unusable);
                assertThat(client.verify(input, Instant.now().plusSeconds(5))).isEqualTo(Decision.UNAVAILABLE);
            }
            assertThat(peers.membershipCalls).hasValue(0);
            peers.pipelineResponse.set("AUTHORIZED");
            peers.membershipResponse.set("NOT_READY");
            assertThat(client.verify(input, Instant.now().plusSeconds(5))).isEqualTo(Decision.NOT_READY);
            peers.membershipResponse.set("wrong-binding");
            assertThat(client.verify(input, Instant.now().plusSeconds(5))).isEqualTo(Decision.UNAVAILABLE);
            assertThat(client.verify(input, Instant.now().minusNanos(1))).isEqualTo(Decision.UNAVAILABLE);
            assertThat(peers.membershipCalls).hasValue(2);
        }
    }

    @Test
    void membershipCannotUseADeadlineBeyondTheOperationalConfirmation() throws Exception {
        var input = fixture();
        try (var peers = new Peers(); var client = peers.client(owner -> owner == Owner.PIPELINE
                ? new Token("synthetic-PIPELINE", Instant.now().plusMillis(700))
                : validToken(owner))) {
            peers.membershipResponse.set("late");
            assertThat(client.verify(input, Instant.now().plusSeconds(5))).isEqualTo(Decision.UNAVAILABLE);
            assertThat(peers.membershipCalls).hasValue(1);
        }
        var origin = URI.create("http://127.0.0.1");
        assertThrows(IllegalArgumentException.class, () ->
                new HttpOwnerReferences(origin, origin, (owner, time) -> validToken(owner), Duration.ofSeconds(1), 4096, 1024, TransportPolicy.forProfiles("azure")));
    }

    private static Token validToken(Owner owner) {
        return new Token("synthetic-" + owner.name(), Instant.now().plusSeconds(15));
    }

    private static NlpEvidenceV2 fixture() throws IOException {
        var mapper = CanonicalJson.mapper();
        var body = mapper.readTree(Files.readAllBytes(Path.of("docs/contracts/nlp-processed-evidence-v2.examples.json"))).get("with_job");
        return NlpEvidenceV2.decode(mapper.writeValueAsBytes(body), 4096,
                body.get("message_id").asString(), body.get("correlation_id").asString());
    }

    private static final class Peers implements AutoCloseable {
        final HttpServer server;
        final URI origin;
        final AtomicReference<String> pipelineResponse = new AtomicReference<>("AUTHORIZED");
        final AtomicReference<String> membershipResponse = new AtomicReference<>("AUTHORIZED");
        final AtomicReference<JsonNode> pipelineRequest = new AtomicReference<>();
        final AtomicReference<JsonNode> membershipRequest = new AtomicReference<>();
        final AtomicInteger membershipCalls = new AtomicInteger();

        Peers() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            server.createContext("/internal/v1/evidence/operational-reference-checks",
                    exchange -> respond(exchange, Owner.PIPELINE, pipelineResponse.get(), pipelineRequest));
            server.createContext("/internal/v1/evidence/membership-checks", exchange -> {
                membershipCalls.incrementAndGet();
                respond(exchange, Owner.LOG_EVENT, membershipResponse.get(), membershipRequest);
            });
            server.start();
        }

        HttpOwnerReferences client(java.util.function.Function<Owner, Token> tokens) {
            return new HttpOwnerReferences(origin, origin, (owner, remaining) -> tokens.apply(owner),
                    Duration.ofSeconds(4), 4096, 1024, Clock.systemUTC(), TransportPolicy.forProfiles("local"));
        }

        private void respond(HttpExchange exchange, Owner owner, String behavior, AtomicReference<JsonNode> seen) throws IOException {
            try (exchange) {
                var mapper = CanonicalJson.mapper();
                var input = mapper.readTree(exchange.getRequestBody().readAllBytes());
                seen.set(input);
                String trace = input.get("correlationId").asString();
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-" + owner.name());
                assertThat(exchange.getRequestHeaders().getFirst("X-Correlation-Id")).isEqualTo(trace);
                if ("late".equals(behavior)) {
                    try { Thread.sleep(1100); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); return; }
                }
                if ("service-refusal".equals(behavior)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                var reply = mapper.createObjectNode()
                        .put("requestId", "wrong-binding".equals(behavior) ? "crossed" : input.get("requestId").asString())
                        .put("correlationId", trace)
                        .put("decision", List.of("DENIED", "NOT_READY", "MISMATCH").contains(behavior) ? behavior : "AUTHORIZED");
                if (!"uncacheable-required".equals(behavior)) exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                byte[] bytes = "overlarge".equals(behavior) ? new byte[1025] : mapper.writeValueAsBytes(reply);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        }

        @Override public void close() { server.stop(0); }
    }
}
