package org.pipesor.correlation.context.infrastructure.delivery;

import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Diagnosis;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Evidence;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Hypothesis;
import com.pipesor.recommendation.retrieval.application.ports.RagUnavailableException;
import com.pipesor.recommendation.retrieval.domain.AuthorizationScope;
import com.pipesor.recommendation.retrieval.domain.CorpusKind;
import com.pipesor.recommendation.retrieval.domain.RetrievalQuery;
import com.pipesor.recommendation.retrieval.domain.RetrievalResult;
import com.pipesor.recommendation.retrieval.infrastructure.RagClientProperties;
import com.pipesor.recommendation.retrieval.infrastructure.RestRagKnowledgeRetriever;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class DemoDiagnosis {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID CHUNK = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DOCUMENT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    static Diagnosis verify(JdbcTemplate jdbc, UUID execution, UUID expectedEvidence, String correlation) {
        // shortcut: test-only reconstruction from the owned receipt, replace with the approved PD-011 query.
        var receipt = jdbc.queryForMap("""
                SELECT operation_id, incident_id, tenant_id, github_installation_id, github_repository_id,
                       pipeline_id, scope_revision, correlation_id, signals::text AS signals_json
                FROM diagnosis.correlated_context_receipts WHERE execution_id = ?
                """, execution);
        var context = (UUID) receipt.get("operation_id");
        var incident = (UUID) receipt.get("incident_id");
        var signals = JSON.readTree((String) receipt.get("signals_json"));
        assertThat(signals).hasSize(1);
        var signal = signals.get(0);
        var evidence = new Evidence(UUID.fromString(signal.get("evidence_id").asString()),
                UUID.fromString(signal.get("origin_context_id").asString()), signal.get("stage").asString(),
                signal.get("content").asString());
        assertThat(evidence.evidenceId()).isEqualTo(expectedEvidence);
        assertThat(evidence.originContextId()).isEqualTo(context);
        assertThat(evidence.content()).contains("Could not resolve host: git.example.invalid");
        assertThat(receipt.get("correlation_id")).isEqualTo(correlation);
        assertThat(signal.get("correlation_id").asString()).isEqualTo(correlation);

        var scope = new AuthorizationScope((UUID) receipt.get("tenant_id"),
                (String) receipt.get("github_installation_id"), (String) receipt.get("github_repository_id"),
                (UUID) receipt.get("pipeline_id"), (String) receipt.get("scope_revision"));
        var query = new RetrievalQuery(correlation, evidence.content(), scope,
                List.of(CorpusKind.AUTHORIZED_TECHNICAL_DOCUMENTATION), 1);
        // Fixture transport redirects the client's HTTPS destination to the local test-only listener.
        var builder = RestClient.builder().requestInterceptor((request, body, next) -> {
            assertThat(request.getURI()).isEqualTo(URI.create("https://rag.demo.invalid/internal/v1/retrieval/query"));
            assertThat(request.getHeaders().getFirst("X-Correlation-ID")).isEqualTo(correlation);
            var sent = JSON.readTree(body);
            assertThat(sent.get("query").asString()).isEqualTo(evidence.content());
            assertThat(sent.at("/authorization_scope/scope_revision").asString()).isEqualTo(scope.scopeRevision());
            var loopback = new HttpRequestWrapper(request) {
                @Override public URI getURI() {
                    return URI.create("http://127.0.0.1:" + Integer.getInteger("pipesor.fixture.rag.port")
                            + request.getURI().getPath());
                }
            };
            loopback.getHeaders().set("X-Pipesor-Caller", "recommendation_service");
            return next.execute(loopback, body);
        });
        var properties = new RagClientProperties(URI.create("https://rag.demo.invalid"),
                "api://11111111-1111-1111-1111-111111111111", Duration.ofSeconds(5));
        var rag = new RestRagKnowledgeRetriever(builder, properties, () -> "synthetic-rag-token");
        var service = new DiagnosticOrchestrator(rag, (observed, knowledge) -> {
            var line = observed.getFirst();
            if (!line.content().contains("Could not resolve host:") || knowledge.size() != 1) {
                throw new IllegalStateException("Synthetic inference fixture did not match");
            }
            return new Hypothesis("The checkout runner could not resolve the Git remote hostname.",
                    knowledge.getFirst().content(),
                    "Simulated inference and embeddings. The reason name resolution failed remains unknown.",
                    List.of(line.evidenceId()), List.of(knowledge.getFirst().chunkId()));
        });
        var output = service.diagnose(context, execution, incident, query, List.of(evidence));
        assertThat(output.evidence()).containsExactly(evidence);
        assertThat(output.sources()).containsExactly(
                new DiagnosticOrchestrator.Source(DOCUMENT, CHUNK, "fixture:runner-network-checks"));
        assertThat(output.correlationId()).isEqualTo(correlation);
        assertThat(output.hypothesis().probableCause()).contains("could not resolve");

        var noMatch = new RetrievalResult.NoMatch(correlation);
        assertRejected(new DiagnosticOrchestrator(ignored -> noMatch, (signalsIn, knowledge) -> null),
                context, execution, incident, query, evidence);
        assertRejected(new DiagnosticOrchestrator(ignored -> noMatch, (signalsIn, knowledge) ->
                        new Hypothesis("Cause", "Check", "Synthetic", List.of(CHUNK), List.of())),
                context, execution, incident, query, evidence);
        assertRejected(new DiagnosticOrchestrator(ignored -> noMatch, (signalsIn, knowledge) ->
                        new Hypothesis("Cause", "Check", "Synthetic", List.of(expectedEvidence), List.of(CHUNK))),
                context, execution, incident, query, evidence);
        var ragFailure = new DiagnosticOrchestrator(ignored -> { throw new RagUnavailableException("fixture", null); },
                (signalsIn, knowledge) -> { throw new AssertionError("Inference ran after failed retrieval"); });
        assertThatThrownBy(() -> ragFailure.diagnose(context, execution, incident, query, List.of(evidence)))
                .isInstanceOf(RagUnavailableException.class);
        var inferenceFailure = new DiagnosticOrchestrator(ignored -> noMatch,
                (signalsIn, knowledge) -> { throw new IllegalStateException("Synthetic inference failure"); });
        assertThatThrownBy(() -> inferenceFailure.diagnose(context, execution, incident, query, List.of(evidence)))
                .hasMessage("Synthetic inference failure");

        System.out.println("PIPESOR_DIAGNOSIS " + JSON.writeValueAsString(output));
        return output;
    }

    private static void assertRejected(DiagnosticOrchestrator service, UUID context, UUID execution, UUID incident,
            RetrievalQuery query, Evidence evidence) {
        assertThatThrownBy(() -> service.diagnose(context, execution, incident, query, List.of(evidence)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Invalid diagnostic hypothesis or citations");
    }
}
