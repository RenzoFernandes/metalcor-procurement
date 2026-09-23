package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Purchase requisition, header and items.")
public record RequisitionResponse(
        Long id,
        String documentNumber,
        String status,
        PlantRef plant,
        CostCenterRef costCenter,
        UserRef requestedBy,
        LocalDate neededBy,
        String notes,
        UserRef approvedBy,
        OffsetDateTime approvedAt,
        String rejectionReason,
        List<RequisitionItemResponse> items,
        @Schema(description = "Sum of estimated_total across items.") BigDecimal total) {
}