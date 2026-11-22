package org.pipesor.correlation.context.domain.model.valueobjects;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Aggregate source locator grammar of protected workflow evidence 2.2 (a superset of 2.1). */
public final class SourceLocator {
    private static final Pattern SHAPE = Pattern.compile(
            "v2:job:([1-9][0-9]{0,19}):member:((?:[A-Za-z0-9._~-]|%[0-9A-F]{2}){1,765}):lines:([1-9][0-9]{0,8})-([1-9][0-9]{0,8}):sha256:[0-9a-f]{64}");
    private static final int MAX_LINES = 200;

    private SourceLocator() { }

    /** True when the locator is valid for its own job id; callers that know the authenticated job use {@link #valid}. */
    public static boolean validShape(String locator) {
        if (locator == null) return false;
        var match = SHAPE.matcher(locator);
        return match.matches() && valid(locator, match.group(1));
    }

    /** True when the locator is valid and names the given authenticated job id. */
    public static boolean valid(String locator, String job) {
        if (locator == null || job == null) return false;
        var match = SHAPE.matcher(locator);
        if (!match.matches() || !match.group(1).equals(job)) return false;
        long first = Long.parseLong(match.group(3));
        long last = Long.parseLong(match.group(4));
        if (last < first || last - first >= MAX_LINES) return false;
        String member = decode(match.group(2));
        return member != null && validMember(member);
    }

    private static boolean validMember(String member) {
        int bytes = member.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 1 || bytes > 255 || member.startsWith("/") || member.contains("\\")) return false;
        if (member.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) return false;
        String[] segments = member.split("/", -1);
        if (segments.length > 2) return false;
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals("..") || segment.equals(".")) return false;
        }
        return true;
    }

    private static String decode(String encoded) {
        var bytes = new ByteArrayOutputStream();
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '%') {
                bytes.write(Integer.parseInt(encoded.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.write(c);
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException invalid) {
            return null;
        }
    }
}
