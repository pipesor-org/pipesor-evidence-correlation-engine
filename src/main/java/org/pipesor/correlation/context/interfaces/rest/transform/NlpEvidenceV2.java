package org.pipesor.correlation.context.interfaces.rest.transform;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.pipesor.correlation.context.domain.model.valueobjects.EvidenceSignal;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import tools.jackson.databind.JsonNode;

/** Validated protected input. Shape checks do not establish scope authority or owner existence. */
public record NlpEvidenceV2(
        UUID messageId, String correlationId, String source, String occurredAt, String sourceUpdatedAt,
        CaptureContext captureContext, Pipeline pipeline, Execution execution, String commitSha,
        String service, String environment, UUID pipelineId, UUID executionId, UUID incidentId,
        UUID evidenceId, String job, Long sequence, EvidenceSignal signal, String template, String fingerprint) {

    public record CaptureContext(UUID tenantId, String githubAccountId, String githubInstallationId,
            String scopeRevision) {
        @Override public String toString() { return "CaptureContext[redacted]"; }
    }

    public record Pipeline(String githubRepositoryId, String githubWorkflowId) {
        @Override public String toString() { return "Pipeline[redacted]"; }
    }

    public record Execution(String githubRunId, int runAttempt, String status, String conclusion) {
        @Override public String toString() { return "Execution[redacted]"; }
    }

    public static NlpEvidenceV2 decode(byte[] body, int maxBytes, String idempotencyKey, String correlationId) {
        try {
            require(body != null && maxBytes > 0 && body.length <= maxBytes);
            String input = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString();
            require(!input.startsWith("\ufeff"));
            JsonNode root = object(CanonicalJson.mapper().readTree(input));
            String version = text(root, "contract_version");
            require("2.0".equals(version) || "2.1".equals(version));
            UUID messageId = uuid(root, "message_id");
            String trace = safeId(root, "correlation_id");
            require(messageId.toString().equals(idempotencyKey) && trace.equals(correlationId));
            String source = text(root, "source");
            require("github_actions".equals(source));
            String occurredAt = timestamp(root, "occurred_at");
            String sourceUpdatedAt = timestamp(root, "source_updated_at");
            JsonNode capture = object(root.get("capture_context"));
            var captureContext = new CaptureContext(uuid(capture, "tenant_id"), externalId(capture, "github_account_id"),
                    externalId(capture, "github_installation_id"), safeId(capture, "scope_revision"));
            JsonNode pipelineNode = object(root.get("pipeline"));
            var pipeline = new Pipeline(externalId(pipelineNode, "github_repository_id"),
                    externalId(pipelineNode, "github_workflow_id"));
            JsonNode executionNode = object(root.get("execution"));
            JsonNode attemptNode = executionNode.get("run_attempt");
            require(attemptNode != null && attemptNode.isIntegralNumber() && attemptNode.canConvertToInt());
            int attempt = attemptNode.intValue();
            require(attempt > 0);
            String status = text(executionNode, "status");
            String conclusion = text(executionNode, "conclusion");
            require("completed".equals(status) && ("failure".equals(conclusion) || "timed_out".equals(conclusion)));
            var execution = new Execution(externalId(executionNode, "github_run_id"), attempt, status, conclusion);
            String sha = text(object(root.get("commit")), "sha");
            require(sha.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"));
            String service = nullableText(root, "service", true);
            String environment = nullableText(root, "environment", true);
            UUID pipelineId = uuid(root, "pipeline_id");
            UUID executionId = uuid(root, "execution_id");
            UUID incidentId = uuid(root, "incident_id");
            UUID evidenceId = uuid(root, "evidence_id");
            String job = nullableText(root, "job", false);
            String locator = null;
            if ("2.1".equals(version)) {
                locator = text(root, "source_locator");
                require(job != null && job.matches("[1-9][0-9]*") && locator.matches(
                        "v2:job:" + job + ":member:[0-9]{1,9}_verify\\.txt:lines:([1-9][0-9]{0,8})-\\1:sha256:[0-9a-f]{64}"));
            } else { require(!root.has("source_locator")); }
            Long sequence = sequence(root);
            var signal = new EvidenceSignal(text(root, "stage"), text(root, "sanitized_summary"),
                    text(root, "sanitized_content"));
            String template = text(root, "template");

            var significant = new TreeMap<String, Object>();
            significant.put("capture_context", sorted(Map.of("tenant_id", captureContext.tenantId().toString(),
                    "github_account_id", captureContext.githubAccountId(),
                    "github_installation_id", captureContext.githubInstallationId(),
                    "scope_revision", captureContext.scopeRevision())));
            significant.put("pipeline", sorted(Map.of("github_repository_id", pipeline.githubRepositoryId(),
                    "github_workflow_id", pipeline.githubWorkflowId())));
            significant.put("execution", sorted(Map.of("github_run_id", execution.githubRunId(),
                    "run_attempt", execution.runAttempt(), "status", execution.status(), "conclusion", execution.conclusion())));
            significant.put("commit", sorted(Map.of("sha", sha)));
            significant.put("contract_version", version);
            if (locator != null) { significant.put("source_locator", locator); }
            significant.put("message_id", messageId.toString());
            significant.put("correlation_id", trace);
            significant.put("source", source);
            significant.put("occurred_at", occurredAt);
            significant.put("source_updated_at", sourceUpdatedAt);
            significant.put("service", service);
            significant.put("environment", environment);
            significant.put("pipeline_id", pipelineId.toString());
            significant.put("execution_id", executionId.toString());
            significant.put("incident_id", incidentId.toString());
            significant.put("evidence_id", evidenceId.toString());
            significant.put("stage", signal.stage());
            significant.put("job", job);
            significant.put("sequence", sequence);
            significant.put("sanitized_summary", signal.summary());
            significant.put("sanitized_content", signal.content());
            String fingerprint = CanonicalJson.fingerprint(CanonicalJson.mapper().writeValueAsBytes(significant));
            return new NlpEvidenceV2(messageId, trace, source, occurredAt, sourceUpdatedAt, captureContext,
                    pipeline, execution, sha, service, environment, pipelineId, executionId, incidentId,
                    evidenceId, job, sequence, signal, template, fingerprint);
        } catch (RuntimeException | CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid protected NLP evidence");
        }
    }

    private static Map<String, Object> sorted(Map<String, Object> values) { return new TreeMap<>(values); }

    private static JsonNode object(JsonNode value) {
        require(value != null && value.isObject());
        return value;
    }

    private static String text(JsonNode object, String field) {
        JsonNode node = object.get(field);
        require(node != null && node.isString());
        String value = node.asString();
        require(!value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85));
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            require(current != 0 && !Character.isLowSurrogate(current));
            if (Character.isHighSurrogate(current)) {
                require(++i < value.length() && Character.isLowSurrogate(value.charAt(i)));
            }
        }
        return value;
    }

    private static String nullableText(JsonNode object, String field, boolean required) {
        JsonNode node = object.get(field);
        require(node != null || !required);
        return node == null || node.isNull() ? null : text(object, field);
    }

    private static String safeId(JsonNode object, String field) {
        String value = text(object, field);
        require(value.matches("[A-Za-z0-9._:-]{1,128}"));
        return value;
    }

    private static String externalId(JsonNode object, String field) {
        String value = text(object, field);
        require(value.matches("[1-9][0-9]*"));
        return value;
    }

    private static UUID uuid(JsonNode object, String field) {
        String value = text(object, field);
        UUID parsed = UUID.fromString(value);
        require(parsed.toString().equals(value) && !parsed.equals(new UUID(0, 0)));
        return parsed;
    }

    private static String timestamp(JsonNode object, String field) {
        String value = text(object, field);
        require(value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,9})?(?:Z|[+]00:00)"));
        require(OffsetDateTime.parse(value).getOffset().equals(ZoneOffset.UTC));
        return value;
    }

    private static Long sequence(JsonNode object) {
        JsonNode node = object.get("sequence");
        require(node != null);
        if (node.isNull()) return null;
        require(node.isIntegralNumber() && node.canConvertToLong());
        long value = node.longValue();
        require(value >= 0);
        return value;
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalArgumentException("Invalid protected NLP evidence");
    }

    @Override public String toString() { return "NlpEvidenceV2[redacted]"; }
}
