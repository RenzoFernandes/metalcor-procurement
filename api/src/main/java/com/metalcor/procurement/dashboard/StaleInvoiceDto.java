package com.metalcor.procurement.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "A blocked invoice open for more than 45 days due to a match exception (vw_stale_blocked_invoices)")
public record StaleInvoiceDto(
        @Schema(description = "Internal invoice document number") String invoiceNumber,
        @Schema(description = "Gross amount of the invoice") BigDecimal amount,
        @Schema(description = "demo_as_of_date() minus posting_date") long ageDays,
        @Schema(description = "Age band: 0-15, 16-30, 31-45, 46-90 or 90+ days") String ageBand,
        @Schema(description = "Reason the invoice was blocked") String blockReason) {
}