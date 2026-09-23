package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Approval decision for a requisition pending approval.")
public record DecisionRequest(

        @Schema(description = "approve or reject")
        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "approve|reject", message = "must be approve or reject")
        String decision,

        @Schema(description = "Justification. Required when decision is reject.")
        String comment) {
}