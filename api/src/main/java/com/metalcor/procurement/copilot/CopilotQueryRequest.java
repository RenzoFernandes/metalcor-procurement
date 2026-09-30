package com.metalcor.procurement.copilot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CopilotQueryRequest(
        @NotBlank
        @Size(max = 2000, message = "must be at most 2000 characters")
        String pergunta) {
}
