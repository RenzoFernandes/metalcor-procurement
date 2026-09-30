package com.metalcor.procurement.copilot;

import io.swagger.v3.oas.annotations.Operation;
import com.metalcor.procurement.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Natural-language questions answered with a read-only SQL query, run as metalcor_readonly.
 * CurrentUserInterceptor requires a valid X-User-Id; any active user is accepted, with no role
 * restriction, but each user is limited to a few questions per minute (CopilotRateLimiter).
 */
@RestController
@RequestMapping("/api/v1/copilot")
@Tag(name = "Copilot")
public class CopilotController {

    private final CopilotService copilotService;
    private final CopilotRateLimiter rateLimiter;
    private final CurrentUser currentUser;

    public CopilotController(CopilotService copilotService, CopilotRateLimiter rateLimiter, CurrentUser currentUser) {
        this.copilotService = copilotService;
        this.rateLimiter = rateLimiter;
        this.currentUser = currentUser;
    }

    @PostMapping("/query")
    @Operation(summary = "Ask the SQL copilot a question in natural language",
            description = "Sends the question to the configured model (local Ollama or Google Gemini), validates that it answered with a single "
                    + "read-only SELECT, runs it as metalcor_readonly and returns the rows.")
    public CopilotQueryResponse query(@Valid @RequestBody CopilotQueryRequest request) {
        rateLimiter.acquire(currentUser.id());
        return copilotService.ask(request.pergunta());
    }
}