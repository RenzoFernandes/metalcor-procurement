package com.metalcor.procurement.supplier;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "One row of vw_supplier_scorecard")
public record SupplierScorecardDto(
        @Schema(description = "Supplier code", example = "SUP-001") String supplierCode,
        @Schema(description = "Supplier name (fictional)") String supplierName,
        @Schema(description = "Purchase orders not in draft or cancelled status") long orders,
        @Schema(description = "Sum of the order line totals of those orders") BigDecimal totalSpend,
        @Schema(description = "Percentage of delivered orders received on time (1 business day of grace). Null when nothing was delivered")
        BigDecimal onTimeDeliveryPct,
        @Schema(description = "Average calendar days late, over late deliveries only") BigDecimal avgDaysLate,
        @Schema(description = "All invoices of the supplier, including cancelled ones") long invoices,
        @Schema(description = "Percentage of invoices with a match exception or flagged as duplicate")
        BigDecimal exceptionRatePct,
        @Schema(description = "Average days from posting to approval of released exceptions") BigDecimal avgDaysToResolve,
        @Schema(description = "Invoices with an exception still open") long openExceptions) {
}
