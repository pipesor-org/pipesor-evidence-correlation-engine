package org.pipesor.correlation.context.domain.model.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvidenceSignalTest {
    @Test
    void preservesValuesWithoutExposingProtectedTextInToString() {
        for (var stage : List.of("BUILD", "TESTING", "QUALITY_GATE", "DEPLOYMENT", "CONTAINER", "INFRASTRUCTURE")) {
            var signal = new EvidenceSignal(stage, "Protected résumé α", "Protected content 🧪");
            assertThat(signal).isEqualTo(new EvidenceSignal(stage, signal.summary(), signal.content()));
            assertThat(signal.toString()).isEqualTo("EvidenceSignal[redacted]");
        }
    }

    @Test
    void rejectsInvalidShapesWithFixedErrorsWithoutClaimingSanitization() {
        for (var fields : List.of(new String[]{"sensitive-unknown-stage", "safe", "safe"},
                new String[]{null, "safe", "safe"}, new String[]{"BUILD", null, "safe"},
                new String[]{"BUILD", "safe", null}, new String[]{"BUILD", "\n\t ", "safe"},
                new String[]{"BUILD", "safe", "\n\t "}, new String[]{"BUILD", "", "safe"},
                new String[]{"BUILD", "safe", ""})) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new EvidenceSignal(fields[0], fields[1], fields[2]));
            assertThat(failure.getMessage()).isEqualTo("Valid protected evidence signal is required");
            assertThat(failure.getCause()).isNull();
        }
    }

    @Test
    void acceptsProtectedEvidence22LocatorsAndRejectsUnsafeOnes() {
        String digest = "28a1babbb7adec8029801c6eaefd85584e1f485b7f831e8926192dfd2bdc0613";
        // The authentic run 43055757606 produced a 31-line aggregate range under protected evidence 2.2.
        var signal = new EvidenceSignal("INFRASTRUCTURE", "safe", "safe",
                "v2:job:130644639649:member:0_verify.txt:lines:1-31:sha256:" + digest);
        assertThat(signal.sourceLocator()).endsWith(digest);
        new EvidenceSignal("TESTING", "safe", "safe", "v2:job:7:member:build%2F3_Run%20mvn%20test.txt:lines:10-40:sha256:" + digest);
        for (var locator : List.of("v2:job:7:member:..%2Fa.txt:lines:1-1:sha256:" + digest,
                "v2:job:7:member:a.txt:lines:9-1:sha256:" + digest, "v1:job:7:step:1:index:0")) {
            assertThrows(IllegalArgumentException.class, () -> new EvidenceSignal("BUILD", "safe", "safe", locator));
        }
    }
}
