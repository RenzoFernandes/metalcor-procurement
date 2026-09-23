package com.metalcor.procurement.receipt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Request to register a goods receipt against a purchase order.")
public record ReceiptRequest(

        @Schema(description = "Supplier delivery note number, if any.")
        String deliveryNoteNumber,

        @Schema(description = "Line items received.")
        @NotEmpty(message = "must contain at least one item")
        @Valid
        List<ReceiptItemRequest> items) {
}
