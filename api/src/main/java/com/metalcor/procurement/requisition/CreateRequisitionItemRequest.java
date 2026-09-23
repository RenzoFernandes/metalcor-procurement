package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "One requisition line item.")
public record CreateRequisitionItemRequest(

        @Schema(description = "Material requested.")
        @NotNull(message = "must not be null")
        Long materialId,

        @Schema(description = "Quantity requested.")
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        BigDecimal quantity,

        @Schema(description = "Unit of measure of the quantity.")
        @NotNull(message = "must not be null")
        Long unitOfMeasureId,

        @Schema(description = "Estimated price per unit.")
        @NotNull(message = "must not be null")
        @Positive(message = "must be positive")
        BigDecimal estimatedUnitPrice,

        @Schema(description = "Supplier suggested by the requester, if any.")
        Long suggestedSupplierId,

        @Schema(description = "Line-level need date, if different from the header.")
        LocalDate neededBy) {
}
