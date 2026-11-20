package org.pipesor.correlation.context.infrastructure.validation;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.pipesor.logevent.evidence.infrastructure.persistence.jdbc.JdbcEvidenceStore;
import org.pipesor.logevent.evidence.interfaces.rest.EvidenceMembershipHttpHandler;
import org.pipesor.pipeline.configuration.OwnerValidationConfiguration;
import org.pipesor.pipeline.configuration.OwnerValidationProperties;
import org.pipesor.pipeline.execution.application.ports.OperationalReferenceAuthority.Permission;
import org.pipesor.pipeline.execution.application.queries.OperationalReferenceQuery;
import org.pipesor.pipeline.execution.infrastructure.access.HttpIngestionScopeAuthority;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test composition with signed HTTP owners and an explicit synthetic current-scope decision. */
public final class OwnerChain implements AutoCloseable {
    public static final URI ISSUER = URI.create("https://identity.test/tenant/v2.0");
    public static final UUID DIRECTORY = UUID.fromString("31bb0907-a72d-422e-ad83-96355c46b2c3");
    public static final UUID NLP_CLIENT = UUID.fromString("4166972a-7490-433e-a5cf-3725d6ff823c");
    public static final UUID NLP_PRINCIPAL = UUID.fromString("08abff88-1291-47a2-b797-17b862032e95");
    public static final UUID CORRELATION_CLIENT = UUID.fromString("3086a1a3-f6ef-47ae-a049-cf9549c96f48");
    public static final UUID CORRELATION_PRINCIPAL = UUID.fromString("b624214f-7c7e-4688-b49a-53f1c7aeb346");
    public static final String NLP_ROLE = "Nlp.ProcessedEvidence.Send";
    public static final String CORRELATION_AUDIENCE = "correlation-machine";
    private static final String PIPELINE_ROLE = "Correlation.OperationalReferences.Check";
    private static final String MEMBERSHIP_ROLE = "Correlation.EvidenceMembership.Check";
    private static final KeyPair KEYS = keys();

    public final JdbcEvidenceStore evidenceStore;
    public final HttpOwnerReferences references;
    public final AtomicInteger membershipCalls = new AtomicInteger();
    private final HttpServer membershipServer;
    private final EvidenceMembershipHttpHandler membership;

    public OwnerChain(DataSource source, int pipelinePort, UUID captureTenant, String installationId) throws Exception {
        evidenceStore = new JdbcEvidenceStore(source, Clock.systemUTC());
        membership = new EvidenceMembershipHttpHandler(evidenceStore,
                new EvidenceMembershipHttpHandler.Settings(ISSUER, URI.create("https://identity.test/keys"),
                        "evidence-machine", DIRECTORY, CORRELATION_CLIENT, CORRELATION_PRINCIPAL,
                        MEMBERSHIP_ROLE, captureTenant, installationId, Duration.ZERO, Duration.ofSeconds(10),
                        8192, 16_384, 2), decoder(), Clock.systemUTC());
        membershipServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        membershipServer.createContext(EvidenceMembershipHttpHandler.PATH, exchange -> {
            membershipCalls.incrementAndGet();
            membership.handle(exchange);
        });
        membershipServer.start();
        references = new HttpOwnerReferences(URI.create("http://127.0.0.1:" + pipelinePort),
                URI.create("http://127.0.0.1:" + membershipServer.getAddress().getPort()), (owner, remaining) ->
                new HttpOwnerReferences.Token(token(owner == HttpOwnerReferences.Owner.PIPELINE
                        ? "pipeline-machine" : "evidence-machine", owner == HttpOwnerReferences.Owner.PIPELINE
                        ? PIPELINE_ROLE : MEMBERSHIP_ROLE, false), Instant.now().plusSeconds(120)),
                Duration.ofSeconds(10), 8192, 2048, Clock.systemUTC(), true);
    }

    public static void properties(DynamicPropertyRegistry registry) {
        registry.add("pipeline.owner-validation.enabled", () -> "true");
        registry.add("pipeline.owner-validation.issuer", ISSUER::toString);
        registry.add("pipeline.owner-validation.jwk-set-uri", () -> "https://identity.test/keys");
        registry.add("pipeline.owner-validation.tenant", DIRECTORY::toString);
        registry.add("pipeline.owner-validation.audience", () -> "pipeline-machine");
        registry.add("pipeline.owner-validation.caller-client-id", NLP_CLIENT::toString);
        registry.add("pipeline.owner-validation.caller-principal-id", NLP_PRINCIPAL::toString);
        registry.add("pipeline.owner-validation.role", () -> "Evidence.Check");
        registry.add("pipeline.owner-validation.correlation-client-id", CORRELATION_CLIENT::toString);
        registry.add("pipeline.owner-validation.correlation-principal-id", CORRELATION_PRINCIPAL::toString);
        registry.add("pipeline.owner-validation.correlation-role", () -> PIPELINE_ROLE);
        registry.add("pipeline.owner-validation.clock-skew", () -> "0s");
        registry.add("pipeline.owner-validation.request-timeout", () -> "10s");
        registry.add("pipeline.owner-validation.max-request-bytes", () -> "8192");
        registry.add("pipeline.owner-validation.max-response-bytes", () -> "2048");
        registry.add("pipeline.owner-validation.max-in-flight", () -> "2");
        registry.add("pipeline.owner-validation.ingestion-uri", () -> "https://ingestion.test/internal/v1/capture/machine-scope-checks");
        registry.add("pipeline.owner-validation.ingestion-scope", () -> "api://ingestion-machine/.default");
        registry.add("pipeline.owner-validation.connect-timeout", () -> "1s");
    }

    public static final class Scope {
        public final Set<String> deniedRevisions = ConcurrentHashMap.newKeySet();
        public final List<OperationalReferenceQuery> queries = new CopyOnWriteArrayList<>();

        private Permission verify(OperationalReferenceQuery query) {
            queries.add(query);
            return deniedRevisions.contains(query.scopeRevision()) ? Permission.DENIED : Permission.AUTHORIZED;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Fixture {
        @Bean
        Scope fixtureScope() { return new Scope(); }

        @Bean @Primary
        OwnerValidationConfiguration.MachineDecoder signedMachine(OwnerValidationProperties settings) {
            var decoder = decoder();
            decoder.setJwtValidator(OwnerValidationConfiguration.identityValidator(settings, Clock.systemUTC()));
            return new OwnerValidationConfiguration.MachineDecoder(decoder);
        }

        @Bean @Primary
        HttpIngestionScopeAuthority syntheticCurrentScope(Scope scope) {
            var authority = mock(HttpIngestionScopeAuthority.class);
            when(authority.verify(any(OperationalReferenceQuery.class), any(Instant.class), anyLong()))
                    .thenAnswer(call -> scope.verify(call.getArgument(0)));
            return authority;
        }
    }

    public static NimbusJwtDecoder decoder() {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
    }

    public static String token(String audience, String role, boolean nlp) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer(ISSUER.toString()).audience(audience)
                    .claim("tid", DIRECTORY.toString()).claim("idtyp", "app").claim("ver", "2.0")
                    .claim("azp", (nlp ? NLP_CLIENT : CORRELATION_CLIENT).toString())
                    .claim("oid", (nlp ? NLP_PRINCIPAL : CORRELATION_PRINCIPAL).toString())
                    .claim("roles", List.of(role)).issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(300))).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
            jwt.sign(new RSASSASigner(KEYS.getPrivate()));
            return jwt.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException("Synthetic token signing failed");
        }
    }

    public static String pilotToken(String tenant, String audience, String subject, String scope) {
        try {
            var claims = new JWTClaimsSet.Builder().issuer(ISSUER.toString()).audience(audience)
                    .claim("tid", tenant).claim("oid", subject).claim("scp", scope)
                    .expirationTime(Date.from(Instant.now().plusSeconds(System.getenv("PIPESOR_DEMO_INTERACTIVE_DIR") == null ? 300 : 1800))).build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(KEYS.getPrivate()));
            return jwt.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException("Synthetic token signing failed", failure);
        }
    }

    private static KeyPair keys() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) {
            throw new IllegalStateException("Synthetic RSA key generation failed");
        }
    }

    @Override public void close() {
        references.close();
        membershipServer.stop(0);
        membership.close();
    }
}
