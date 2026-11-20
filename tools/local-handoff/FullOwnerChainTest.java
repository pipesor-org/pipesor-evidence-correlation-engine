package org.pipesor.correlation.context.infrastructure.delivery;

import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Diagnosis;
import com.pipesor.recommendation.diagnosis.interfaces.rest.LocalDiagnosticEndpoint;
import com.pipesor.recommendation.receipt.ReceiptEndpoint;
import com.sun.net.httpserver.HttpServer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences.Token;
import org.pipesor.correlation.context.infrastructure.validation.OwnerChain;
import org.pipesor.correlation.context.interfaces.rest.ProcessedEvidenceHttpHandler;
import org.pipesor.logevent.evidence.application.ports.EvidenceStore.Registration;
import org.pipesor.logevent.evidence.interfaces.messaging.ProtectedEvidenceV2;
import org.pipesor.pipeline.GitHubConnectorDouble;
import org.pipesor.pipeline.OperationalPilotIdentity;
import org.pipesor.pipeline.PipelineServiceApplication;
import org.pipesor.pipeline.PostgresTestConfiguration;
import org.pipesor.pipeline.execution.application.commands.ExecutionSnapshot;
import org.pipesor.pipeline.execution.application.services.ExecutionSnapshotService;
import org.pipesor.pipeline.execution.interfaces.messaging.transform.ExecutionSnapshotTransformer;
import org.pipesor.pipeline.shared.domain.model.valueobjects.PipelineStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PipelineServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.enabled=true", "server.address=127.0.0.1"})
@Import({PostgresTestConfiguration.class, OwnerChain.Fixture.class, FullOwnerChainTest.ReceiptFixture.class, OperationalPilotIdentity.class})
class FullOwnerChainTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Clock CLOCK = Clock.systemUTC();
    private static final Duration BUDGET = Duration.ofSeconds(10);
    private static final int LIMIT = 20_000;
    private static final String RECOMMENDATION_TOKEN = "synthetic-recommendation-token";
    private static final String MEMBER_A_REVISION = "scope-rev-12";
    private static final String MEMBER_B_REVISION = "scope-rev-13";

    @LocalServerPort int port;
    @Autowired DataSource source;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExecutionSnapshotService snapshots;
    @Autowired OwnerChain.Scope scope;
    @Autowired LocalRead localRead;
    @Autowired GitHubConnectorDouble github;

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        OwnerChain.properties(registry);
    }

    static final class LocalRead {
        final AtomicReference<UUID> reviewId = new AtomicReference<>();
        final AtomicReference<Diagnosis> result = new AtomicReference<>();
        final String token;

        LocalRead() {
            var random = new byte[32];
            new SecureRandom().nextBytes(random);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReceiptFixture {
        @Bean @Order(1)
        SecurityFilterChain fixtureReceiptSecurity(HttpSecurity http) throws Exception {
            // The test-only route delegates caller decisions to the actual receipt endpoint.
            return http.securityMatcher("/internal/v1/correlated-contexts", LocalDiagnosticEndpoint.PATH,
                            "/local-demo/v1/diagnoses/*/review")
                    .csrf(csrf -> csrf.disable()).authorizeHttpRequests(r -> r.anyRequest().permitAll()).build();
        }

        @Bean LocalRead localRead() { return new LocalRead(); }

        @Bean
        RouterFunction<ServerResponse> fixtureDiagnosticRoute(LocalRead read) {
            return LocalDiagnosticEndpoint.routes(read.token, read.result::get);
        }

        @Bean
        RouterFunction<ServerResponse> fixtureReviewRoute(DataSource source, LocalRead read) {
            return DemoReview.routes(source, read.token);
        }

        @Bean
        RouterFunction<ServerResponse> fixtureReceiptRoute(DataSource source) {
            return ReceiptEndpoint.routes(source, (token, submission) -> RECOMMENDATION_TOKEN.equals(token)
                    ? ReceiptEndpoint.Decision.AUTHORIZED : ReceiptEndpoint.Decision.UNAUTHENTICATED, LIMIT);
        }
    }

    @Test
    void authenticatesAndAccumulatesOnlyWhileBothOwnerReferencesRemainAuthorized() throws Exception {
        migrate("evidence", "log_event_fixture_history", "log-event-migrations");
        migrate("evidence", "correlation_fixture_history", "correlation-migrations");
        migrate("diagnosis", "recommendation_fixture_history", "recommendation-migrations");

        assertThat(readDiagnosis(null).statusCode()).isEqualTo(401);
        assertThat(readDiagnosis(localRead.token).statusCode()).isEqualTo(503);

        ObjectNode a = protectedFixture();
        try (var log = FullOwnerChainTest.class.getResourceAsStream("/checkout-failure.log")) {
            ((ObjectNode) a.get("evidence")).put("excerpt",
                    new String(log.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip());
        }
        ObjectNode b = a.deepCopy().put("message_id", UUID.randomUUID().toString());
        ((ObjectNode) b.get("capture_context")).put("scope_revision", MEMBER_B_REVISION);
        ObjectNode c = a.deepCopy().put("message_id", UUID.randomUUID().toString());
        ((ObjectNode) c.get("execution")).put("run_attempt", 2);
        var eventA = decode(a);
        var eventB = decode(b);
        var eventC = decode(c);
        var binding = eventA.binding();
        var caller = new Caller("fixture-nlp", "fixture-workflow", binding.tenantId(), binding.githubAccountId(),
                binding.githubInstallationId(), binding.githubRepositoryId(), binding.githubWorkflowId());
        if (RegisteredE02.enabled()) RegisteredE02.register(port, github, binding);
        else insertPipeline(binding);

        var api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var owners = new OwnerChain(source, port, binding.tenantId(), binding.githubInstallationId());
             var delivery = new HttpRecommendationDelivery(loopback(port), LIMIT, 4096, BUDGET, true)) {
            var receiver = new ReceiveProcessedEvidence(source, CLOCK, owners.references::verify,
                    LIMIT, delivery::deliver, remaining -> new Token(RECOMMENDATION_TOKEN, CLOCK.instant().plusSeconds(60)));
            var settings = new ProcessedEvidenceHttpHandler.Settings(OwnerChain.ISSUER,
                    URI.create("https://identity.test/keys"), OwnerChain.CORRELATION_AUDIENCE, OwnerChain.DIRECTORY,
                    OwnerChain.NLP_CLIENT, OwnerChain.NLP_PRINCIPAL, OwnerChain.NLP_ROLE, caller,
                    Duration.ZERO, BUDGET, LIMIT, 8192, 2);
            try (var intake = new ProcessedEvidenceHttpHandler(settings, receiver::receive,
                    new ProcessedEvidenceHttpHandler.PendingResponses(422, 413), OwnerChain.decoder(), CLOCK)) {
                api.createContext(ProcessedEvidenceHttpHandler.PATH, intake);
                api.start();
                URI endpoint = loopback(api.getAddress().getPort());

                assertRetryable(send(a, endpoint, null));
                assertThat(scope.queries).hasSize(1);
                assertThat(owners.membershipCalls).hasValue(0);
                assertEmptyCorrelation();
                createOrigin(a, binding);
                assertRetryable(send(a, endpoint, null));
                assertThat(owners.membershipCalls).hasValue(1);
                assertEmptyCorrelation();

                assertThat(owners.evidenceStore.register(eventA.input())).isEqualTo(Registration.CREATED);
                assertNlp(send(a, endpoint, null), 200, "accepted");
                var diagnosis = DemoDiagnosis.verify(jdbc, eventA.input().executionId(), eventA.input().evidenceId(),
                        a.get("correlation_id").asString(), localRead.reviewId::set);
                localRead.result.set(diagnosis);
                if ("E02".equals(System.getProperty("pipesor.fixture.scenario"))) verifyOperationalHttp(diagnosis, binding);
                verifyDiagnosticHttp(diagnosis);
                assertThat(owners.evidenceStore.register(eventB.input())).isEqualTo(Registration.CREATED);

                scope.deniedRevisions.add(MEMBER_A_REVISION);
                scope.queries.clear();
                int membershipBeforeDenial = owners.membershipCalls.get();
                var denied = send(b, endpoint, null);
                assertNlp(denied, 422, "error");
                assertThat(denied.get("kind").asString()).isEqualTo("permanent");
                assertThat(scope.queries).extracting(query -> query.scopeRevision())
                        .containsExactly(MEMBER_B_REVISION, MEMBER_A_REVISION);
                assertThat(owners.membershipCalls).hasValue(membershipBeforeDenial + 1);
                assertThat(count("evidence.correlated_contexts")).isEqualTo(1);
                assertThat(count("diagnosis.correlated_context_receipts")).isEqualTo(1);

                scope.deniedRevisions.clear();
                assertNlp(send(b, endpoint, null), 200, "accepted");
                createOrigin(c, eventC.binding());
                assertThat(owners.evidenceStore.register(eventC.input())).isEqualTo(Registration.CREATED);
                assertNlp(send(c, endpoint, null), 200, "accepted");
                assertNlp(send(a, endpoint, "Changed template on redelivery"), 200, "duplicate");

                assertThat(count("evidence.correlation_groups")).isEqualTo(2);
                assertThat(count("evidence.correlated_contexts")).isEqualTo(3);
                assertThat(count("diagnosis.correlated_context_receipts")).isEqualTo(3);
                var introduced = List.of(eventA.input().evidenceId(), eventB.input().evidenceId());
                var canonicalMembers = introduced.stream().sorted(Comparator.comparing(UUID::toString)).toList();
                assertThat(members(eventA.input().executionId())).containsExactlyInAnyOrder(
                        List.of(eventA.input().evidenceId()), canonicalMembers);
                assertThat(members(eventC.input().executionId())).containsExactly(List.of(eventC.input().evidenceId()));
                var receipt = jdbc.queryForMap("""
                        SELECT scope_revision, signals::text AS signals
                        FROM diagnosis.correlated_context_receipts
                        WHERE execution_id = ? AND cardinality(evidence_ids) = 2
                        """, eventA.input().executionId());
                assertThat(receipt.get("scope_revision")).isEqualTo(MEMBER_B_REVISION);
                var signals = JSON.readTree((String) receipt.get("signals"));
                assertThat(signals).extracting(signal -> signal.get("evidence_id").asString())
                        .containsExactlyElementsOf(introduced.stream().map(UUID::toString).toList());
                for (var signal : signals) {
                    assertThat(signal.propertyNames()).containsExactlyInAnyOrder(
                            "evidence_id", "origin_context_id", "correlation_id", "stage", "summary", "content");
                }
                System.out.println("PILOT_LEGACY_PROVENANCE mixed revisions retained; v1 receipt carries no member capture scope");
            }
        } finally {
            api.stop(0);
        }
    }

    private HttpResponse<String> readDiagnosis(String token) throws Exception {
        var request = HttpRequest.newBuilder(loopback(port).resolve(LocalDiagnosticEndpoint.PATH)).timeout(BUDGET);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void verifyDiagnosticHttp(Diagnosis diagnosis) throws Exception {
        assertThat(readDiagnosis("invalid").statusCode()).isEqualTo(401);
        var response = readDiagnosis(localRead.token);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.valueToTree(diagnosis));
        System.out.println("PIPESOR_DIAGNOSTIC_HTTP 401 503 200 verified for " + diagnosis.correlationId());
        UUID reviewId = localRead.reviewId.get();
        if (reviewId != null) {
            var uri = loopback(port).resolve("/local-demo/v1/diagnoses/" + reviewId + "/review");
            var client = HttpClient.newHttpClient();
            var denied = HttpRequest.newBuilder(uri).header("Authorization", "Bearer fixture-other-scope").build();
            assertThat(client.send(denied, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(404);
            var accepted = HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + localRead.token).build();
            var view = client.send(accepted, HttpResponse.BodyHandlers.ofString());
            assertThat(view.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(view.body()).path("original")).isEqualTo(JSON.valueToTree(diagnosis));
        }
        if (RegisteredE02.interactive()) {
            RegisteredE02.present(diagnosis.incidentId(), java.util.Objects.requireNonNull(reviewId), localRead.token);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM diagnosis.human_reviews WHERE diagnostic_id = ?",
                    Integer.class, reviewId)).isPositive();
            return;
        }
        var browserCheck = System.getenv("PIPESOR_DEMO_BROWSER_CHECK");
        if (browserCheck != null && !browserCheck.isBlank()) {
            var log = Files.createTempFile("pipesor-browser-", ".log");
            try {
                var command = new ProcessBuilder("node", browserCheck)
                        .redirectErrorStream(true).redirectOutput(log.toFile());
                command.environment().put("PIPESOR_DEMO_PORT", Integer.toString(port));
                command.environment().put("PIPESOR_DEMO_TOKEN", localRead.token);
                if ("E02".equals(System.getProperty("pipesor.fixture.scenario"))) {
                    command.environment().put("PIPESOR_OPERATIONAL_TOKEN",
                            OperationalPilotIdentity.token(OperationalPilotIdentity.TENANT));
                }
                if (reviewId != null) {
                    command.environment().put("PIPESOR_REVIEW_IDS", reviewId.toString());
                    command.environment().put("PIPESOR_REVIEW_TOKEN", localRead.token);
                }
                var browser = command.start();
                try {
                    assertThat(browser.waitFor(60, TimeUnit.SECONDS)).as("Browser check finished").isTrue();
                    System.out.print(Files.readString(log));
                    assertThat(browser.exitValue()).as("Browser check status").isZero();
                } finally {
                    if (browser.isAlive()) browser.destroyForcibly();
                }
            } finally {
                Files.deleteIfExists(log);
            }
        }
        if (reviewId != null) {
            assertThat(jdbc.queryForList("""
                    SELECT decision FROM diagnosis.human_reviews WHERE diagnostic_id = ? ORDER BY revision
                    """, String.class, reviewId)).containsExactly("CONFIRMED", "CORRECTED", "REJECTED");
            var endpoint = loopback(port).resolve("/local-demo/v1/diagnoses/" + reviewId + "/review");
            var request = HttpRequest.newBuilder(endpoint).header("Authorization", "Bearer " + localRead.token).build();
            var finalView = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(finalView.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(finalView.body()).path("original")).isEqualTo(JSON.valueToTree(diagnosis));
            System.out.println("PIPESOR_INTEGRATED_REVIEW_RECEIPT " + JSON.writeValueAsString(JSON.readTree(finalView.body())));
            System.out.println("PIPESOR_INTEGRATED_REVIEW persisted CONFIRMED CORRECTED REJECTED; cross-scope 404");
        }
    }

    private void verifyOperationalHttp(Diagnosis diagnosis, ProtectedEvidenceV2.Binding binding) throws Exception {
        String repository = binding.githubRepositoryId();
        var uri = loopback(port).resolve("/api/v1/incidents/" + diagnosis.incidentId());
        var client = HttpClient.newHttpClient();
        var valid = OperationalPilotIdentity.token(OperationalPilotIdentity.TENANT);
        var request = HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + valid).build();
        var detail = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(detail.body()).path("correlationId").asString()).isEqualTo(diagnosis.correlationId());
        var wrongTenant = OperationalPilotIdentity.token(UUID.randomUUID().toString());
        var wrong = HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + wrongTenant).build();
        assertThat(client.send(wrong, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        OperationalPilotIdentity.denyRepository(github, repository);
        try {
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        } finally {
            if (RegisteredE02.enabled()) RegisteredE02.facts(github, binding);
            else OperationalPilotIdentity.grant(github, repository);
        }
        OperationalPilotIdentity.unavailable(github, true);
        try {
            assertThat(client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(503);
        } finally {
            OperationalPilotIdentity.unavailable(github, false);
        }
        System.out.println("PILOT_PIPELINE_HTTP persisted incident, cross-tenant 401, repository denial 403, authority outage 503");
    }

    private void migrate(String schema, String history, String location) {
        // Log/Event and Correlation share the schema while owning separate migration histories.
        Flyway.configure().dataSource(source).defaultSchema(schema).table(history).locations("classpath:" + location)
                .baselineOnMigrate(true).baselineVersion("0").load().migrate();
    }

    private void insertPipeline(ProtectedEvidenceV2.Binding binding) {
        var repository = UUID.randomUUID();
        jdbc.update("INSERT INTO ops.repositories (id, github_repository_id, full_name, created_at) VALUES (?, ?, 'fixture/workflow', now())",
                repository, binding.githubRepositoryId());
        jdbc.update("""
                INSERT INTO ops.pipelines (id, repository_id, github_workflow_id, workflow_name, workflow_file,
                    criticality, monitoring_status, created_at)
                VALUES (?, ?, ?, 'Fixture workflow', 'fixture.yml', 'HIGH', 'ACTIVE', now())
                """, binding.pipelineId(), repository, binding.githubWorkflowId());
        var actor = OperationalPilotIdentity.grant(github, binding.githubRepositoryId());
        jdbc.update("""
                INSERT INTO ops.pipeline_access_grants (id, pipeline_id, principal_ref, capability, correlation_id, created_at)
                VALUES (?, ?, ?, 'VIEW', 'operational-pilot', now())
                """, UUID.randomUUID(), binding.pipelineId(), actor);
    }

    private void createOrigin(ObjectNode body, ProtectedEvidenceV2.Binding binding) {
        var at = binding.occurredAt();
        var stage = PipelineStage.valueOf(body.at("/evidence/stage").asString());
        var snapshot = new ExecutionSnapshot("1.0", UUID.randomUUID(), "execution.snapshot", "github_actions",
                at, binding.sourceUpdatedAt(), body.get("correlation_id").asString(),
                new ExecutionSnapshot.Pipeline(binding.githubRepositoryId(), binding.githubWorkflowId()),
                new ExecutionSnapshot.Execution(binding.githubRunId(), binding.runAttempt(), "main", "completed", "failure", at, at),
                new ExecutionSnapshot.Commit(binding.commitSha()), null, null,
                List.of(new ExecutionSnapshot.Stage(stage, "completed", at, at)),
                new ExecutionSnapshot.Failure(stage, "Synthetic fixture failure", at));
        assertThat(snapshots.handle(new ExecutionSnapshotTransformer().fromJson(JSON.writeValueAsString(snapshot))))
                .isEqualTo("APPLIED");
    }

    private void assertEmptyCorrelation() {
        assertThat(count("evidence.correlated_contexts")).isZero();
        assertThat(count("diagnosis.correlated_context_receipts")).isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<List<UUID>> members(UUID execution) {
        return jdbc.query("SELECT evidence_ids FROM diagnosis.correlated_context_receipts WHERE execution_id = ?",
                (row, number) -> List.of((UUID[]) row.getArray(1).getArray()), execution);
    }

    private static ObjectNode protectedFixture() throws Exception {
        try (var body = FullOwnerChainTest.class.getResourceAsStream("/protected-workflow-evidence-v2.json")) {
            return (ObjectNode) JSON.readTree(body.readAllBytes());
        }
    }

    private static ProtectedEvidenceV2 decode(ObjectNode body) {
        return ProtectedEvidenceV2.decode(JSON.writeValueAsBytes(body), LIMIT, body.get("message_id").asString(),
                body.get("correlation_id").asString(), "protected_workflow_evidence", 2);
    }

    private static URI loopback(int port) {
        return URI.create("http://127.0.0.1:" + port);
    }

    private static void assertNlp(JsonNode result, int status, String outcome) {
        assertThat(result.get("http_status").asInt()).isEqualTo(status);
        assertThat(result.get("status").asString()).isEqualTo(outcome);
    }

    private static void assertRetryable(JsonNode result) {
        assertNlp(result, 503, "error");
        assertThat(result.get("kind").asString()).isEqualTo("transient");
    }

    private static JsonNode send(ObjectNode body, URI endpoint, String template) throws Exception {
        var command = new ProcessBuilder(System.getProperty("pipesor.fixture.python"), System.getProperty("pipesor.fixture.nlp"))
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        command.environment().put("no_proxy", "127.0.0.1");
        var process = command.start();
        try {
            try (var input = process.getOutputStream()) {
                var request = JSON.valueToTree(Map.of("body", body, "endpoint", endpoint.toString(),
                        "token", OwnerChain.token(OwnerChain.CORRELATION_AUDIENCE, OwnerChain.NLP_ROLE, true)));
                if (template != null) ((ObjectNode) request).put("template", template);
                input.write(JSON.writeValueAsBytes(request));
            }
            assertThat(process.waitFor(40, TimeUnit.SECONDS)).as("NLP fixture finished").isTrue();
            assertThat(process.exitValue()).as("NLP fixture exit status").isZero();
            return JSON.readTree(process.getInputStream().readAllBytes());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
