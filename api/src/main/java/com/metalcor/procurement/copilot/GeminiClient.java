package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.ServiceUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Hosted provider (app.copilot.provider=gemini), for environments where a local Ollama is not
 * reachable. Calls the Google Gemini generateContent endpoint
 * (POST {base-url}/v1beta/models/{model}:generateContent) with the key in the x-goog-api-key header.
 * The prompt comes from CopilotPromptBuilder, the same one Ollama uses.
 */
@Component
@ConditionalOnProperty(name = "app.copilot.provider", havingValue = "gemini")
public class GeminiClient implements CopilotLlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final String model;

    @Autowired
    public GeminiClient(
            @Value("${app.copilot.gemini.base-url}") String baseUrl,
            @Value("${app.copilot.gemini.api-key:}") String apiKey,
            @Value("${app.copilot.gemini.model}") String model) {
        this(baseUrl, apiKey, model, Duration.ofSeconds(60));
    }

    GeminiClient(String baseUrl, String apiKey, String model, Duration readTimeout) {
        if (apiKey == null || apiKey.isBlank()) {
            // Fail at startup rather than on the first question.
            throw new IllegalStateException(
                    "app.copilot.provider=gemini requires the GEMINI_API_KEY environment variable.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.model = model;
    }

    @Override
    public String generateSql(String question) {
        String prompt = CopilotPromptBuilder.build(question);
        try {
            Map<?, ?> response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))))
                    .retrieve()
                    .body(Map.class);
            String text = firstCandidateText(response);
            if (text == null) {
                // Empty or missing candidates, typically a response blocked by a safety filter.
                log.warn("Copilot provider gemini returned no candidate text");
                throw new ServiceUnavailableException(CopilotLlmClient.UNAVAILABLE_MESSAGE);
            }
            return text;
        } catch (RestClientResponseException e) {
            // 429 = rate limit; 401/403 = bad key. The status goes to the log, never the key.
            log.warn("Copilot provider gemini answered HTTP {}", e.getStatusCode().value());
            throw new ServiceUnavailableException(CopilotLlmClient.UNAVAILABLE_MESSAGE);
        } catch (RestClientException e) {
            // Timeouts and network errors.
            log.warn("Copilot provider gemini unreachable: {}", e.getMessage());
            throw new ServiceUnavailableException(CopilotLlmClient.UNAVAILABLE_MESSAGE);
        }
    }

    /** candidates[0].content.parts[0].text, or null when the response has no such text. */
    private static String firstCandidateText(Map<?, ?> response) {
        if (response != null && response.get("candidates") instanceof List<?> candidates && !candidates.isEmpty()
                && candidates.get(0) instanceof Map<?, ?> candidate
                && candidate.get("content") instanceof Map<?, ?> content
                && content.get("parts") instanceof List<?> parts && !parts.isEmpty()
                && parts.get(0) instanceof Map<?, ?> part
                && part.get("text") != null) {
            return part.get("text").toString();
        }
        return null;
    }
}