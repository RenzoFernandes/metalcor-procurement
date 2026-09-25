package com.metalcor.procurement.invoice;

import com.metalcor.procurement.common.PageResponse;
import com.metalcor.procurement.payment.PaymentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private static final String USER_HEADER_DESCRIPTION =
            "Id of the acting user (app_users.id), must be active. Provisional auth until login exists.";

    private final InvoiceRepository invoices;
    private final InvoiceService service;

    public InvoiceController(InvoiceRepository invoices, InvoiceService service) {
        this.invoices = invoices;
        this.service = service;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a supplier invoice by id, with the three-way match outcome of each line")
    public InvoiceResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a blocked invoice despite its three-way match exception",
            parameters = @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = USER_HEADER_DESCRIPTION))
    public InvoiceResponse approve(@PathVariable long id, @RequestBody(required = false) ApprovalRequest request) {
        return service.approve(id, request);
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Pay a matched or approved invoice, closing its purchase order",
            parameters = @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = USER_HEADER_DESCRIPTION))
    public ResponseEntity<PayInvoiceResponse> pay(@PathVariable long id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.pay(id, request));
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
