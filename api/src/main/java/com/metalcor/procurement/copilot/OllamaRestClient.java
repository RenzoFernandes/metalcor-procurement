package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.ServiceUnavailableException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Real implementation: calls the non-streaming /api/generate endpoint of a local Ollama server.
 * No new HTTP client library: Spring's own RestClient (spring-boot-starter-web) is enough.
 */
@Component
public class OllamaRestClient implements OllamaClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final String model;

    public OllamaRestClient(
            @Value("${copilot.ollama.base-url}") String baseUrl,
            @Value("${copilot.ollama.model}") String model) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public String generateSql(String question) {
        String prompt = CopilotPromptBuilder.build(question);
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/api/generate")
                    .body(Map.of(
                            "model", model,
                            "prompt", prompt,
                            "stream", false))
                    .retrieve()
                    .body(Map.class);
            Object text = response == null ? null : response.get("response");
            return text == null ? "" : text.toString();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(
                    "Copiloto indisponível: verifique se o Ollama está rodando em " + baseUrl + ".");
        }
    }
}