package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Material reference.")
public record MaterialRef(Long id, String code, String description) {
}