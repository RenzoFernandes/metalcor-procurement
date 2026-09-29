package com.metalcor.procurement.copilot;

/**
 * Talks to the model that turns a natural-language question into SQL. An interface so tests can
 * substitute a stub and never depend on a real Ollama server running.
 */
public interface OllamaClient {

    /** Raw model response text for the given question, not yet extracted or validated as SQL. */
    String generateSql(String question);
}