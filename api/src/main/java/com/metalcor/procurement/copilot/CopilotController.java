package com.metalcor.procurement.copilot;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Natural-language questions answered with a read-only SQL query, run as metalcor_readonly.
 * A write endpoint, so CurrentUserInterceptor already requires a valid X-User-Id; any active user
 * is accepted, with no role restriction.
 */
@RestController
@RequestMapping("/api/v1/copilot")
@Tag(name = "Copilot")
public class CopilotController {

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
    }

    @PostMapping("/query")
    @Operation(summary = "Ask the SQL copilot a question in natural language",
            description = "Sends the question to a local Ollama model, validates that it answered with a single "
                    + "read-only SELECT, runs it as metalcor_readonly and returns the rows.")
    public CopilotQueryResponse query(@Valid @RequestBody CopilotQueryRequest request) {
        return copilotService.ask(request.pergunta());
    }
}