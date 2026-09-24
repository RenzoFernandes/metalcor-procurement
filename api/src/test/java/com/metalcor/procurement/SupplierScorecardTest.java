package com.metalcor.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(0)
class SupplierScorecardTest extends AbstractIntegrationTest {

    @Test
    void scorecardReturnsTwelveSuppliers() throws Exception {
        mockMvc.perform(get("/api/v1/suppliers/scorecard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].supplierCode").isNotEmpty())
                .andExpect(jsonPath("$[0].totalSpend").isNumber());
    }
}
