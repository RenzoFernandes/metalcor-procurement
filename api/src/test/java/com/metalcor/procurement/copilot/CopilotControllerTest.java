package com.metalcor.procurement.copilot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.metalcor.procurement.AbstractIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * POST /api/v1/copilot/query, with the real model provider replaced by StubCopilotLlmClient: no
 * network call is made, and the response text is fully controlled by each test.
 */
@Order(0)
@Import(StubCopilotLlmClient.Config.class)
class CopilotControllerTest extends AbstractIntegrationTest {

    private static final String USER_ID = "1";

    @Autowired
    private StubCopilotLlmClient llm;

    @Test
    void validSelectRunsAndReturnsRows() throws Exception {
        llm.respondWith("""
                Here is the query:
                ```sql
                select category_code, category_name from vw_spend_by_month_category limit 5
                ```
                """);

        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"Quanto gastamos por categoria?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value(org.hamcrest.Matchers.containsStringIgnoringCase("select")))
                .andExpect(jsonPath("$.colunas").isNotEmpty())
                .andExpect(jsonPath("$.linhas").isArray())
                .andExpect(jsonPath("$.totalLinhas").isNumber());
    }

    @Test
    void writeStatementFromTheModelIsRejectedAndNeverExecuted() throws Exception {
        llm.respondWith("""
                ```sql
                DELETE FROM materials
                ```
                """);

        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"Apague os materiais\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void multipleStatementsFromTheModelAreRejected() throws Exception {
        llm.respondWith("```sql\nselect 1; drop table materials\n```");

        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"teste\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void syntacticallyValidSqlWithAnUnknownColumnIsRejectedAsBadRequestNotServerError() throws Exception {
        llm.respondWith("```sql\nselect coluna_que_nao_existe from vw_invoice_match\n```");

        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"teste\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "O SQL gerado pelo copiloto não é válido. Tente reformular a pergunta."));
    }

    @Test
    void requiresAValidXUserIdHeader() throws Exception {
        llm.respondWith("```sql\nselect 1\n```");

        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", "") // blank = missing; DefaultUserHeaderConfig adds a default
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"teste\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tooLongQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exceedingTheRateLimitReturns429WithRetryAfter() throws Exception {
        llm.respondWith("```sql\nselect 1\n```");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/copilot/query")
                            .header("X-User-Id", "6")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"pergunta\": \"teste\"}"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", "6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"teste\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"))
                .andExpect(jsonPath("$.title").value("Too many requests"));

        // The limit is per user: someone else is not affected.
        mockMvc.perform(post("/api/v1/copilot/query")
                        .header("X-User-Id", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\": \"teste\"}"))
                .andExpect(status().isOk());
    }
}
