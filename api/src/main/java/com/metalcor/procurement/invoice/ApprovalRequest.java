package com.metalcor.procurement.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Approval of a blocked invoice, despite its three-way match exception. The whole body is optional.")
public record ApprovalRequest(

        @Schema(description = "Free-text justification for approving despite the exception. Optional.")
        @Size(max = 1000, message = "must be at most 1000 characters")
        String notes) {
}