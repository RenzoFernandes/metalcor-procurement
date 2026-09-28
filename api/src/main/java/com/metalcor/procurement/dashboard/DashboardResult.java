package com.metalcor.procurement.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Query result plus the exact SQL that produced it, for the technical mode")
public record DashboardResult<T>(
        @Schema(description = "Result of the query") T data,
        @Schema(description = "Exact SELECT/WITH statement executed, read-only") String sql) {
}
