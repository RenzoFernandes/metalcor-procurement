package com.metalcor.procurement.order;

import com.metalcor.procurement.receipt.CreateReceiptResponse;
import com.metalcor.procurement.receipt.ReceiptRequest;
import com.metalcor.procurement.receipt.ReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Tag(name = "Purchase Orders")
public class PurchaseOrderController {

    private static final String USER_HEADER_DESCRIPTION =
            "Id of the acting user (app_users.id), must be active. Provisional auth until login exists.";

    private final PurchaseOrderService service;
    private final ReceiptService receiptService;

    public PurchaseOrderController(PurchaseOrderService service, ReceiptService receiptService) {
        this.service = service;
        this.receiptService = receiptService;
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/receipts")
    @Operation(summary = "Register a goods receipt against an issued or partially received purchase order",
            parameters = @Parameter(name = "X-User-Id", in = ParameterIn.HEADER, required = true, description = USER_HEADER_DESCRIPTION))
    public ResponseEntity<CreateReceiptResponse> receive(@PathVariable long id, @Valid @RequestBody ReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(receiptService.createReceipt(id, request));
    }
}
