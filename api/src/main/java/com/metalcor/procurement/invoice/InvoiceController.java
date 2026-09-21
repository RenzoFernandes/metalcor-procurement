package com.metalcor.procurement.invoice;

import com.metalcor.procurement.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceRepository invoices;

    public InvoiceController(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @GetMapping("/exceptions")
    @Operation(summary = "Invoices with three-way match exceptions",
            description = "Reads vw_invoice_match where has_exception. Ordered by posting date (newest first), then invoice number.")
    public PageResponse<InvoiceExceptionDto> exceptions(
            @Parameter(description = "open (still pending) or released (approved or paid despite the exception)")
            @RequestParam(name = "resolution", required = false)
            @Pattern(regexp = "open|released", message = "must be open or released")
            String resolution,

            @Parameter(description = "price_variance, quantity_variance or invoice_before_receipt")
            @RequestParam(name = "exceptionType", required = false)
            @Pattern(regexp = "price_variance|quantity_variance|invoice_before_receipt",
                    message = "must be price_variance, quantity_variance or invoice_before_receipt")
            String exceptionType,

            @Parameter(description = "Page number, starting at 0")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "must be 0 or greater")
            int page,

            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "must be between 1 and 100")
            @Max(value = 100, message = "must be between 1 and 100")
            int size) {
        return invoices.findExceptions(resolution, exceptionType, page, size);
    }
}
