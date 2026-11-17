package org.pipesor.correlation.context.infrastructure.delivery;

import org.pipesor.correlation.context.application.ports.RecommendationDelivery.Receipt;
import org.pipesor.correlation.context.application.ports.RecommendationDelivery.Status;
import org.pipesor.correlation.context.application.queries.CorrelatedRevision;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Strict approved typed representation; never accepts a raw request as source input. */
final class RecommendationJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .disable(JsonWriteFeature.ESCAPE_NON_ASCII, JsonWriteFeature.ESCAPE_FORWARD_SLASHES,
                    JsonWriteFeature.WRITE_HEX_UPPER_CASE)
            .enable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
            .build();
    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder().appendInstant(6).toFormatter();

    private RecommendationJson() { }

    static byte[] encode(CorrelatedRevision revision, int maxBytes) {
        if (maxBytes < 1) throw invalid();
        try {
            if (revision == null || revision.contextId() == null || revision.groupId() == null
                    || revision.incidentId() == null || revision.executionId() == null || revision.scope() == null
                    || revision.createdAt() == null || revision.createdAt().getNano() % 1000 != 0
                    || revision.revision() != revision.signals().size() || revision.signals().isEmpty()) throw invalid();
            safeId(revision.correlationId());
            if (revision.summary() == null) throw invalid();
            long minimumBytes = revision.summary().length();
            if (minimumBytes > maxBytes || revision.signals().size() > maxBytes) throw new RequestTooLarge();
            text(revision.summary());
            String created = TIME.format(revision.createdAt());
            if (created.length() != 27) throw invalid();
            var scope = revision.scope();
            var scopeJson = object();
            scopeJson.put("github_installation_id", scope.installationId());
            scopeJson.put("github_repository_id", scope.repositoryId());
            scopeJson.put("pipeline_id", scope.pipelineId() == null ? null : scope.pipelineId().toString());
            scopeJson.put("scope_revision", scope.revision());
            scopeJson.put("tenant_id", scope.tenantId().toString());
            var ids = new HashSet<UUID>();
            var origins = new HashSet<UUID>();
            var signals = new ArrayList<Map<String, Object>>();
            for (var introduced : revision.signals()) {
                if (introduced == null || introduced.evidenceId() == null || introduced.originContextId() == null
                        || introduced.signal() == null || !ids.add(introduced.evidenceId())
                        || !origins.add(introduced.originContextId())) throw invalid();
                safeId(introduced.correlationId());
                minimumBytes += (long) introduced.signal().summary().length() + introduced.signal().content().length()
                        + introduced.correlationId().length() + introduced.signal().stage().length() + 2L * 36;
                if (minimumBytes > maxBytes) throw new RequestTooLarge();
                text(introduced.signal().summary());
                text(introduced.signal().content());
                var signal = object();
                signal.put("content", introduced.signal().content());
                signal.put("correlation_id", introduced.correlationId());
                signal.put("evidence_id", introduced.evidenceId().toString());
                signal.put("origin_context_id", introduced.originContextId().toString());
                signal.put("stage", introduced.signal().stage());
                signal.put("summary", introduced.signal().summary());
                signals.add(signal);
            }
            var last = revision.signals().getLast();
            if (!last.originContextId().equals(revision.contextId())
                    || !last.correlationId().equals(revision.correlationId())) throw invalid();
            var context = object();
            context.put("correlation_id", revision.correlationId());
            context.put("created_at", created);
            context.put("evidence_ids", ids.stream().map(UUID::toString).sorted().toList());
            context.put("execution_id", revision.executionId().toString());
            context.put("id", revision.contextId().toString());
            context.put("incident_id", revision.incidentId().toString());
            context.put("summary", revision.summary());
            var body = object();
            body.put("authorization_scope", scopeJson);
            body.put("context", context);
            body.put("contract_version", "1.0");
            body.put("operation_id", revision.contextId().toString());
            body.put("signals", signals);
            var output = new ByteArrayOutputStream() {
                @Override public synchronized void write(int value) {
                    if (count >= maxBytes) throw new RequestTooLarge();
                    super.write(value);
                }
                @Override public synchronized void write(byte[] bytes, int offset, int length) {
                    if (length > maxBytes - count) throw new RequestTooLarge();
                    super.write(bytes, offset, length);
                }
            };
            JSON.writeValue(output, body);
            return output.toByteArray();
        } catch (RequestTooLarge tooLarge) {
            throw tooLarge;
        } catch (RuntimeException failure) {
            if (hasSizeFailure(failure)) throw new RequestTooLarge();
            throw invalid();
        }
    }

    static String fingerprint(byte[] canonical) {
        try {
            return "v1:sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest unavailable");
        }
    }

    static Receipt receipt(byte[] bytes, UUID expected) {
        try {
            String input = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (input.startsWith("\ufeff")) throw invalid();
            JsonNode body = JSON.readTree(input);
            if (body == null || !body.isObject()
                    || !Set.copyOf(body.propertyNames()).equals(Set.of("contract_version", "operation_id", "context_id", "status"))) throw invalid();
            for (String key : body.propertyNames()) if (!body.get(key).isString()) throw invalid();
            if (!body.get("contract_version").asString().equals("1.0")
                    || !body.get("operation_id").asString().equals(expected.toString())
                    || !body.get("context_id").asString().equals(expected.toString())) throw invalid();
            Status status = switch (body.get("status").asString()) {
                case "accepted" -> Status.ACCEPTED;
                case "duplicate" -> Status.DUPLICATE;
                default -> throw invalid();
            };
            return new Receipt(expected, expected, status);
        } catch (Exception failure) {
            throw invalid();
        }
    }

    private static Map<String, Object> object() { return new TreeMap<>(); }

    private static boolean hasSizeFailure(Throwable failure) {
        for (Throwable item = failure; item != null; item = item.getCause()) {
            if (item instanceof RequestTooLarge) return true;
        }
        return false;
    }

    private static void safeId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) throw invalid();
    }

    private static void text(String value) {
        if (value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c)
                || Character.isSpaceChar(c) || c == 0x85)) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c)) throw invalid();
            if (Character.isHighSurrogate(c) && (++i >= value.length()
                    || !Character.isLowSurrogate(value.charAt(i)))) throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Valid frozen recommendation context is required");
    }

    static final class RequestTooLarge extends RuntimeException {
        RequestTooLarge() { super("Recommendation request exceeds supplied capacity"); }
    }
}
