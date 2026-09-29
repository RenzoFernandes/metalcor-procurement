package com.metalcor.procurement.copilot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test double for OllamaClient: returns a fixed, settable response instead of calling a real Ollama server. */
public class StubOllamaClient implements OllamaClient {

    private volatile String nextResponse = "";

    public void respondWith(String response) {
        this.nextResponse = response;
    }

    @Override
    public String generateSql(String question) {
        return nextResponse;
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        public StubOllamaClient stubOllamaClient() {
            return new StubOllamaClient();
        }
    }
}