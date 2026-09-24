package com.metalcor.procurement.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "One supplier invoice line item.")
public record InvoiceItemRequest(

        @Schema(description = "Purchase order line being invoiced.")
        @NotNull(message = "must not be null")
        Long purchaseOrderItemId,

        @Schema(description = "Quantity billed by the supplier.")
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        BigDecimal quantityInvoiced,

        @Schema(description = "Unit price billed by the supplier.")
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        BigDecimal unitPrice) {
}