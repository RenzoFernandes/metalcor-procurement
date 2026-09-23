package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request to create a purchase requisition in draft status.")
public record CreateRequisitionRequest(

        @Schema(description = "Plant that needs the materials.")
        @NotNull(message = "must not be null")
        Long plantId,

        @Schema(description = "Cost center charged.")
        @NotNull(message = "must not be null")
        Long costCenterId,

        @Schema(description = "Date the materials are needed.")
        @NotNull(message = "must not be null")
        LocalDate neededBy,

        @Schema(description = "Free-text notes.")
        String notes,

        @Schema(description = "Line items, 1 to 8.")
        @NotEmpty(message = "must contain at least one item")
        @Size(max = 8, message = "must contain at most 8 items")
        @Valid
        List<CreateRequisitionItemRequest> items) {
}