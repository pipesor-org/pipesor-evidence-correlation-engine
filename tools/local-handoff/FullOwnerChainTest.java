package org.pipesor.correlation.context.infrastructure.delivery;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PipelineServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.flyway.enabled=true")
@Import({PostgresTestConfiguration.class, OwnerChain.Fixture.class, FullOwnerChainTest.ReceiptFixture.class})
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

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        OwnerChain.properties(registry);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReceiptFixture {
        @Bean @Order(1)
        SecurityFilterChain fixtureReceiptSecurity(HttpSecurity http) throws Exception {
            // The test-only route delegates caller decisions to the actual receipt endpoint.
            return http.securityMatcher("/internal/v1/correlated-contexts")
                    .csrf(csrf -> csrf.disable()).authorizeHttpRequests(r -> r.anyRequest().permitAll()).build();
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

        ObjectNode a = protectedFixture();
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
        insertPipeline(binding);

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

                assertRetryable(send(a, endpoint, "Synthetic template"));
                assertThat(scope.queries).hasSize(1);
                assertThat(owners.membershipCalls).hasValue(0);
                assertEmptyCorrelation();
                createOrigin(a, binding);
                assertRetryable(send(a, endpoint, "Synthetic template"));
                assertThat(owners.membershipCalls).hasValue(1);
                assertEmptyCorrelation();

                assertThat(owners.evidenceStore.register(eventA.input())).isEqualTo(Registration.CREATED);
                assertNlp(send(a, endpoint, "Synthetic template"), 200, "accepted");
                assertThat(owners.evidenceStore.register(eventB.input())).isEqualTo(Registration.CREATED);

                scope.deniedRevisions.add(MEMBER_A_REVISION);
                scope.queries.clear();
                int membershipBeforeDenial = owners.membershipCalls.get();
                var denied = send(b, endpoint, "Synthetic template");
                assertNlp(denied, 422, "error");
                assertThat(denied.get("kind").asString()).isEqualTo("permanent");
                assertThat(scope.queries).extracting(query -> query.scopeRevision())
                        .containsExactly(MEMBER_B_REVISION, MEMBER_A_REVISION);
                assertThat(owners.membershipCalls).hasValue(membershipBeforeDenial + 1);
                assertThat(count("evidence.correlated_contexts")).isEqualTo(1);
                assertThat(count("diagnosis.correlated_context_receipts")).isEqualTo(1);

                scope.deniedRevisions.clear();
                assertNlp(send(b, endpoint, "Synthetic template"), 200, "accepted");
                createOrigin(c, eventC.binding());
                assertThat(owners.evidenceStore.register(eventC.input())).isEqualTo(Registration.CREATED);
                assertNlp(send(c, endpoint, "Synthetic template"), 200, "accepted");
                assertNlp(send(a, endpoint, "Changed template on redelivery"), 200, "duplicate");

                assertThat(count("evidence.correlation_groups")).isEqualTo(2);
                assertThat(count("evidence.correlated_contexts")).isEqualTo(3);
                assertThat(count("diagnosis.correlated_context_receipts")).isEqualTo(3);
                var introduced = List.of(eventA.input().evidenceId(), eventB.input().evidenceId());
                var canonicalMembers = introduced.stream().sorted(Comparator.comparing(UUID::toString)).toList();
                assertThat(members(eventA.input().executionId())).containsExactlyInAnyOrder(
                        List.of(eventA.input().evidenceId()), canonicalMembers);
                assertThat(members(eventC.input().executionId())).containsExactly(List.of(eventC.input().evidenceId()));
                assertThat(jdbc.queryForObject("""
                        SELECT signals::text FROM diagnosis.correlated_context_receipts
                        WHERE execution_id = ? AND cardinality(evidence_ids) = 2
                        """, String.class, eventA.input().executionId())).satisfies(signals ->
                        assertThat(JSON.readTree(signals)).extracting(signal -> signal.get("evidence_id").asString())
                                .containsExactlyElementsOf(introduced.stream().map(UUID::toString).toList()));
            }
        } finally {
            api.stop(0);
        }
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
    }

    private void createOrigin(ObjectNode body, ProtectedEvidenceV2.Binding binding) {
        var at = binding.occurredAt();
        var snapshot = new ExecutionSnapshot("1.0", UUID.randomUUID(), "execution.snapshot", "github_actions",
                at, binding.sourceUpdatedAt(), body.get("correlation_id").asString(),
                new ExecutionSnapshot.Pipeline(binding.githubRepositoryId(), binding.githubWorkflowId()),
                new ExecutionSnapshot.Execution(binding.githubRunId(), binding.runAttempt(), "main", "completed", "failure", at, at),
                new ExecutionSnapshot.Commit(binding.commitSha()), null, null,
                List.of(new ExecutionSnapshot.Stage(PipelineStage.INFRASTRUCTURE, "completed", at, at)),
                new ExecutionSnapshot.Failure(PipelineStage.INFRASTRUCTURE, "Synthetic fixture failure", at));
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
                input.write(JSON.writeValueAsBytes(Map.of("body", body, "endpoint", endpoint.toString(), "template", template,
                        "token", OwnerChain.token(OwnerChain.CORRELATION_AUDIENCE, OwnerChain.NLP_ROLE, true))));
            }
            assertThat(process.waitFor(40, TimeUnit.SECONDS)).as("NLP fixture finished").isTrue();
            assertThat(process.exitValue()).as("NLP fixture exit status").isZero();
            return JSON.readTree(process.getInputStream().readAllBytes());
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
