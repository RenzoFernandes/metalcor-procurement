package com.metalcor.procurement.copilot;

import jakarta.validation.constraints.NotBlank;

public record CopilotQueryRequest(@NotBlank String pergunta) {
}