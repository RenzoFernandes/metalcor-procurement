package com.metalcor.procurement.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "One row of vw_spend_by_month_category")
public record SpendByMonthCategoryDto(
        @Schema(description = "First day of the order month") LocalDate orderMonth,
        @Schema(description = "Material category code", example = "ACO") String categoryCode,
        @Schema(description = "Material category name") String categoryName,
        @Schema(description = "Distinct purchase orders with at least one item of the category") long orders,
        @Schema(description = "Order lines of the category") long items,
        @Schema(description = "Sum of the line totals") BigDecimal totalValue,
        @Schema(description = "Distinct suppliers with orders in the month and category") long suppliers) {
}