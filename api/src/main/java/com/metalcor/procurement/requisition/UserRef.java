package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "App user reference (no email: same restriction as metalcor_readonly).")
public record UserRef(Long id, String name, String role) {
}