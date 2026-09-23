package com.metalcor.procurement.order;

import com.metalcor.procurement.requisition.MaterialRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Purchase order line item.")
public record PurchaseOrderItemResponse(
        Long id,
        MaterialRef material,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
