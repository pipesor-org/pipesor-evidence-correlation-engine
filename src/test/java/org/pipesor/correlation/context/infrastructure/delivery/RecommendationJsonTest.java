package org.pipesor.correlation.context.infrastructure.delivery;

import org.junit.jupiter.api.Test;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery.Status;
import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecommendationJsonTest {
    @Test
    void canonicalBytesAndFingerprintsMatchBothPythonGoldenVectors() throws Exception {
        for (var fixture : DeliveryFixtures.vectors()) {
            byte[] bytes = RecommendationJson.encode(DeliveryFixtures.revision(fixture), 20_000);
            assertThat(bytes).isEqualTo(fixture.get("proposed_canonical_json").asString().getBytes(StandardCharsets.UTF_8));
            assertThat(RecommendationJson.fingerprint(bytes)).isEqualTo(fixture.get("proposed_fingerprint").asString());
            var id = DeliveryFixtures.revision(fixture).contextId();
            assertThat(RecommendationJson.receipt(DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("accepted_receipt")), id).status())
                    .isEqualTo(Status.ACCEPTED);
            assertThat(RecommendationJson.receipt(DeliveryFixtures.JSON.writeValueAsBytes(fixture.get("duplicate_receipt")), id).status())
                    .isEqualTo(Status.DUPLICATE);
        }
    }

    @Test
    void preservesNullPipelineAndUnicodeEscapingWithoutNormalizingProtectedText() throws Exception {
        var original = DeliveryFixtures.revision(DeliveryFixtures.vectors().get(0));
        var scope = new ScopeEnvelope(original.scope().tenantId(), "123", "456", null, "scope-12");
        String text = "Protected 🧪 / résumé \"value\" \\ \b\t\n\f\r\u0001\u000b\u001f";
        var revision = new org.pipesor.correlation.context.application.queries.CorrelatedRevision(
                original.contextId(), original.groupId(), 1, original.signals(), scope,
                original.incidentId(), original.executionId(), text, original.correlationId(), original.createdAt());
        var encoded = new String(RecommendationJson.encode(revision, 20_000), StandardCharsets.UTF_8);
        assertThat(encoded).contains("\"pipeline_id\":null", "🧪 / résumé", "\\b\\t\\n\\f\\r\\u0001\\u000b\\u001f");
        assertThat(encoded).doesNotContain("\\/", "\\u001F");
        assertThat(DeliveryFixtures.JSON.readTree(encoded).get("context").get("summary").asString()).isEqualTo(text);
    }

    @Test
    void preservesAggregateSourceLocatorForRecommendation() throws Exception {
        var original = DeliveryFixtures.revision(DeliveryFixtures.vectors().get(0));
        var signals = new ArrayList<>(original.signals());
        var introduced = signals.getFirst();
        var signal = introduced.signal();
        String locator = "v2:job:701:member:0_verify.txt:lines:2-2:sha256:" + "a".repeat(64);
        signals.set(0, new org.pipesor.correlation.context.application.queries.CorrelatedRevision.IntroducedSignal(
                introduced.evidenceId(), introduced.originContextId(), introduced.correlationId(),
                new EvidenceSignal(signal.stage(), signal.summary(), signal.content(), locator)));
        var revision = new org.pipesor.correlation.context.application.queries.CorrelatedRevision(
                original.contextId(), original.groupId(), original.revision(), signals, original.scope(),
                original.incidentId(), original.executionId(), original.summary(), original.correlationId(),
                original.createdAt());

        var encoded = DeliveryFixtures.JSON.readTree(RecommendationJson.encode(revision, 20_000));

        assertThat(encoded.get("signals").get(0).get("source_locator").asString()).isEqualTo(locator);
    }

    @Test
    void rejectsInvalidProjectionBeforeEncodingAndDoesNotExposeText() throws Exception {
        var good = DeliveryFixtures.revision(DeliveryFixtures.vectors().get(1));
        var badSignals = new ArrayList<>(good.signals());
        badSignals.set(0, badSignals.get(1));
        for (var bad : List.of(
                new org.pipesor.correlation.context.application.queries.CorrelatedRevision(good.contextId(), good.groupId(),
                        2, badSignals, good.scope(), good.incidentId(), good.executionId(), good.summary(), good.correlationId(), good.createdAt()),
                new org.pipesor.correlation.context.application.queries.CorrelatedRevision(good.contextId(), good.groupId(),
                        2, good.signals().reversed(), good.scope(), good.incidentId(), good.executionId(), good.summary(), good.correlationId(), good.createdAt()),
                new org.pipesor.correlation.context.application.queries.CorrelatedRevision(good.contextId(), good.groupId(),
                        2, good.signals(), good.scope(), good.incidentId(), good.executionId(), "private\u0000text", good.correlationId(), good.createdAt()),
                new org.pipesor.correlation.context.application.queries.CorrelatedRevision(good.contextId(), good.groupId(),
                        2, good.signals(), good.scope(), good.incidentId(), good.executionId(), "private\ud800", good.correlationId(), good.createdAt()),
                new org.pipesor.correlation.context.application.queries.CorrelatedRevision(good.contextId(), good.groupId(),
                        2, good.signals(), good.scope(), good.incidentId(), good.executionId(), good.summary(), good.correlationId(), Instant.parse("2026-11-17T10:00:00.000000001Z")))) {
            var failure = assertThrows(IllegalArgumentException.class, () -> RecommendationJson.encode(bad, 20_000));
            assertThat(failure.getMessage()).isEqualTo("Valid frozen recommendation context is required");
            assertThat(failure.getCause()).isNull();
        }
        assertThrows(RecommendationJson.RequestTooLarge.class, () -> RecommendationJson.encode(good, 20));
    }

    @Test
    void strictReceiptRejectsAmbiguousOrCrossedRepresentations() throws Exception {
        var fixture = DeliveryFixtures.vectors().get(0);
        var id = DeliveryFixtures.revision(fixture).contextId();
        String valid = DeliveryFixtures.JSON.writeValueAsString(fixture.get("accepted_receipt"));
        for (String bad : List.of("", "null", "[]", "<html>private content</html>", valid + " {}", "\ufeff" + valid,
                valid.replace("\"accepted\"", "\"diagnosed\""), valid.replace("\"1.0\"", "\"2.0\""),
                valid.replace(id.toString(), "00000000-0000-0000-0000-000000000999"),
                valid.replace("\"accepted\"", "\"accepted\",\"status\":\"duplicate\""),
                valid.replace("\"accepted\"", "\"accepted\",\"private_body\":\"secret\""))) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> RecommendationJson.receipt(bad.getBytes(StandardCharsets.UTF_8), id));
            assertThat(failure.getMessage()).isEqualTo("Valid frozen recommendation context is required");
            assertThat(failure.getCause()).isNull();
        }
        assertThrows(IllegalArgumentException.class, () -> RecommendationJson.receipt(new byte[]{(byte) 0xc3, 0x28}, id));
    }
}
