package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Plant reference.")
public record PlantRef(Long id, String code, String name) {
}