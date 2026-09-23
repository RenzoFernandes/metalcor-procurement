package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Purchase requisition line item.")
public record RequisitionItemResponse(
        Long id,
        int lineNumber,
        MaterialRef material,
        BigDecimal quantity,
        UnitOfMeasureRef unitOfMeasure,
        BigDecimal estimatedUnitPrice,
        BigDecimal estimatedTotal,
        SupplierRef suggestedSupplier,
        LocalDate neededBy) {
}