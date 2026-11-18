package org.pipesor.correlation.context.interfaces.rest.transform;

import org.junit.jupiter.api.Test;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NlpEvidenceV2Test {
    private static final String MESSAGE = "cf6b69cf-a28d-49f3-bceb-000000000001";
    private static final String TRACE = "ci-investigation-204";
    private static final int LIMIT = 4096;
    private static final String FIXTURE = "docs/contracts/nlp-processed-evidence-v2.examples.json";

    @Test
    void matchesTheTwoProducerVectorsAndPreservesExactSourceFields() throws Exception {
        var present = decode(fixture("with_job"));
        var absent = decode(fixture("without_job"));
        assertThat(present.fingerprint()).isEqualTo("v1:sha256:245bf1a099fc94634b2a5e461d8e8dc089172fbbc8517f58e803dd8d1ae7f953");
        assertThat(absent.fingerprint()).isEqualTo("v1:sha256:20c48e47e7da3ab53f3431cb43a03d8dba3dd5e1de21a497866d470f41e095a2");
        assertThat(present.signal().summary()).isEqualTo("Failure [redacted]");
        assertThat(present.signal().content()).isEqualTo("Trace [redacted]");
        assertThat(present.template()).isEqualTo("Synthetic template");
        assertThat(present.occurredAt()).isEqualTo("2026-11-08T09:20:31.123456789Z");
        assertThat(present.sourceUpdatedAt()).isEqualTo("2026-11-08T09:20:40.987654321Z");
        assertThat(present.pipeline().githubRepositoryId()).isEqualTo("92233720368547758081234567");
        assertThat(present.execution().githubRunId()).isEqualTo("999999999999999999999999999");
        assertThat(present.sequence()).isEqualTo(4294967303L);
        assertThat(present.job()).isEqualTo("job-[redacted]");
        assertThat(absent.job()).isNull();
        assertThat(present.toString()).doesNotContain("Synthetic template", "Trace [redacted]", TRACE);
    }

    @Test
    void comparesKnownProtectedValuesWithoutDependingOnJsonOrderOrMutableEnrichment() throws Exception {
        var initial = fixture("without_job");
        var accepted = decode(initial);
        var reordered = CanonicalJson.mapper().createObjectNode();
        initial.propertyNames().stream().sorted().forEach(field -> reordered.set(field, initial.get(field)));
        reordered.putNull("job").put("template", "Updated enrichment").put("new_optional_field", "ignored");
        ((ObjectNode) reordered.get("capture_context")).put("future_optional_field", true);
        byte[] encoded = CanonicalJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(reordered);
        var replay = NlpEvidenceV2.decode(encoded, LIMIT, MESSAGE, TRACE);
        assertThat(replay.fingerprint()).isEqualTo(accepted.fingerprint());
        assertThat(replay.template()).isEqualTo("Updated enrichment");

        var timestampChange = initial.deepCopy().put("occurred_at", "2026-11-08T09:20:31.123456790Z");
        assertThat(decode(timestampChange).fingerprint()).isNotEqualTo(accepted.fingerprint());
        var exactUtcOffset = initial.deepCopy().put("occurred_at", "2026-11-08T09:20:31.123456789+00:00");
        assertThat(decode(exactUtcOffset).fingerprint()).isNotEqualTo(accepted.fingerprint());
        var attemptChange = initial.deepCopy();
        ((ObjectNode) attemptChange.get("execution")).put("run_attempt", 2);
        assertThat(decode(attemptChange).fingerprint()).isNotEqualTo(accepted.fingerprint());
        var scopeChange = initial.deepCopy();
        ((ObjectNode) scopeChange.get("capture_context")).put("scope_revision", "scope-rev-13");
        assertThat(decode(scopeChange).fingerprint()).isNotEqualTo(accepted.fingerprint());
        var protectedTextChange = initial.deepCopy().put("sanitized_content", "Trace changed");
        assertThat(decode(protectedTextChange).fingerprint()).isNotEqualTo(accepted.fingerprint());
    }

    @Test
    void rejectsUnusableInputsWithoutLeakingTheirValues() throws Exception {
        var node = fixture("with_job");
        byte[] body = CanonicalJson.mapper().writeValueAsBytes(node);
        assertRejected(body, body.length - 1, MESSAGE, TRACE);
        assertRejected(body, LIMIT, "different", TRACE);
        assertRejected(body, LIMIT, MESSAGE, "different");
        var json = new String(body, StandardCharsets.UTF_8);
        for (var input : List.of(json + " {}", "\ufeff" + json,
                json.replace("\"scope_revision\":", "\"scope_revision\":\"private-value\",\"scope_revision\":"),
                json.replace("\"2.0\"", "\"3.0\""), json.replace("4294967303", "9223372036854775808"),
                json.replace("\"run_attempt\":1", "\"run_attempt\":1.0"),
                json.replace("\"service\":null,", ""),
                json.replace("123456789Z", "123456789+01:00"),
                json.replace("Synthetic template", "\\" + "uD800"))) {
            assertRejected(input.getBytes(StandardCharsets.UTF_8), LIMIT, MESSAGE, TRACE);
        }
        assertRejected(new byte[]{(byte) 0xc3, 0x28}, LIMIT, MESSAGE, TRACE);
    }

    private static ObjectNode fixture(String variant) throws Exception {
        return (ObjectNode) CanonicalJson.mapper().readTree(Files.readAllBytes(Path.of(FIXTURE))).get(variant);
    }

    private static NlpEvidenceV2 decode(ObjectNode body) {
        return NlpEvidenceV2.decode(CanonicalJson.mapper().writeValueAsBytes(body), LIMIT, MESSAGE, TRACE);
    }

    private static void assertRejected(byte[] body, int max, String key, String trace) {
        var failure = assertThrows(IllegalArgumentException.class, () -> NlpEvidenceV2.decode(body, max, key, trace));
        assertThat(failure.getCause()).isNull();
        assertThat(failure.getMessage()).doesNotContain("private-value", MESSAGE, "Trace [redacted]");
    }
}
