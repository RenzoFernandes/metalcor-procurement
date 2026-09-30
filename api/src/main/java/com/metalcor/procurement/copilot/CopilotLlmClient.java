package com.metalcor.procurement.copilot;

/**
 * Talks to the model that turns a natural-language question into SQL. An interface because there is
 * more than one provider (local Ollama, Google Gemini, chosen by app.copilot.provider) and so tests
 * can substitute a stub and never depend on a real model server.
 */
public interface CopilotLlmClient {

    /** Shown to the user when any provider is unreachable or over quota; never names the provider. */
    String UNAVAILABLE_MESSAGE = "Copiloto indisponível no momento. Tente novamente em alguns minutos.";

    /** Raw model response text for the given question, not yet extracted or validated as SQL. */
    String generateSql(String question);
}