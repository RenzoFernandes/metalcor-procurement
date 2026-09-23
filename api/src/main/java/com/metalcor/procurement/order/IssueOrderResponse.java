package com.metalcor.procurement.order;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Purchase orders issued from a requisition, and the requisition's new status.")
public record IssueOrderResponse(
        List<PurchaseOrderResponse> orders,
        String requisitionStatus) {
}
