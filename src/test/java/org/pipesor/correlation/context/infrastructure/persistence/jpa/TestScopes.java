package org.pipesor.correlation.context.infrastructure.persistence.jpa;

import org.pipesor.correlation.context.domain.model.valueobjects.ScopeEnvelope;
import java.util.UUID;

final class TestScopes {
    static final ScopeEnvelope SCOPE = new ScopeEnvelope(UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "123", "456", null, "scope-v12");
    static final String SQL = "'11111111-1111-1111-1111-111111111111','123','456',NULL,'scope-v12'";
    private TestScopes() { }
}
