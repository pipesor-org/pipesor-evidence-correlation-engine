package org.pipesor.correlation.configuration;

import org.pipesor.correlation.context.infrastructure.delivery.HttpRecommendationDelivery;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence;
import org.pipesor.correlation.context.infrastructure.validation.HttpOwnerReferences;
import org.pipesor.correlation.context.interfaces.rest.ProcessedEvidenceController;
import org.pipesor.correlation.shared.security.PlatformSecurityProperties;
import org.pipesor.correlation.shared.security.TransportPolicy;
import org.pipesor.correlation.shared.security.WorkloadTokens;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

/** Composition of the synchronous NLP → Correlation → Recommendation flow (ADR-015). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorrelationProperties.class)
public class CorrelationRuntimeConfiguration {
    static final String PIPELINE_ROLE = "Correlation.OperationalReferences.Check";
    static final String LOG_EVENT_ROLE = "Correlation.EvidenceMembership.Check";
    static final String RECOMMENDATION_ROLE = "Correlation.Contexts.Deliver";

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean(destroyMethod = "close")
    HttpOwnerReferences ownerReferences(CorrelationProperties settings, WorkloadTokens tokens, TransportPolicy transport) {
        var owners = settings.correlation().owners();
        return new HttpOwnerReferences(settings.endpoints().pipeline(), settings.endpoints().logevent(), (owner, remaining) -> {
            var token = owner == HttpOwnerReferences.Owner.PIPELINE
                    ? tokens.issue("pipeline", PIPELINE_ROLE) : tokens.issue("log-event", LOG_EVENT_ROLE);
            return new HttpOwnerReferences.Token(token.value(), token.expiresAt());
        }, owners.timeout(), owners.maxRequestBytes(), owners.maxResponseBytes(), transport);
    }

    @Bean(destroyMethod = "close")
    HttpRecommendationDelivery recommendationDelivery(CorrelationProperties settings, TransportPolicy transport) {
        var peer = settings.correlation().recommendation();
        return new HttpRecommendationDelivery(settings.endpoints().recommendation(), peer.maxRequestBytes(),
                peer.maxResponseBytes(), peer.timeout(), transport);
    }

    @Bean
    ReceiveProcessedEvidence receiveProcessedEvidence(DataSource dataSource, Clock clock, HttpOwnerReferences owners,
            HttpRecommendationDelivery delivery, WorkloadTokens tokens, CorrelationProperties settings) {
        return new ReceiveProcessedEvidence(dataSource, clock, owners::verify,
                settings.correlation().recommendation().maxRequestBytes(), delivery::deliver, remaining -> {
                    var token = tokens.issue("recommendation", RECOMMENDATION_ROLE);
                    return new HttpOwnerReferences.Token(token.value(), token.expiresAt());
                });
    }

    @Bean
    ProcessedEvidenceController.Processor processedEvidenceProcessor(ReceiveProcessedEvidence receiver) {
        return receiver::receive;
    }

    @Bean
    ProcessedEvidenceController.Limits processedEvidenceLimits(CorrelationProperties settings,
            PlatformSecurityProperties platform) {
        var intake = settings.correlation().intake();
        return new ProcessedEvidenceController.Limits(platform.tenantId(), intake.requestTimeout(), intake.maxRequestBytes(),
                intake.maxHeaderChars(), intake.maxInFlight(), intake.resourceDeniedStatus(), intake.capacityExceededStatus());
    }
}
