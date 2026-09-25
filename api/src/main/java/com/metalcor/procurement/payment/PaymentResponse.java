package com.metalcor.procurement.payment;

import com.metalcor.procurement.requisition.UserRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Payment of a supplier invoice (accounts payable settlement).")
public record PaymentResponse(
        Long id,
        String documentNumber,
        String invoiceNumber,
        BigDecimal amount,
        LocalDate scheduledFor,
        String paymentMethod,
        @Schema(description = "scheduled, paid or cancelled") String status,
        OffsetDateTime paidAt,
        UserRef createdBy,
        String reference) {
}