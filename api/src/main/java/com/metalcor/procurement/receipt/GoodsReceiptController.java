package com.metalcor.procurement.receipt;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/goods-receipts")
@Tag(name = "Goods Receipts")
public class GoodsReceiptController {

    private final ReceiptService service;

    public GoodsReceiptController(ReceiptService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public GoodsReceiptResponse get(@PathVariable long id) {
        return service.get(id);
    }
}
