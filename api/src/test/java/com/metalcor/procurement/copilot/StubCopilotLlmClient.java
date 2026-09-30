package com.metalcor.procurement.copilot;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test double for CopilotLlmClient: returns a fixed, settable response instead of calling a real model server. */
public class StubCopilotLlmClient implements CopilotLlmClient {

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
        public StubCopilotLlmClient stubCopilotLlmClient() {
            return new StubCopilotLlmClient();
        }
    }
}