package com.metalcor.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@Order(0)
class InvoiceExceptionsTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/invoices/exceptions";

    @Test
    void openExceptionsTotalEight() throws Exception {
        mockMvc.perform(get(URL).param("resolution", "open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.items.length()").value(8))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void releasedExceptionsTotal108() throws Exception {
        mockMvc.perform(get(URL).param("resolution", "released").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(108))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.totalPages").value(11));
    }

    @Test
    void priceVarianceTotal73() throws Exception {
        mockMvc.perform(get(URL).param("exceptionType", "price_variance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(73));
    }

    @Test
    void sizeAboveLimitIsRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(get(URL).param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void unknownResolutionIsRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(get(URL).param("resolution", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("resolution"));
    }

    @Test
    void pageBeyondTheEndReturnsEmptyListWithCorrectTotal() throws Exception {
        mockMvc.perform(get(URL).param("resolution", "open").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.page").value(99));
    }
}
