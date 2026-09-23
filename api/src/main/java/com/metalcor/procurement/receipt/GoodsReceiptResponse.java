package com.metalcor.procurement.receipt;

import com.metalcor.procurement.requisition.PlantRef;
import com.metalcor.procurement.requisition.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Goods receipt, header and items.")
public record GoodsReceiptResponse(
        Long id,
        String documentNumber,
        String purchaseOrderNumber,
        PlantRef plant,
        UserRef receivedBy,
        LocalDate receiptDate,
        String deliveryNoteNumber,
        String status,
        List<GoodsReceiptItemResponse> items) {
}
