package com.metalcor.procurement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** X-User-Id on reads, roles on the remaining writes, and input size limits. */
class SecurityHardeningTest extends AbstractIntegrationTest {

    private static final String REQUESTER_ID = "1";
    private static final String BUYER_ID = "8";
    private static final String FINANCE_ID = "12";

    @Test
    void readsWithoutUserHeaderAreUnauthorized() throws Exception {
        // DefaultUserHeaderConfig adds a default header, so an explicit blank one stands for "missing".
        for (String url : new String[] {"/api/v1/dashboard/kpis", "/api/v1/dashboard/export.xlsx",
                "/api/v1/suppliers/scorecard", "/api/v1/invoices/1", "/api/v1/purchase-orders/1"}) {
            mockMvc.perform(get(url).header("X-User-Id", ""))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void readsWithUnknownUserAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/kpis").header("X-User-Id", "999999"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyRequestersCreateAndSubmitRequisitions() throws Exception {
        String body = """
                {"plantId": 1, "costCenterId": 1, "neededBy": "2026-10-15",
                 "items": [{"materialId": 1, "quantity": 1, "unitOfMeasureId": 1, "estimatedUnitPrice": 10}]}
                """;
        mockMvc.perform(post("/api/v1/requisitions").header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/requisitions/1/submit").header("X-User-Id", FINANCE_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyRequestersRegisterGoodsReceipts() throws Exception {
        mockMvc.perform(post("/api/v1/purchase-orders/1/receipts").header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [{\"purchaseOrderItemId\": 1, \"quantityReceived\": 1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tooManyReceiptOrInvoiceItemsAreRejected() throws Exception {
        String receiptItems = "{\"purchaseOrderItemId\": 1, \"quantityReceived\": 1},".repeat(9);
        mockMvc.perform(post("/api/v1/purchase-orders/1/receipts").header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\": [" + receiptItems.substring(0, receiptItems.length() - 1) + "]}"))
                .andExpect(status().isBadRequest());

        String invoiceItems = "{\"purchaseOrderItemId\": 1, \"quantityInvoiced\": 1, \"unitPrice\": 1},".repeat(9);
        mockMvc.perform(post("/api/v1/purchase-orders/1/invoices").header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierInvoiceNumber\": \"X\", \"invoiceDate\": \"2026-09-01\", \"items\": ["
                                + invoiceItems.substring(0, invoiceItems.length() - 1) + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedTextFieldsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/purchase-orders/1/receipts").header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deliveryNoteNumber\": \"" + "9".repeat(31)
                                + "\", \"items\": [{\"purchaseOrderItemId\": 1, \"quantityReceived\": 1}]}"))
                .andExpect(status().isBadRequest());
    }
}
