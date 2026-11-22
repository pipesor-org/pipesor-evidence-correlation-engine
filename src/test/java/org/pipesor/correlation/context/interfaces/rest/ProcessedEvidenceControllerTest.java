package org.pipesor.correlation.context.interfaces.rest;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pipesor.correlation.configuration.CorrelationRoutes;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Caller;
import org.pipesor.correlation.context.infrastructure.persistence.jpa.ReceiveProcessedEvidence.Outcome;
import org.pipesor.correlation.shared.infrastructure.json.CanonicalJson;
import org.pipesor.correlation.shared.security.JwksController;
import org.pipesor.correlation.shared.security.PlatformIds;
import org.pipesor.correlation.shared.security.PlatformSecurityConfiguration;
import org.pipesor.correlation.shared.security.SigningKey;
import org.pipesor.correlation.shared.security.WorkloadTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ProcessedEvidenceController.class, JwksController.class})
@Import({PlatformSecurityConfiguration.class, CorrelationRoutes.class, ProcessedEvidenceControllerTest.Stubs.class})
@ActiveProfiles("local")
class ProcessedEvidenceControllerTest {
    private static final UUID TENANT = UUID.fromString("d91d1df6-ae89-40c2-99b5-20edfb3e9ed0");
    private static final String MESSAGE = "cf6b69cf-a28d-49f3-bceb-000000000001";
    private static final String TRACE = "ci-investigation-204";
    private static final KeyPair NLP = rsa();
    private static final KeyPair RAG = rsa();
    private static final HttpServer JWKS = jwks();
    private static final Path OWN_KEY = ownKey();
    private static final AtomicReference<Outcome> OUTCOME = new AtomicReference<>(Outcome.ACCEPTED);
    private static final AtomicReference<Caller> CALLER = new AtomicReference<>();
    private static final AtomicInteger CALLS = new AtomicInteger();

    @Autowired MockMvc mvc;

    @DynamicPropertySource
    static void platform(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + JWKS.getAddress().getPort();
        registry.add("pipesor.workload-id", () -> "correlation");
        registry.add("pipesor.tenant-id", TENANT::toString);
        registry.add("pipesor.signing-key-file", OWN_KEY::toString);
        registry.add("pipesor.security.audience", () -> "urn:pipesor:api:correlation");
        registry.add("pipesor.security.trusted-workloads[0].id", () -> "nlp");
        registry.add("pipesor.security.trusted-workloads[0].jwks-uri", () -> base + "/nlp");
        registry.add("pipesor.security.trusted-workloads[0].roles", () -> CorrelationRoutes.NLP_ROLE);
        registry.add("pipesor.security.trusted-workloads[1].id", () -> "rag");
        registry.add("pipesor.security.trusted-workloads[1].jwks-uri", () -> base + "/rag");
        registry.add("pipesor.security.trusted-workloads[1].roles", () -> "Rag.Query");
    }

    @TestConfiguration
    static class Stubs {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ProcessedEvidenceController.Processor processor() {
            return (evidence, caller, deadline) -> {
                CALLS.incrementAndGet();
                CALLER.set(caller);
                return OUTCOME.get();
            };
        }
        @Bean ProcessedEvidenceController.Limits limits() {
            return new ProcessedEvidenceController.Limits(TENANT, Duration.ofSeconds(5), 8192, 512, 4, 422, 413);
        }
    }

    @BeforeEach
    void reset() {
        OUTCOME.set(Outcome.ACCEPTED);
        CALLER.set(null);
        CALLS.set(0);
    }

    @AfterAll
    static void stop() { JWKS.stop(0); }

    @Test
    void onlyTheNlpWorkloadWithItsRoleReachesTheProcessor() throws Exception {
        mvc.perform(request(body(), null)).andExpect(status().isUnauthorized());
        mvc.perform(request(body(), token(NLP, "nlp", "Rag.Query"))).andExpect(status().isUnauthorized());
        mvc.perform(request(body(), token(RAG, "rag", "Rag.Query"))).andExpect(status().isForbidden());
        mvc.perform(request(body(), token(NLP, "nlp", CorrelationRoutes.NLP_ROLE)).header("Authorization", "Bearer x"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(ProcessedEvidenceController.PATH).header("Authorization", "Bearer " + token(NLP, "nlp", CorrelationRoutes.NLP_ROLE)))
                .andExpect(status().isForbidden());
        assertThat(CALLS).hasValue(0);
    }

    @Test
    void confirmsWithTheOriginalMessageAndBindsTheCaptureScopePerRequest() throws Exception {
        mvc.perform(request(body(), token(NLP, "nlp", CorrelationRoutes.NLP_ROLE)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("accepted")).andExpect(jsonPath("$.message_id").value(MESSAGE));
        var caller = CALLER.get();
        assertThat(caller.producerKey()).isEqualTo("workload:" + PlatformIds.workload("nlp"));
        assertThat(caller.tenantId()).isEqualTo(TENANT);
        assertThat(caller.scopeKey()).startsWith("github:6802100000000000000000000/7710600000000000000000000/");
        OUTCOME.set(Outcome.DUPLICATE);
        mvc.perform(request(body(), token(NLP, "nlp", CorrelationRoutes.NLP_ROLE))).andExpect(jsonPath("$.status").value("duplicate"));
    }

    @Test
    void mapsOutcomesToProblemResponses() throws Exception {
        String credential = token(NLP, "nlp", CorrelationRoutes.NLP_ROLE);
        for (var expected : new Object[][]{{Outcome.RESOURCE_DENIED, 422}, {Outcome.CAPACITY_EXCEEDED, 413},
                {Outcome.MESSAGE_CONFLICT, 409}, {Outcome.REFERENCE_MISMATCH, 400}, {Outcome.NOT_READY, 503},
                {Outcome.UNAVAILABLE, 503}}) {
            OUTCOME.set((Outcome) expected[0]);
            mvc.perform(request(body(), credential)).andExpect(status().is((int) expected[1]))
                    .andExpect(header().string("Content-Type", "application/problem+json"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void rejectsForeignTenantsAndMalformedRequestsWithoutProcessing() throws Exception {
        String credential = token(NLP, "nlp", CorrelationRoutes.NLP_ROLE);
        var foreign = body();
        ((ObjectNode) foreign.get("capture_context")).put("tenant_id", UUID.randomUUID().toString());
        mvc.perform(request(foreign, credential)).andExpect(status().is(422));
        mvc.perform(request(body(), credential).contentType("text/plain")).andExpect(status().is(415));
        mvc.perform(post(ProcessedEvidenceController.PATH).header("Authorization", "Bearer " + credential)
                .contentType("application/json").header("X-Correlation-ID", TRACE)
                .content(CanonicalJson.mapper().writeValueAsString(body()))).andExpect(status().isBadRequest());
        mvc.perform(request(body().put("message_id", UUID.randomUUID().toString()), credential)).andExpect(status().isBadRequest());
        var large = body().put("sanitized_content", "x".repeat(9000));
        mvc.perform(request(large, credential)).andExpect(status().is(413));
        assertThat(CALLS).hasValue(0);
    }

    @Test
    void publishesOnlyPublicKeys() throws Exception {
        mvc.perform(get(JwksController.PATH)).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    private static MockHttpServletRequestBuilder request(ObjectNode body, String token) throws Exception {
        var request = post(ProcessedEvidenceController.PATH).contentType("application/json; charset=utf-8")
                .header("Idempotency-Key", MESSAGE).header("X-Correlation-ID", TRACE)
                .content(CanonicalJson.mapper().writeValueAsString(body));
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private static ObjectNode body() throws Exception {
        return (ObjectNode) CanonicalJson.mapper().readTree(Files.readAllBytes(
                Path.of("docs/contracts/nlp-processed-evidence-v2.examples.json"))).get("with_job");
    }

    private static String token(KeyPair pair, String workload, String role) {
        return new WorkloadTokens(workload, TENANT, key(pair), Clock.systemUTC()).issue("correlation", role).value();
    }

    private static SigningKey key(KeyPair pair) {
        return new SigningKey() {
            @Override public RSAPublicKey publicKey() { return (RSAPublicKey) pair.getPublic(); }
            @Override public byte[] sign(byte[] input) {
                try {
                    var signature = Signature.getInstance("SHA256withRSA");
                    signature.initSign(pair.getPrivate());
                    signature.update(input);
                    return signature.sign();
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
        };
    }

    private static HttpServer jwks() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            for (var entry : new Object[][]{{"/nlp", NLP}, {"/rag", RAG}}) {
                var pair = (KeyPair) entry[1];
                byte[] json = new JWKSet(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).keyID(key(pair).keyId()).build())
                        .toString(true).getBytes(StandardCharsets.UTF_8);
                server.createContext((String) entry[0], exchange -> {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, json.length);
                    exchange.getResponseBody().write(json);
                    exchange.close();
                });
            }
            server.start();
            return server;
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static Path ownKey() {
        try {
            Path file = Files.createTempFile("correlation-test", ".pem");
            Files.writeString(file, "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(rsa().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            file.toFile().deleteOnExit();
            return file;
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static KeyPair rsa() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
