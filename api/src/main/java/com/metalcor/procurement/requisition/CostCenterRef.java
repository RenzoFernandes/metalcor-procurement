package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cost center reference.")
public record CostCenterRef(Long id, String code, String name) {
}