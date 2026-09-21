package com.metalcor.procurement.supplier;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(name = "Suppliers")
public class SupplierController {

    private final SupplierRepository suppliers;

    public SupplierController(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping("/scorecard")
    @Operation(summary = "Supplier scorecard",
            description = "Spend, delivery punctuality and invoice exceptions per supplier (view vw_supplier_scorecard), ordered by total spend.")
    public List<SupplierScorecardDto> scorecard() {
        return suppliers.findScorecard();
    }
}
