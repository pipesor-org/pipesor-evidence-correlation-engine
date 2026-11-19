package com.pipesor.recommendation.diagnosis.infrastructure;

import com.azure.identity.AzureCliCredentialBuilder;
import com.pipesor.recommendation.diagnosis.application.DiagnosticOrchestrator.Inference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Optional;

public final class FoundryDiagnosticFixture {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final URI ENDPOINT = URI.create("https://ai-pipesor-dev-04f339b7.services.ai.azure.com");
    private static final String DEPLOYMENT = "pipesor-dev-diagnostics";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private FoundryDiagnosticFixture() {}

    public static Inference create() {
        String evidencePath = System.getenv("PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR");
        if (evidencePath == null || evidencePath.isBlank()) {
            throw new IllegalArgumentException("Set PIPESOR_DEMO_FOUNDRY_EVIDENCE_DIR to a protected directory");
        }
        Path evidenceDir = Path.of(evidencePath);
        String scenario = System.getProperty("pipesor.fixture.scenario", "local-checkout-dns");
        boolean preliminary = scenario.equals("E02") || scenario.equals("E03");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        var client = RestClient.builder().requestFactory(factory).bufferContent((uri, method) -> true)
                .requestInterceptor((request, body, next) -> {
                    if (!request.getURI().equals(ENDPOINT.resolve("/openai/v1/chat/completions")) || body.length > 8000) {
                        throw new IllegalStateException("Synthetic request exceeds the approved bounds");
                    }
                    var wire = JSON.readTree(body);
                    if (wire.get("max_completion_tokens").asInt() > 1024
                            || !wire.get("model").asString().equals(DEPLOYMENT)) {
                        throw new IllegalStateException("Unexpected model or output bound");
                    }
                    Path receipt = null;
                    // One slot per outbound attempt, including timeout or non-2xx.
                    for (int i = 1; i <= (preliminary ? 6 : 8); i++) {
                        try {
                            receipt = Files.createFile(evidenceDir.resolve("attempt-" + i + ".json"));
                            break;
                        } catch (FileAlreadyExistsException used) {
                            // Continue to the next unused slot.
                        }
                    }
                    if (receipt == null) throw new IllegalStateException("Foundry smoke request budget exhausted");
                    var info = new LinkedHashMap<String, Object>();
                    info.put("scenario", scenario);
                    info.put("deployment", DEPLOYMENT);
                    info.put("endpoint", request.getURI().toString());
                    info.put("authentication", "Azure CLI-backed Microsoft Entra");
                    try {
                        var response = next.execute(request, body);
                        info.put("http", response.getStatusCode().value());
                        info.put("request_id", Optional.ofNullable(response.getHeaders().getFirst("apim-request-id"))
                                .orElse(response.getHeaders().getFirst("x-ms-request-id")));
                        var bytes = response.getBody().readNBytes(128 * 1024 + 1);
                        if (bytes.length > 0 && bytes.length <= 128 * 1024) {
                            try {
                                var payload = JSON.readTree(bytes);
                                if (response.getStatusCode().is2xxSuccessful()) {
                                    info.put("model", payload.path("model").asString());
                                    info.put("prompt_tokens", payload.at("/usage/prompt_tokens").asInt());
                                    info.put("completion_tokens", payload.at("/usage/completion_tokens").asInt());
                                    info.put("finish_reason", payload.at("/choices/0/finish_reason").asString());
                                    if (preliminary) {
                                        // The preliminary runner admits only the pinned synthetic inputs.
                                        info.put("synthetic_model_output", payload.at("/choices/0/message/content").asString());
                                    }
                                } else {
                                    info.put("error_code", payload.at("/error/code").asString()
                                            .replaceAll("[^a-zA-Z0-9_.-]", ""));
                                }
                            } catch (RuntimeException ignored) {
                                info.put("metadata", "unreadable");
                            }
                        }
                        if (response.getStatusCode().is2xxSuccessful()
                                && !"gpt-4o-2024-11-20".equals(info.get("model"))) {
                            response.close();
                            throw new IllegalStateException("Unexpected Foundry model version");
                        }
                        return response;
                    } catch (java.io.IOException failure) {
                        info.put("transport_error", failure.getClass().getSimpleName());
                        throw failure;
                    } finally {
                        Files.writeString(receipt, JSON.writeValueAsString(info));
                        info.remove("synthetic_model_output");
                        System.out.println("PIPESOR_FOUNDRY_RECEIPT " + JSON.writeValueAsString(info));
                    }
                }).build();
        return new AzureOpenAiInference(ENDPOINT, DEPLOYMENT, new AzureCliCredentialBuilder().build(), TIMEOUT, client);
    }
}
