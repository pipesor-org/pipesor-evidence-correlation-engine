package org.pipesor.correlation.context.infrastructure.delivery;

import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Diagnosis;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Evidence;
import com.pipesor.recommendation.retrieval.domain.RetrievalQuery;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

final class DemoReview {
    static RouterFunction<ServerResponse> routes(DataSource source, String token) {
        return request -> Optional.empty();
    }

    static Diagnosis diagnose(DataSource source, DiagnosticOrchestrator service, UUID context, UUID execution,
                              UUID incident, RetrievalQuery query, List<Evidence> evidence, Consumer<UUID> publishId) {
        return service.diagnose(context, execution, incident, query, evidence);
    }
}
