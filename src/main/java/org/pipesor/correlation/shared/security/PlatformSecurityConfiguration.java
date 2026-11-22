package org.pipesor.correlation.shared.security;

import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;

import java.time.Clock;

/**
 * Shared composition of the platform identity contract: signing key, workload token issuance,
 * token verification and the stateless filter chain. Services contribute only their routes.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PlatformSecurityProperties.class)
public class PlatformSecurityConfiguration {

    /** Service-owned route rules, applied between the public routes and the final deny-all. */
    @FunctionalInterface
    public interface PlatformRoutes {
        void configure(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry routes);
    }

    @Bean
    TransportPolicy transportPolicy(Environment environment) {
        return TransportPolicy.forProfiles(environment.getActiveProfiles());
    }

    @Bean
    SigningKey workloadSigningKey(PlatformSecurityProperties settings, TransportPolicy transport) {
        if (settings.signingKeyFile() != null) {
            if (!transport.loopbackHttpAllowed()) throw new IllegalStateException("File signing keys require the local profile");
            return new PemSigningKey(settings.signingKeyFile());
        }
        return new KeyVaultSigningKey(settings.signingKeyId(), new DefaultAzureCredentialBuilder().build());
    }

    @Bean
    WorkloadTokens workloadTokens(PlatformSecurityProperties settings, SigningKey workloadSigningKey) {
        return new WorkloadTokens(settings.workloadId(), settings.tenantId(), workloadSigningKey, Clock.systemUTC());
    }

    @Bean
    JwtDecoder platformJwtDecoder(PlatformSecurityProperties settings, TransportPolicy transport) {
        return new PlatformJwtDecoder(settings, transport);
    }

    @Bean
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, JwtDecoder platformJwtDecoder,
            PlatformRoutes routes) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.requireExplicitSave(true))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new AccessDeniedHandlerImpl()))
                .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.decoder(platformJwtDecoder)
                        .jwtAuthenticationConverter(PlatformAuthorities.converter())))
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(HttpMethod.GET, JwksController.PATH, "/actuator/health/liveness",
                            "/actuator/health/readiness").permitAll();
                    routes.configure(requests);
                    requests.anyRequest().denyAll();
                });
        return http.build();
    }
}
