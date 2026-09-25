package com.metalcor.procurement.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Payment associated with an invoice, embedded in the invoice response.")
public record PaymentRef(
        Long id,
        String documentNumber,
        BigDecimal amount,
        LocalDate scheduledFor,
        String paymentMethod,
        @Schema(description = "scheduled, paid or cancelled") String status,
        OffsetDateTime paidAt) {
}