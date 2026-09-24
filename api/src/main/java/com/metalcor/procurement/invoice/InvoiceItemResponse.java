package com.metalcor.procurement.invoice;

import com.metalcor.procurement.requisition.MaterialRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Supplier invoice line item, with the three-way match outcome of that line (vw_invoice_line_match).")
public record InvoiceItemResponse(
        MaterialRef material,
        BigDecimal quantityInvoiced,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        boolean priceException,
        boolean quantityException) {
}