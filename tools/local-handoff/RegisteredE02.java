package org.pipesor.correlation.context.infrastructure.delivery;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.pipesor.logevent.evidence.interfaces.messaging.ProtectedEvidenceV2;
import org.pipesor.pipeline.GitHubConnectorDouble;
import org.pipesor.pipeline.OperationalPilotIdentity;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

final class RegisteredE02 {
    private static final String REPOSITORY = "pilot/reporting-service";
    private static final String WORKFLOW = "Integration checks";
    private static final String FILE = ".github/workflows/integration-checks.yml";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String INTERACTIVE = System.getenv("PIPESOR_DEMO_INTERACTIVE_DIR");
    private static final String BROWSER = System.getenv("PIPESOR_DEMO_REGISTRATION_BROWSER");

    static boolean interactive() { return INTERACTIVE != null && !INTERACTIVE.isBlank(); }
    static boolean enabled() { return interactive() || BROWSER != null && !BROWSER.isBlank(); }

    static void facts(GitHubConnectorDouble github, ProtectedEvidenceV2.Binding binding) {
        OperationalPilotIdentity.onboardingFacts(github, binding.githubRepositoryId(), binding.githubWorkflowId(),
                binding.githubInstallationId(), REPOSITORY, WORKFLOW, FILE);
    }

    static void register(int port, GitHubConnectorDouble github, ProtectedEvidenceV2.Binding binding) throws Exception {
        assertThat(binding.tenantId().toString()).isEqualTo(OperationalPilotIdentity.TENANT);
        facts(github, binding);
        var token = OperationalPilotIdentity.token(OperationalPilotIdentity.TENANT);
        var catalog = JSON.writeValueAsString(List.of(Map.of(
                "repository", REPOSITORY, "repositoryId", binding.githubRepositoryId(),
                "workflowId", binding.githubWorkflowId(), "workflowName", WORKFLOW,
                "workflowPath", FILE, "defaultBranch", "main")));
        if (interactive()) {
            write("port", Integer.toString(port));
            write("catalog.json", catalog);
            write("pipeline-token", token);
            System.out.println("PIPESOR_ADVISOR_READY register the E02 workflow, then signal replay");
            await("replay");
        } else {
            var log = Files.createTempFile("pipesor-e02-registration-", ".log");
            try {
                var command = new ProcessBuilder("node", BROWSER, "owner-chain")
                        .redirectErrorStream(true).redirectOutput(log.toFile());
                command.environment().put("PIPESOR_DEMO_PORT", Integer.toString(port));
                command.environment().put("PIPESOR_REGISTRATION_TOKEN", token);
                command.environment().put("PIPESOR_DEMO_REGISTRATION_CATALOG", catalog);
                var process = command.start();
                try {
                    assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("Registration browser finished").isTrue();
                    assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
                } finally {
                    if (process.isAlive()) process.destroyForcibly();
                    System.out.print(Files.readString(log));
                }
            } finally {
                Files.deleteIfExists(log);
            }
        }
        String path = "/api/v1/pipelines/" + binding.pipelineId();
        var record = read(port, path, token);
        assertThat(record.path("id").asString()).isEqualTo(binding.pipelineId().toString());
        assertThat(record.path("githubRepositoryId").asString()).isEqualTo(binding.githubRepositoryId());
        assertThat(record.path("githubWorkflowId").asString()).isEqualTo(binding.githubWorkflowId());
        assertThat(record.path("monitoringStatus").asString()).isEqualTo("ACTIVE");
        assertThat(read(port, path + "/incidents", token).path("items").size()).isZero();
        System.out.println("PIPESOR_E02_BINDING verified registered pipeline identity; zero incidents before replay");
    }

    static void present(UUID incidentId, UUID reviewId, String token) throws Exception {
        write("review-token", token);
        write("review-path", "/incidents/" + incidentId + "?replay=" + reviewId);
        write("validation-path", "/operations?queue=validation&review=" + reviewId);
        System.out.println("PIPESOR_ADVISOR_DIAGNOSIS_READY open the scoped P3 link; signal stop to finish");
        await("stop");
    }

    private static tools.jackson.databind.JsonNode read(int port, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private static void write(String name, String content) throws Exception {
        var file = Path.of(INTERACTIVE, name);
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, content);
    }

    private static void await(String signal) throws InterruptedException {
        var deadline = Instant.now().plus(Duration.ofMinutes(10));
        while (!Files.isRegularFile(Path.of(INTERACTIVE, signal))) {
            if (Instant.now().isAfter(deadline)) throw new IllegalStateException("Advisor session timed out: " + signal);
            Thread.sleep(250);
        }
    }
}
