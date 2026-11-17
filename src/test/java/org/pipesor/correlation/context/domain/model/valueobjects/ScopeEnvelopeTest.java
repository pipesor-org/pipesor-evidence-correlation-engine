package org.pipesor.correlation.context.domain.model.valueobjects;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScopeEnvelopeTest {
    @Test
    void preservesHistoricalValuesAndSeparatesResourceIdentityFromRevision() {
        var tenant = UUID.randomUUID();
        var scope = new ScopeEnvelope(tenant, "123", "456", null, "scope-v12");
        assertThat(scope.sameResource(new ScopeEnvelope(tenant, "123", "456", null, "scope-v13"))).isTrue();
        assertThat(scope.sameResource(new ScopeEnvelope(tenant, "123", "456", UUID.randomUUID(), "scope-v12"))).isFalse();
        assertThat(scope.toString()).isEqualTo("ScopeEnvelope[redacted]");
        assertThat(scope).isNotEqualTo(new ScopeEnvelope(tenant, "123", "456", null, "scope-v13"));
    }

    @Test
    void rejectsMalformedFieldsUsingFixedMessagesWithoutClaimingAuthority() {
        for (var invalid : List.of("", "0", "01", "-1", " 123", "1".repeat(33), "sensitive")) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> new ScopeEnvelope(UUID.randomUUID(), invalid, "456", null, "scope-v12"));
            assertThat(error.getMessage()).isEqualTo("Valid historical scope envelope is required");
            assertThrows(IllegalArgumentException.class,
                    () -> new ScopeEnvelope(UUID.randomUUID(), "123", invalid, null, "scope-v12"));
        }
        for (var invalid : List.of("", "scope revision", "x".repeat(129), "scope-α")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ScopeEnvelope(UUID.randomUUID(), "123", "456", null, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new ScopeEnvelope(null, "123", "456", null, "v1"));
        assertThrows(IllegalArgumentException.class, () -> new ScopeEnvelope(UUID.randomUUID(), null, "456", null, "v1"));
        assertThrows(IllegalArgumentException.class, () -> new ScopeEnvelope(UUID.randomUUID(), "123", null, null, "v1"));
        assertThrows(IllegalArgumentException.class, () -> new ScopeEnvelope(UUID.randomUUID(), "123", "456", null, null));
    }
}
