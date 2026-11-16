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
}
