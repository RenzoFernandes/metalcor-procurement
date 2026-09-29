package com.metalcor.procurement.copilot;

import java.util.List;
import java.util.Map;

public record CopilotQueryResponse(
        String pergunta,
        String sql,
        List<String> colunas,
        List<Map<String, Object>> linhas,
        int totalLinhas) {
}