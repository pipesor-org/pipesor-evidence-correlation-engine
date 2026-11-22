package org.pipesor.correlation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** The packaged runtime starts with the local profile, migrates its schema and protects every business route. */
class CorrelationApplicationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("pgvector/pgvector:pg17");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static ConfigurableApplicationContext context;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        POSTGRES.start();
        Path key = Files.createTempFile("correlation-app", ".pem");
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
        key.toFile().deleteOnExit();
        context = new SpringApplicationBuilder(CorrelationApplication.class).profiles("local").run(
                "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--pipesor.workload-id=correlation", "--pipesor.tenant-id=24c96be0-50f3-4064-b5e4-643088141ae8",
                "--pipesor.signing-key-file=" + key, "--pipesor.security.audience=urn:pipesor:api:correlation",
                "--pipesor.security.trusted-workloads[0].id=nlp",
                "--pipesor.security.trusted-workloads[0].jwks-uri=http://127.0.0.1:9/.well-known/jwks.json",
                "--pipesor.security.trusted-workloads[0].roles=Nlp.ProcessedEvidence.Send",
                "--pipesor.endpoints.pipeline=http://127.0.0.1:9", "--pipesor.endpoints.logevent=http://127.0.0.1:9",
                "--pipesor.endpoints.recommendation=http://127.0.0.1:9");
        base = "http://127.0.0.1:" + context.getEnvironment().getProperty("local.server.port");
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
        POSTGRES.stop();
    }

    @Test
    void migratesOnlyItsOwnSchemaHistoryAndReportsReadiness() throws Exception {
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evidence.flyway_history_correlation WHERE success AND version <> '0'",
                Integer.class)).isEqualTo(5);
        assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/env").statusCode()).isIn(401, 403);
        assertThat(get("/.well-known/oauth-protected-resource").statusCode()).isEqualTo(404);
        var logout = HTTP.send(HttpRequest.newBuilder(URI.create(base + "/logout")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(logout.statusCode()).isIn(401, 403);
    }

    @Test
    void publishesKeysAndRejectsUnauthenticatedEvidence() throws Exception {
        var jwks = get("/.well-known/jwks.json");
        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(jwks.body()).contains("\"kty\":\"RSA\"").doesNotContain("\"d\":");
        var post = HTTP.send(HttpRequest.newBuilder(URI.create(base + "/internal/v1/processed-evidence"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(post.statusCode()).isEqualTo(401);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(base + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
