package com.metalcor.procurement.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.metalcor.procurement.AbstractIntegrationTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Seed users (db/seed/01_master_data.sql): 1 = requester (plant 1), 2 = requester (used to receive),
 * 8 = buyer (plant 1), 12 = finance.
 * Seed prices (db/seed/01_master_data.sql): material 1 (ACO-001) is sold by supplier 2 at 6.4172
 * (cheapest, no supplier suggested). Supplier 2 payment terms: 30 days (db/seed/01_master_data.sql).
 * Default match tolerances (V2 seed): 2% price, 5% quantity.
 */
@Order(4)
class InvoiceControllerTest extends AbstractIntegrationTest {

    private static final String REQUESTER_ID = "1";
    private static final String BUYER_ID = "8";
    private static final String RECEIVER_ID = "2";
    private static final String FINANCE_ID = "12";
    private static final String PO_UNIT_PRICE = "6.4172";

    @Autowired
    JdbcClient jdbc;

    @Test
    void invoiceWithinToleranceIsMatchedAndDueDateDefaultsFromPaymentTerms() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0001", "2026-09-20", null, itemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("matched"))
                .andExpect(jsonPath("$.blockReason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dueDate").value("2026-10-20"))
                .andExpect(jsonPath("$.grossAmount").value(64.17))
                .andExpect(jsonPath("$.items[0].priceException").value(false))
                .andExpect(jsonPath("$.items[0].quantityException").value(false));
    }

    @Test
    void invoicePriceAboveToleranceIsBlockedWithPriceReason() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0002", "2026-09-20", null, itemId, "10", "6.7381")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.blockReason").value("Preço da fatura acima do pedido (tolerância de 2%)"))
                .andExpect(jsonPath("$.items[0].priceException").value(true))
                .andExpect(jsonPath("$.items[0].quantityException").value(false));
    }

    @Test
    void invoiceQuantityAboveReceivedIsBlockedWithQuantityReason() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0003", "2026-09-20", null, itemId, "11", PO_UNIT_PRICE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.blockReason").value("Quantidade faturada maior que a recebida (tolerância de 5%)"))
                .andExpect(jsonPath("$.items[0].priceException").value(false))
                .andExpect(jsonPath("$.items[0].quantityException").value(true));
    }

    @Test
    void invoiceWithBothExceptionsIsBlockedCitingBoth() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0004", "2026-09-20", null, itemId, "11", "6.7381")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("blocked"))
                .andExpect(jsonPath("$.blockReason").value(
                        "Preço da fatura acima do pedido (tolerância de 2%); Quantidade faturada maior que a recebida (tolerância de 5%)"));
    }

    @Test
    void itemFromAnotherOrderReturnsBadRequestAndCreatesNothing() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long otherOrderId = issueAndFullyReceiveOrder(10);
        long otherItemId = firstItemId(otherOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0005", "2026-09-20", null, otherItemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isBadRequest());

        Long invoiceCount = jdbc.sql("SELECT count(*) FROM invoice_receipts WHERE purchase_order_id = :id")
                .param("id", orderId).query(Long.class).single();
        assertThat(invoiceCount).isEqualTo(0L);
    }

    @Test
    void duplicateSupplierInvoiceNumberReturnsBadRequestWithoutLeakingDatabaseError() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);
        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-DUP-001", "2026-09-20", null, itemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isCreated());

        long otherOrderId = issueAndFullyReceiveOrder(10);
        long otherItemId = firstItemId(otherOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + otherOrderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-DUP-001", "2026-09-20", null, otherItemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("constraint"))));

        Long invoiceCount = jdbc.sql("SELECT count(*) FROM invoice_receipts WHERE purchase_order_id = :id")
                .param("id", otherOrderId).query(Long.class).single();
        assertThat(invoiceCount).isEqualTo(0L);
    }

    @Test
    void invoicingIssuedOrderReturnsConflict() throws Exception {
        long orderId = issueSingleItemOrder(10);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0006", "2026-09-20", null, firstItemId(orderId), "10", PO_UNIT_PRICE)))
                .andExpect(status().isConflict());
    }

    @Test
    void invoicingWithNonFinanceRoleReturnsForbidden() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0007", "2026-09-20", null, itemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isForbidden());

        Long invoiceCount = jdbc.sql("SELECT count(*) FROM invoice_receipts WHERE purchase_order_id = :id")
                .param("id", orderId).query(Long.class).single();
        assertThat(invoiceCount).isEqualTo(0L);
    }

    @Test
    void invoicingWithoutUserHeaderReturnsUnauthorized() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0008", "2026-09-20", null, itemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invoiceIsRecordedInAuditLogWithFinanceUser() throws Exception {
        long orderId = issueAndFullyReceiveOrder(10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("NF-0009", "2026-09-20", null, itemId, "10", PO_UNIT_PRICE)))
                .andExpect(status().isCreated());

        Long changedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'invoice_receipts' AND operation = 'INSERT'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .query(Long.class).single();
        assertThat(changedBy).isEqualTo(12L);
    }

    @Test
    void getUnknownInvoiceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/invoices/999999"))
                .andExpect(status().isNotFound());
    }

    private static String invoiceBody(String supplierInvoiceNumber, String invoiceDate, String dueDate,
            long itemId, String quantity, String unitPrice) {
        String dueDateField = dueDate == null ? "" : "\"dueDate\": \"" + dueDate + "\",";
        return """
                {
                  "supplierInvoiceNumber": "%s",
                  "invoiceDate": "%s",
                  %s
                  "items": [{"purchaseOrderItemId": %d, "quantityInvoiced": %s, "unitPrice": %s}]
                }
                """.formatted(supplierInvoiceNumber, invoiceDate, dueDateField, itemId, quantity, unitPrice);
    }

    private static String receiptBody(long itemId, String quantity) {
        return """
                {
                  "items": [{"purchaseOrderItemId": %d, "quantityReceived": %s}]
                }
                """.formatted(itemId, quantity);
    }

    private static String requisitionBody(int quantity) {
        return """
                {
                  "plantId": 1,
                  "costCenterId": 1,
                  "neededBy": "2026-10-15",
                  "items": [{"materialId": 1, "quantity": %d, "unitOfMeasureId": 1, "estimatedUnitPrice": 50}]
                }
                """.formatted(quantity);
    }

    private long createRequisition(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/requisitions")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long approveRequisition(int quantity) throws Exception {
        long id = createRequisition(requisitionBody(quantity));
        mockMvc.perform(post("/api/v1/requisitions/" + id + "/submit")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requisitions/" + id + "/decide")
                        .header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"approve\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private long issueSingleItemOrder(int quantity) throws Exception {
        long requisitionId = approveRequisition(quantity);

        MvcResult result = mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isCreated())
                .andReturn();
        Number orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orders[0].id");
        return orderId.longValue();
    }

    private long issueAndFullyReceiveOrder(int quantity) throws Exception {
        long orderId = issueSingleItemOrder(quantity);
        long itemId = firstItemId(orderId);
        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, String.valueOf(quantity))))
                .andExpect(status().isCreated());
        return orderId;
    }

    private long firstItemId(long orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/purchase-orders/" + orderId))
                .andExpect(status().isOk())
                .andReturn();
        Number itemId = JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
        return itemId.longValue();
    }
}
