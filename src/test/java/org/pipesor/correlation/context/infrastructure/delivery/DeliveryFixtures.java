package org.pipesor.correlation.context.infrastructure.delivery;

import org.pipesor.correlation.context.application.queries.CorrelatedRevision;
import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

final class DeliveryFixtures {
    static final JsonMapper JSON = JsonMapper.builder().build();

    static JsonNode vectors() throws IOException {
        return JSON.readTree(Files.readAllBytes(Path.of("docs/contracts/correlation-recommendation-v1.examples.json"))).get("fixtures");
    }

    static CorrelatedRevision revision(JsonNode fixture) {
        var body = fixture.get("submission");
        var context = body.get("context");
        var scope = body.get("authorization_scope");
        var signals = new ArrayList<CorrelatedRevision.IntroducedSignal>();
        for (var signal : body.get("signals")) {
            signals.add(new CorrelatedRevision.IntroducedSignal(id(signal, "evidence_id"), id(signal, "origin_context_id"),
                    signal.get("correlation_id").asString(), new EvidenceSignal(signal.get("stage").asString(),
                    signal.get("summary").asString(), signal.get("content").asString())));
        }
        return new CorrelatedRevision(id(context, "id"), UUID.fromString("00000000-0000-0000-0000-000000000801"),
                signals.size(), signals, new ScopeEnvelope(id(scope, "tenant_id"),
                scope.get("github_installation_id").asString(), scope.get("github_repository_id").asString(),
                scope.get("pipeline_id").isNull() ? null : id(scope, "pipeline_id"), scope.get("scope_revision").asString()),
                id(context, "incident_id"), id(context, "execution_id"), context.get("summary").asString(),
                context.get("correlation_id").asString(), Instant.parse(context.get("created_at").asString()));
    }

    static UUID id(JsonNode node, String field) { return UUID.fromString(node.get(field).asString()); }

    private DeliveryFixtures() { }
}
