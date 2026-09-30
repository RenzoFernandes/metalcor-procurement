package com.metalcor.procurement.receipt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Request to register a goods receipt against a purchase order.")
public record ReceiptRequest(

        @Schema(description = "Supplier delivery note number, if any.")
        @Size(max = 30, message = "must be at most 30 characters")
        String deliveryNoteNumber,

        @Schema(description = "Line items received.")
        @NotEmpty(message = "must contain at least one item")
        @Size(max = 8, message = "must contain at most 8 items")
        @Valid
        List<ReceiptItemRequest> items) {
}
