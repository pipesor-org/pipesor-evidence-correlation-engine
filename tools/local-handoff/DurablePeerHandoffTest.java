package org.pipesor.correlation.context.infrastructure.delivery;

import com.pipesor.recommendation.RecommendationServiceApplication;
import com.pipesor.recommendation.receipt.ReceiptEndpoint;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Outcome;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Decision;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Token;
import org.pipesor.correlation.context.interfaces.rest.transform.NlpEvidenceV2;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "pipesor.rag.endpoint=https://rag.example.invalid",
        "pipesor.rag.audience=api://00000000-0000-0000-0000-000000000001"
}, classes = {RecommendationServiceApplication.class, DurablePeerHandoffTest.Fixture.class})
public class DurablePeerHandoffTest {
    private static final int LIMIT = 20_000;
    private static final String TOKEN = "synthetic-authorized-correlation";
    @LocalServerPort int recommendationPort;
    @Autowired PGSimpleDataSource source;

    @TestConfiguration(proxyBeanMethods = false)
    public static class Fixture {
        @Bean(initMethod = "start", destroyMethod = "stop")
        PostgreSQLContainer receiptDatabase() {
            return new PostgreSQLContainer("postgres:17.11");
        }

        @Bean
        PGSimpleDataSource receiptDataSource(PostgreSQLContainer database) {
            var source = new PGSimpleDataSource();
            source.setURL(database.getJdbcUrl());
            source.setUser(database.getUsername());
            source.setPassword(database.getPassword());
            Flyway.configure().dataSource(source).defaultSchema("diagnosis").load().migrate();
            Flyway.configure().dataSource(source).defaultSchema("evidence")
                    .locations("classpath:correlation-migrations").load().migrate();
            return source;
        }

        @Bean
        RouterFunction<ServerResponse> receiptRoutes(PGSimpleDataSource source) throws Exception {
            var input = decode(wireFixture());
            // Synthetic authority binds this fixture's exact scope and logical references.
            return ReceiptEndpoint.routes(source, (token, submission) -> {
                if (!TOKEN.equals(token)) return ReceiptEndpoint.Decision.UNAUTHENTICATED;
                boolean matches = submission.tenantId().equals(input.captureContext().tenantId())
                        && submission.installationId().equals(input.captureContext().githubInstallationId())
                        && submission.repositoryId().equals(input.pipeline().githubRepositoryId())
                        && submission.pipelineId().equals(input.pipelineId())
                        && submission.scopeRevision().equals(input.captureContext().scopeRevision())
                        && submission.executionId().equals(input.executionId())
                        && submission.incidentId().equals(input.incidentId())
                        && submission.evidenceIds().equals(List.of(input.evidenceId()));
                return matches ? ReceiptEndpoint.Decision.AUTHORIZED : ReceiptEndpoint.Decision.FORBIDDEN;
            }, LIMIT);
        }
    }

    @Test
    void recoversLostRealReceiptWithIdenticalOperationAndOnlyOneDownstreamRow() throws Exception {
        var body = wireFixture();
        var input = decode(body);
        var caller = new Caller("synthetic-signed-nlp", "synthetic-scope-" + UUID.randomUUID(),
                input.captureContext().tenantId(), input.captureContext().githubAccountId(),
                input.captureContext().githubInstallationId(), input.pipeline().githubRepositoryId(),
                input.pipeline().githubWorkflowId());
        var permission = new AtomicReference<>(Decision.NOT_READY);
        var requests = new CopyOnWriteArrayList<Observed>();
        var proxyFailure = new AtomicReference<Throwable>();
        var proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var forwarding = HttpClient.newHttpClient()) {
            proxy.createContext("/internal/v1/correlated-contexts", exchange -> {
                try {
                    byte[] bytes = exchange.getRequestBody().readAllBytes();
                    String operation = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                    String correlation = exchange.getRequestHeaders().getFirst("X-Correlation-ID");
                    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                                    + recommendationPort + exchange.getRequestURI().getPath()))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", exchange.getRequestHeaders().getFirst("Content-Type"))
                            .header("Authorization", exchange.getRequestHeaders().getFirst("Authorization"))
                            .header("Idempotency-Key", operation).header("X-Correlation-ID", correlation)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
                    var response = forwarding.send(request, HttpResponse.BodyHandlers.ofByteArray());
                    requests.add(new Observed(operation, correlation, bytes, response.statusCode(), response.body()));
                    exchange.getResponseHeaders().set("Content-Type", response.headers()
                            .firstValue("Content-Type").orElse("application/json"));
                    if (requests.size() == 1 && response.statusCode() == 200) {
                        // Truncate only after the real receiver committed and returned its receipt.
                        exchange.sendResponseHeaders(200, response.body().length + 1L);
                    } else {
                        exchange.sendResponseHeaders(response.statusCode(), response.body().length);
                        exchange.getResponseBody().write(response.body());
                    }
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    proxyFailure.compareAndSet(null, failure);
                } catch (Exception failure) {
                    proxyFailure.compareAndSet(null, failure);
                } finally {
                    exchange.close();
                }
            });
            proxy.start();
            try (var delivery = new HttpRecommendationDelivery(
                    URI.create("http://127.0.0.1:" + proxy.getAddress().getPort()),
                    LIMIT, 4096, Duration.ofSeconds(10), true)) {
                var receiver = new ReceiveProcessedEvidence(source, Clock.systemUTC(),
                        (event, evidenceId, revision, deadline) -> permission.get(), LIMIT, delivery::deliver,
                        remaining -> new Token(TOKEN, Instant.now().plusSeconds(60)));

                assertThat(receiver.receive(input, caller, deadline())).isEqualTo(Outcome.NOT_READY);
                assertThat(requests).isEmpty();
                assertThat(count("SELECT count(*) FROM evidence.correlation_receipts")).isZero();
                assertThat(count("SELECT count(*) FROM diagnosis.correlated_context_receipts")).isZero();

                permission.set(Decision.AUTHORIZED);
                assertThat(receiver.receive(input, caller, deadline())).isEqualTo(Outcome.UNAVAILABLE);
                assertThat(proxyFailure.get()).isNull();
                assertThat(requests).hasSize(1);
                var first = requests.getFirst();
                assertThat(first.status()).isEqualTo(200);
                assertThat(json(first.response()).get("status").asString()).isEqualTo("accepted");
                UUID operation = UUID.fromString(first.operation());
                assertThat(count("SELECT count(*) FROM diagnosis.correlated_context_receipts")).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM evidence.correlation_receipts")).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM evidence.correlation_groups WHERE confirmed_context_id IS NOT NULL"))
                        .isZero();
                assertPersisted(operation, input, first.body());

                var replay = decode(body.deepCopy().put("template", "Updated mutable Drain3 enrichment"));
                assertThat(receiver.receive(replay, caller, deadline())).isEqualTo(Outcome.ACCEPTED);
                assertThat(proxyFailure.get()).isNull();
                assertThat(requests).hasSize(2);
                var second = requests.getLast();
                assertThat(second.operation()).isEqualTo(first.operation());
                assertThat(second.correlation()).isEqualTo(input.correlationId());
                assertThat(second.body()).containsExactly(first.body());
                assertThat(second.status()).isEqualTo(200);
                assertThat(json(second.response()).get("status").asString()).isEqualTo("duplicate");
                assertThat(count("SELECT count(*) FROM diagnosis.correlated_context_receipts")).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM evidence.correlation_receipts")).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM evidence.correlation_groups WHERE confirmed_context_id IS NOT NULL"))
                        .isEqualTo(1);
                try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                        "SELECT confirmed_context_id FROM evidence.correlation_groups WHERE scope_key = ?")) {
                    statement.setString(1, caller.scopeKey());
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getObject(1, UUID.class)).isEqualTo(operation);
                        assertThat(rows.next()).isFalse();
                    }
                }

                assertThat(receiver.receive(replay, caller, deadline())).isEqualTo(Outcome.DUPLICATE);
                permission.set(Decision.DENIED);
                assertThat(receiver.receive(replay, caller, deadline())).isEqualTo(Outcome.RESOURCE_DENIED);
                assertThat(requests).hasSize(2);
                assertPersisted(operation, input, first.body());
            }
        } finally {
            proxy.stop(0);
        }
    }

    private void assertPersisted(UUID operation, NlpEvidenceV2 input, byte[] originalBody) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("""
                SELECT context_id, execution_id, incident_id, correlation_id, signals, scope_revision, evidence_ids
                FROM diagnosis.correlated_context_receipts WHERE operation_id = ?
                """)) {
            statement.setObject(1, operation);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1, UUID.class)).isEqualTo(operation);
                assertThat(rows.getObject(2, UUID.class)).isEqualTo(input.executionId());
                assertThat(rows.getObject(3, UUID.class)).isEqualTo(input.incidentId());
                assertThat(rows.getString(4)).isEqualTo(input.correlationId());
                assertThat(CanonicalJson.mapper().readTree(rows.getString(5))).isEqualTo(json(originalBody).get("signals"));
                assertThat(rows.getString(6)).isEqualTo(input.captureContext().scopeRevision());
                assertThat((UUID[]) rows.getArray(7).getArray()).containsExactly(input.evidenceId());
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private long count(String sql) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static Instant deadline() { return Instant.now().plusSeconds(20); }

    private static ObjectNode wireFixture() throws Exception {
        try (var input = DurablePeerHandoffTest.class.getResourceAsStream("/nlp-processed-evidence-v2.examples.json")) {
            return (ObjectNode) CanonicalJson.mapper().readTree(input.readAllBytes()).get("with_job");
        }
    }

    private static NlpEvidenceV2 decode(ObjectNode body) {
        return NlpEvidenceV2.decode(CanonicalJson.mapper().writeValueAsBytes(body), LIMIT,
                body.get("message_id").asString(), body.get("correlation_id").asString());
    }

    private static tools.jackson.databind.JsonNode json(byte[] bytes) {
        return CanonicalJson.mapper().readTree(bytes);
    }

    private record Observed(String operation, String correlation, byte[] body, int status, byte[] response) { }
}
