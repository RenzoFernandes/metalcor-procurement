package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Unit of measure reference.")
public record UnitOfMeasureRef(Long id, String code) {
}