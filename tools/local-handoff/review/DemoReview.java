package org.pipesor.correlation.context.infrastructure.delivery;

import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Diagnosis;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Evidence;
import com.pipesor.recommendation.diagnosis.review.PersistedDiagnosticWorkflow;
import com.pipesor.recommendation.diagnosis.review.ReviewEndpoint;
import com.pipesor.recommendation.diagnosis.review.ReviewStore;
import com.pipesor.recommendation.retrieval.domain.AuthorizationScope;
import com.pipesor.recommendation.retrieval.domain.RetrievalQuery;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

final class DemoReview {
    private static final AtomicReference<AuthorizationScope> ALLOWED_SCOPE = new AtomicReference<>();

    static RouterFunction<ServerResponse> routes(DataSource source, String token) {
        return ReviewEndpoint.routes(new ReviewStore(source), new ReviewEndpoint.Authority() {
            public String authenticate(String bearer) {
                if ("fixture-other-scope".equals(bearer)) return "other-local-01";
                return MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),
                        bearer.getBytes(StandardCharsets.UTF_8)) ? "reviewer-local-01" : null;
            }

            public boolean allows(String reviewer, AuthorizationScope scope, boolean write) {
                return "reviewer-local-01".equals(reviewer) && scope.equals(ALLOWED_SCOPE.get());
            }
        });
    }

    static Diagnosis diagnose(DataSource source, DiagnosticOrchestrator service, UUID context, UUID execution,
                              UUID incident, RetrievalQuery query, List<Evidence> evidence, Consumer<UUID> publishId)
            throws SQLException {
        var workflow = new PersistedDiagnosticWorkflow(service, new ReviewStore(source));
        var admitted = workflow.diagnose(context, execution, incident, query, evidence);
        var replayOnly = new PersistedDiagnosticWorkflow(new DiagnosticOrchestrator(
                ignored -> { throw new AssertionError("Admission replay called retrieval"); },
                (signals, matches) -> { throw new AssertionError("Admission replay called inference"); }), new ReviewStore(source));
        assertThat(replayOnly.diagnose(context, execution, incident, query, evidence)).isEqualTo(admitted);
        ALLOWED_SCOPE.set(query.authorizationScope());
        publishId.accept(admitted.diagnosticId());
        System.out.println("PIPESOR_DIAGNOSIS_ADMITTED " + admitted.diagnosticId() + " version=" + admitted.version());
        return admitted.output();
    }
}
