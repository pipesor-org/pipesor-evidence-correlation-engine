package org.pipesor.correlation.shared.infrastructure.json;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Shared UTF-8 codec. Each exchange selects and orders its own significant fields. */
public final class CanonicalJson {
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .disable(JsonWriteFeature.ESCAPE_NON_ASCII, JsonWriteFeature.ESCAPE_FORWARD_SLASHES,
                    JsonWriteFeature.WRITE_HEX_UPPER_CASE)
            .enable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
            .build();

    private CanonicalJson() { }

    public static JsonMapper mapper() {
        return JSON;
    }

    public static String fingerprint(byte[] canonical) {
        try {
            return "v1:sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required digest unavailable");
        }
    }
}
