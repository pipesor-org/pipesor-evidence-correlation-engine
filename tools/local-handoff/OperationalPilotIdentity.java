package org.pipesor.pipeline;

import org.pipesor.correlation.context.infrastructure.validation.OwnerChain;
import org.pipesor.pipeline.configuration.ApiSecurityConfiguration;
import org.pipesor.pipeline.catalog.application.ports.GitHubConnector.RepositoryAccess;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;

@TestConfiguration(proxyBeanMethods = false)
@Import(GitHubConnectorTestConfiguration.class)
public class OperationalPilotIdentity {
    public static final String TENANT = "d91d1df6-ae89-40c2-99b5-20edfb3e9ed0";
    public static final String SUBJECT = "db155203-fcb8-4cb4-9597-c853f21b0235";
    public static final String AUDIENCE = "pipeline-pilot";
    public static final String SCOPE = "Pipeline.Access";

    @Bean @Primary
    JwtDecoder signedPilotDecoder() {
        var decoder = OwnerChain.decoder();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(OwnerChain.ISSUER.toString()),
                ApiSecurityConfiguration.identityValidator(TENANT, AUDIENCE, SCOPE)));
        return decoder;
    }

    public static String grant(GitHubConnectorDouble facts, String repository) {
        var actor = TENANT + ":" + SUBJECT;
        facts.allow(actor, repository, false);
        return actor;
    }

    public static void onboardingFacts(GitHubConnectorDouble facts, String repository, String workflow,
            String installation, String name, String workflowName, String file) {
        var actor = TENANT + ":" + SUBJECT;
        facts.facts(actor, repository, new RepositoryAccess(repository, installation, true, true, true));
        facts.workflow(actor, repository, workflow, name, "main", workflowName, file);
    }

    public static void denyRepository(GitHubConnectorDouble facts, String repository) {
        facts.facts(TENANT + ":" + SUBJECT, repository, new RepositoryAccess(repository, null, false, false, false));
    }

    public static void unavailable(GitHubConnectorDouble facts, boolean unavailable) {
        facts.unavailable = unavailable;
    }

    public static String token(String tenant) {
        return OwnerChain.pilotToken(tenant, AUDIENCE, SUBJECT, SCOPE);
    }
}
