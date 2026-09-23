package com.metalcor.procurement.receipt;

import com.metalcor.procurement.requisition.MaterialRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Goods receipt line item.")
public record GoodsReceiptItemResponse(
        MaterialRef material,
        BigDecimal quantityReceived) {
}
