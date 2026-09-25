package com.metalcor.procurement.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to pay a matched or approved invoice. The payment is posted immediately.")
public record PaymentRequest(

        @Schema(description = "bank_transfer, boleto or pix.")
        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "bank_transfer|boleto|pix", message = "must be bank_transfer, boleto or pix")
        String paymentMethod,

        @Schema(description = "External reference (bank authentication code, boleto line, Pix id). Optional.")
        @Size(max = 60, message = "must be at most 60 characters")
        String reference) {
}
