package com.metalcor.procurement.invoice;

import com.metalcor.procurement.payment.PaymentResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment just posted, and the invoice's and purchase order's new status.")
public record PayInvoiceResponse(
        PaymentResponse payment,
        String invoiceStatus,
        String purchaseOrderStatus) {
}