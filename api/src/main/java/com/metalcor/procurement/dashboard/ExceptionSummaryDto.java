package com.metalcor.procurement.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "vw_match_exception_summary aggregated by exception type and resolution, across all months")
public record ExceptionSummaryDto(
        @Schema(description = "price_variance, quantity_variance or invoice_before_receipt") String exceptionType,
        @Schema(description = "open, released or cancelled") String resolution,
        @Schema(description = "Number of invoices") long invoiceCount,
        @Schema(description = "Sum of gross_amount of those invoices") BigDecimal grossAmount) {
}