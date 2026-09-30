package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.ServiceUnavailableException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Local provider (app.copilot.provider=ollama, the default): calls the non-streaming /api/generate
 * endpoint of a local Ollama server. No new HTTP client library: Spring's own RestClient
 * (spring-boot-starter-web) is enough.
 */
@Component
@ConditionalOnProperty(name = "app.copilot.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaRestClient implements CopilotLlmClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaRestClient.class);

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
            // The user-facing message is provider-neutral; the detail for the developer stays in the log.
            log.warn("Copilot provider ollama unavailable at {}: {}", baseUrl, e.getMessage());
            throw new ServiceUnavailableException(CopilotLlmClient.UNAVAILABLE_MESSAGE);
        }
    }
}