package com.metalcor.procurement.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.metalcor.procurement.common.ServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Provider selection (app.copilot.provider) and the Gemini client against a local stub HTTP
 * server (the JDK's own HttpServer, no extra dependency). No key is real and no network call
 * leaves the machine. No database needed.
 */
class CopilotProviderTest {

    private static final String MODEL = "test-model";

    private static final String OK_BODY = """
            {"candidates":[{"content":{"parts":[{"text":"```sql\\nselect 1\\n```"}],"role":"model"},\
            "finishReason":"STOP"}],"usageMetadata":{}}""";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
            .withUserConfiguration(OllamaRestClient.class, GeminiClient.class)
            .withPropertyValues(
                    "copilot.ollama.base-url=http://localhost:1",
                    "copilot.ollama.model=test-model",
                    "app.copilot.gemini.base-url=http://localhost:1",
                    "app.copilot.gemini.model=" + MODEL);

    private HttpServer server;
    private volatile int status;
    private volatile String body;
    private volatile long delayMillis;
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @BeforeEach
    void startStubServer() throws IOException {
        status = 200;
        body = OK_BODY;
        delayMillis = 0;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/", exchange -> {
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // The client gave up (timeout test).
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    private String stubUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private GeminiClient clientWithKey(String apiKey) {
        return new GeminiClient(stubUrl(), apiKey, MODEL);
    }

    @Test
    void defaultsToOllamaWhenTheProviderIsNotSet() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CopilotLlmClient.class);
            assertThat(context).hasSingleBean(OllamaRestClient.class);
            assertThat(context).doesNotHaveBean(GeminiClient.class);
        });
    }

    @Test
    void selectsOllamaWhenExplicitlyConfigured() {
        contextRunner.withPropertyValues("app.copilot.provider=ollama").run(context -> {
            assertThat(context.getBean(CopilotLlmClient.class)).isInstanceOf(OllamaRestClient.class);
            assertThat(context).doesNotHaveBean(GeminiClient.class);
        });
    }

    @Test
    void selectsGeminiWhenConfigured() {
        contextRunner.withPropertyValues(
                "app.copilot.provider=gemini",
                "app.copilot.gemini.api-key=fake-key").run(context -> {
            assertThat(context).hasSingleBean(CopilotLlmClient.class);
            assertThat(context.getBean(CopilotLlmClient.class)).isInstanceOf(GeminiClient.class);
            assertThat(context).doesNotHaveBean(OllamaRestClient.class);
        });
    }

    @Test
    void geminiWithoutAnApiKeyFailsAtStartup() {
        contextRunner.withPropertyValues("app.copilot.provider=gemini", "app.copilot.gemini.api-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sendsTheApiKeyHeaderAndThePromptAndReturnsTheCandidateText() {
        String response = clientWithKey("fake-key").generateSql("Quanto gastamos por categoria?");

        assertThat(response).contains("select 1");
        assertThat(apiKeyHeader.get()).isEqualTo("fake-key");
        assertThat(requestPath.get()).isEqualTo("/v1beta/models/" + MODEL + ":generateContent");
        assertThat(requestBody.get())
                .contains("\"contents\"")
                .contains("\"parts\"")
                .contains("Quanto gastamos por categoria?")
                .contains("vw_spend_by_month_category");
    }

    @Test
    void emptyCandidatesBecomeServiceUnavailable() {
        body = "{\"candidates\":[]}";

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE);
    }

    @Test
    void blockedResponseWithoutCandidatesBecomeServiceUnavailable() {
        body = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE);
    }

    @Test
    void candidateWithoutTextBecomesServiceUnavailable() {
        body = "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}";

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE);
    }

    @Test
    void rateLimitBecomesServiceUnavailableWithANeutralMessage() {
        status = 429;
        body = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}";

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Copilot is unavailable right now. Please try again in a few minutes.")
                .hasMessageNotContaining("Ollama")
                .hasMessageNotContaining("Gemini");
    }

    @Test
    void invalidKeyBecomesServiceUnavailableAndNeverLeaksTheKey() {
        status = 401;
        body = "{\"error\":{\"code\":401,\"status\":\"UNAUTHENTICATED\"}}";

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE)
                .hasMessageNotContaining("fake-key");
    }

    @Test
    void forbiddenBecomesServiceUnavailable() {
        status = 403;
        body = "{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}";

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE);
    }

    @Test
    void timeoutBecomesServiceUnavailable() {
        delayMillis = 2000;
        GeminiClient slowClient = new GeminiClient(stubUrl(), "fake-key", MODEL, Duration.ofMillis(200));

        assertThatThrownBy(() -> slowClient.generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE);
    }

    @Test
    void networkFailureBecomesServiceUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> clientWithKey("fake-key").generateSql("teste"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(CopilotLlmClient.UNAVAILABLE_MESSAGE);
    }
}