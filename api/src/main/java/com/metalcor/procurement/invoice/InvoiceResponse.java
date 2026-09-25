package com.metalcor.procurement.invoice;

import com.metalcor.procurement.payment.PaymentRef;
import com.metalcor.procurement.requisition.SupplierRef;
import com.metalcor.procurement.requisition.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Supplier invoice, header and items, with the three-way match outcome.")
public record InvoiceResponse(
        Long id,
        String documentNumber,
        String purchaseOrderNumber,
        SupplierRef supplier,
        String supplierInvoiceNumber,
        LocalDate invoiceDate,
        LocalDate dueDate,
        LocalDate postingDate,
        BigDecimal grossAmount,
        @Schema(description = "received, matched, blocked, approved, paid or cancelled") String status,
        @Schema(description = "Why the invoice is blocked. Only when status is blocked.") String blockReason,
        @Schema(description = "User who approved the invoice. Only when status is approved or paid.") UserRef approvedBy,
        OffsetDateTime approvedAt,
        List<InvoiceItemResponse> items,
        @Schema(description = "Payment of this invoice, if any.") PaymentRef payment) {
}