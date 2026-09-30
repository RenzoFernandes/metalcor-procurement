package com.metalcor.procurement.requisition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Approval decision for a requisition pending approval.")
public record DecisionRequest(

        @Schema(description = "approve or reject")
        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "approve|reject", message = "must be approve or reject")
        String decision,

        @Schema(description = "Justification. Required when decision is reject.")
        @Size(max = 500, message = "must be at most 500 characters")
        String comment) {

    @JsonIgnore
    @AssertTrue(message = "comment is required when decision is reject")
    public boolean isCommentProvidedWhenRejecting() {
        return !"reject".equals(decision) || (comment != null && !comment.isBlank());
    }
}
