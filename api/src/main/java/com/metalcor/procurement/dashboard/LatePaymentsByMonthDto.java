package com.metalcor.procurement.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "vw_payment_timeliness grouped by the month of the due date")
public record LatePaymentsByMonthDto(
        @Schema(description = "First day of the due_date month") LocalDate dueMonth,
        @Schema(description = "All payments due in the month") long payments,
        @Schema(description = "Of those, how many were scheduled after the due business day") long latePayments,
        @Schema(description = "Average days_late over the late payments only. Null when none were late") BigDecimal avgDaysLate) {
}