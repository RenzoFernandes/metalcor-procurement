package com.metalcor.procurement.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "One invoice with a three-way match exception (vw_invoice_match where has_exception)")
public record InvoiceExceptionDto(
        @Schema(description = "Internal invoice document number") String invoiceNumber,
        long supplierId,
        String supplierName,
        @Schema(description = "Purchase order document number") String poNumber,
        LocalDate invoiceDate,
        LocalDate postingDate,
        LocalDate dueDate,
        BigDecimal grossAmount,
        @Schema(description = "received, matched, blocked, approved, paid or cancelled") String invoiceStatus,
        @Schema(description = "Largest absolute price variance among the lines, in %") BigDecimal maxAbsPriceVariancePct,
        @Schema(description = "Largest quantity variance among the lines, in %. Null when nothing was received")
        BigDecimal maxQuantityVariancePct,
        boolean priceException,
        boolean quantityException,
        boolean invoiceBeforeReceipt,
        @Schema(description = "price_variance, quantity_variance and/or invoice_before_receipt") List<String> exceptionTypes,
        @Schema(description = "open, released or cancelled") String resolution,
        @Schema(description = "Name of the approver. Only when released") String approvedBy,
        OffsetDateTime approvedAt,
        @Schema(description = "Days from posting to approval. Only when released") Integer daysToResolve,
        String blockReason,
        @Schema(description = "Days since posting, as of the demo date. Only when open") Integer ageDays,
        @Schema(description = "Open for more than 45 days") boolean stale) {
}
