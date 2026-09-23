package com.metalcor.procurement.order;

import com.metalcor.procurement.requisition.PlantRef;
import com.metalcor.procurement.requisition.SupplierRef;
import com.metalcor.procurement.requisition.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Purchase order, header and items.")
public record PurchaseOrderResponse(
        Long id,
        String documentNumber,
        SupplierRef supplier,
        @Schema(description = "Document number of the source requisition, if any.") String requisitionNumber,
        PlantRef plant,
        UserRef buyer,
        LocalDate orderDate,
        LocalDate expectedDeliveryDate,
        Integer paymentTermsDays,
        String status,
        List<PurchaseOrderItemResponse> items,
        @Schema(description = "Sum of line_total across items.") BigDecimal total) {
}
