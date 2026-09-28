package com.metalcor.procurement.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Key numbers for the manager panel. Each one carries its own SQL, since they come from different queries")
public record DashboardKpisDto(
        @Schema(description = "Sum of purchase_order_items.line_total, all orders") DashboardResult<BigDecimal> totalSpend,
        @Schema(description = "Number of purchase orders") DashboardResult<Long> orders,
        @Schema(description = "Invoices with an open match exception (vw_invoice_match.resolution = 'open')")
        DashboardResult<Long> openExceptionInvoices,
        @Schema(description = "Sum of gross_amount of those open-exception invoices") DashboardResult<BigDecimal> blockedAmount,
        @Schema(description = "Weighted average of vw_supplier_scorecard.on_time_delivery_pct, weighted by orders")
        DashboardResult<BigDecimal> overallOnTimeDeliveryPct) {
}