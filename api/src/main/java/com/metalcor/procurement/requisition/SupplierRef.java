package com.metalcor.procurement.requisition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Supplier reference.")
public record SupplierRef(Long id, String code, String name) {
}