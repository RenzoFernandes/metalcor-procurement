package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.BadRequestException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Orchestrates one copilot question: ask the model, extract the SQL, validate it, run it. */
@Service
public class CopilotService {

    private static final Pattern SQL_FENCE = Pattern.compile("```sql\\s*(.*?)```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_FENCE = Pattern.compile("```\\s*(.*?)```", Pattern.DOTALL);

    private final CopilotLlmClient llmClient;
    private final CopilotRepository repository;

    public CopilotService(CopilotLlmClient llmClient, CopilotRepository repository) {
        this.llmClient = llmClient;
        this.repository = repository;
    }

    public CopilotQueryResponse ask(String question) {
        String modelResponse = llmClient.generateSql(question);
        String rawSql = extractSql(modelResponse);
        String sql = SqlValidator.validateAndLimit(rawSql);

        // A syntactically valid SELECT can still reference a column or table that does not exist,
        // or PostgreSQL can reject it for another reason: an expected outcome of the model getting
        // it wrong, not a system failure, so it is a 400, never a generic 500.
        CopilotQueryResult result;
        try {
            result = repository.execute(sql);
        } catch (DataAccessException e) {
            throw new BadRequestException("O SQL gerado pelo copiloto não é válido. Tente reformular a pergunta.");
        }
        return new CopilotQueryResponse(question, sql, result.columns(), result.rows(), result.rows().size());
    }

    /** Prefers a ```sql fenced block, then any fenced block, then the whole response, always trimmed. */
    static String extractSql(String modelResponse) {
        if (modelResponse == null) {
            return "";
        }
        Matcher sqlFence = SQL_FENCE.matcher(modelResponse);
        if (sqlFence.find()) {
            return sqlFence.group(1).trim();
        }
        Matcher anyFence = ANY_FENCE.matcher(modelResponse);
        if (anyFence.find()) {
            return anyFence.group(1).trim();
        }
        return modelResponse.trim();
    }
}