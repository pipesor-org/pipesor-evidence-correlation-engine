package org.pipesor.correlation.configuration;

import org.pipesor.correlation.context.interfaces.rest.ProcessedEvidenceController;
import org.pipesor.correlation.shared.security.PlatformAuthorities;
import org.pipesor.correlation.shared.security.PlatformSecurityConfiguration.PlatformRoutes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

/** Route catalog of platform identity contract v1 for this workload: only NLP may deliver processed evidence. */
@Configuration(proxyBeanMethods = false)
public class CorrelationRoutes {
    public static final String NLP_ROLE = "Nlp.ProcessedEvidence.Send";

    @Bean
    PlatformRoutes processedEvidenceRoutes() {
        return routes -> routes.requestMatchers(HttpMethod.POST, ProcessedEvidenceController.PATH)
                .access(PlatformAuthorities.workload("nlp", NLP_ROLE));
    }
}
