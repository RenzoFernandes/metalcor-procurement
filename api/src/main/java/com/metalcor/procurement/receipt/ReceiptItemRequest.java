package com.metalcor.procurement.receipt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "One goods receipt line item.")
public record ReceiptItemRequest(

        @Schema(description = "Purchase order line being received.")
        @NotNull(message = "must not be null")
        Long purchaseOrderItemId,

        @Schema(description = "Quantity received now, in the order line unit of measure.")
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        BigDecimal quantityReceived) {
}
