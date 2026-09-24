package com.metalcor.procurement.invoice;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request to register a supplier invoice against a purchase order, with automatic three-way match.")
public record InvoiceRequest(

        @Schema(description = "Invoice number as printed by the supplier. Unique per supplier.")
        @NotBlank(message = "must not be blank")
        String supplierInvoiceNumber,

        @Schema(description = "Date on the supplier invoice.")
        @NotNull(message = "must not be null")
        LocalDate invoiceDate,

        @Schema(description = "Payment due date. When absent, defaults to invoiceDate plus the supplier's payment terms.")
        LocalDate dueDate,

        @Schema(description = "Line items billed.")
        @NotEmpty(message = "must contain at least one item")
        @Valid
        List<InvoiceItemRequest> items) {
}