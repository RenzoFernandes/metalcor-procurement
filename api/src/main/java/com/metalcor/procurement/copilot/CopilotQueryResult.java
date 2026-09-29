package com.metalcor.procurement.copilot;

import java.util.List;
import java.util.Map;

record CopilotQueryResult(List<String> columns, List<Map<String, Object>> rows) {
}