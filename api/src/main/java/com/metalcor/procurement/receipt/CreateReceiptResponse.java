package com.metalcor.procurement.receipt;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Goods receipt just created, and the purchase order's new status.")
public record CreateReceiptResponse(
        GoodsReceiptResponse goodsReceipt,
        String purchaseOrderStatus) {
}
