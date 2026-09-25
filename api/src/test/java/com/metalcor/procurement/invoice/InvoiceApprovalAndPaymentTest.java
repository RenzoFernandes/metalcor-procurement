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
 * (cheapest, no supplier suggested). Default match tolerances (V2 seed): 2% price, 5% quantity.
 */
@Order(5)
class InvoiceApprovalAndPaymentTest extends AbstractIntegrationTest {

    private static final String REQUESTER_ID = "1";
    private static final String BUYER_ID = "8";
    private static final String RECEIVER_ID = "2";
    private static final String FINANCE_ID = "12";
    private static final String PO_UNIT_PRICE = "6.4172";
    private static final String OVER_TOLERANCE_UNIT_PRICE = "6.7381";

    @Autowired
    JdbcClient jdbc;

    @Test
    void approvingBlockedInvoiceSetsApprovedByAndApprovedAt() throws Exception {
        long invoiceId = createBlockedInvoice("NF-A-0001");

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/approve")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\": \"Preço aceito pelo comprador\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approvedBy.id").value(12))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty())
                .andExpect(jsonPath("$.blockReason").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())));
    }

    @Test
    void approvingNonBlockedInvoiceReturnsConflict() throws Exception {
        long invoiceId = createMatchedInvoice("NF-A-0002");

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/approve")
                        .header("X-User-Id", FINANCE_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void approvingWithoutFinanceRoleReturnsForbidden() throws Exception {
        long invoiceId = createBlockedInvoice("NF-A-0003");

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/approve")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void payingMatchedInvoiceCreatesPaymentAndClosesOrder() throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        long invoiceId = createInvoice(orderId, itemId, "NF-P-0001", "2026-09-01", null, "10", PO_UNIT_PRICE);

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", "PIX-TEST-001")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceStatus").value("paid"))
                .andExpect(jsonPath("$.purchaseOrderStatus").value("closed"))
                .andExpect(jsonPath("$.payment.status").value("paid"))
                .andExpect(jsonPath("$.payment.paymentMethod").value("pix"))
                .andExpect(jsonPath("$.payment.paidAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/purchase-orders/" + orderId))
                .andExpect(jsonPath("$.status").value("closed"));
        mockMvc.perform(get("/api/v1/invoices/" + invoiceId))
                .andExpect(jsonPath("$.status").value("paid"))
                .andExpect(jsonPath("$.payment.status").value("paid"))
                // Never went through /approve (was matched, not blocked): the payer becomes the approver,
                // satisfying ck_invoice_receipts_approved (approved_by/approved_at required when paid).
                .andExpect(jsonPath("$.approvedBy.id").value(12))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());
    }

    @Test
    void payingApprovedInvoiceAlsoWorks() throws Exception {
        long invoiceId = createBlockedInvoice("NF-P-0002");
        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/approve")
                        .header("X-User-Id", FINANCE_ID))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("boleto", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceStatus").value("paid"));
    }

    @Test
    void payingBlockedInvoiceWithoutApprovalReturnsConflict() throws Exception {
        long invoiceId = createBlockedInvoice("NF-P-0003");

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void payingAlreadyPaidInvoiceReturnsConflict() throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        long invoiceId = createInvoice(orderId, itemId, "NF-P-0004", "2026-09-01", null, "10", PO_UNIT_PRICE);

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void payingWithoutFinanceRoleReturnsForbidden() throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        long invoiceId = createInvoice(orderId, itemId, "NF-P-0005", "2026-09-01", null, "10", PO_UNIT_PRICE);

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void scheduledForMovesFromWeekendDueDateToMonday() throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        // 2026-09-19 is a Saturday; the next business day is Monday 2026-09-21.
        long invoiceId = createInvoice(orderId, itemId, "NF-P-0006", "2026-09-01", "2026-09-19", "10", PO_UNIT_PRICE);

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment.scheduledFor").value("2026-09-21"));
    }

    @Test
    void approvalAndPaymentAreRecordedInAuditLogWithFinanceUser() throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        long invoiceId = createInvoice(orderId, itemId, "NF-P-0007", "2026-09-01", null, "10", PO_UNIT_PRICE);

        mockMvc.perform(post("/api/v1/invoices/" + invoiceId + "/pay")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("pix", null)))
                .andExpect(status().isCreated());

        Long invoiceChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'invoice_receipts' AND record_id = :id
                           AND operation = 'UPDATE'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .param("id", invoiceId)
                .query(Long.class).single();
        assertThat(invoiceChangedBy).isEqualTo(12L);

        Long paymentChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'payments' AND operation = 'INSERT'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .query(Long.class).single();
        assertThat(paymentChangedBy).isEqualTo(12L);

        Long orderChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'purchase_orders' AND record_id = :id
                           AND operation = 'UPDATE'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .param("id", orderId)
                .query(Long.class).single();
        assertThat(orderChangedBy).isEqualTo(12L);
    }

    @Test
    void getUnknownPaymentReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/payments/999999"))
                .andExpect(status().isNotFound());
    }

    private long createBlockedInvoice(String supplierInvoiceNumber) throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        return createInvoice(orderId, itemId, supplierInvoiceNumber, "2026-09-01", null, "10", OVER_TOLERANCE_UNIT_PRICE);
    }

    private long createMatchedInvoice(String supplierInvoiceNumber) throws Exception {
        long orderId = issueAndFullyReceiveOrder();
        long itemId = firstItemId(orderId);
        return createInvoice(orderId, itemId, supplierInvoiceNumber, "2026-09-01", null, "10", PO_UNIT_PRICE);
    }

    private long createInvoice(long orderId, long itemId, String supplierInvoiceNumber, String invoiceDate,
            String dueDate, String quantity, String unitPrice) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/invoices")
                        .header("X-User-Id", FINANCE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody(supplierInvoiceNumber, invoiceDate, dueDate, itemId, quantity, unitPrice)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
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

    private static String paymentBody(String paymentMethod, String reference) {
        String referenceField = reference == null ? "" : "\"reference\": \"" + reference + "\",";
        return """
                {
                  %s
                  "paymentMethod": "%s"
                }
                """.formatted(referenceField, paymentMethod);
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

    private long approveRequisition() throws Exception {
        long id = createRequisition(requisitionBody(10));
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

    private long issueSingleItemOrder() throws Exception {
        long requisitionId = approveRequisition();

        MvcResult result = mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isCreated())
                .andReturn();
        Number orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orders[0].id");
        return orderId.longValue();
    }

    private long issueAndFullyReceiveOrder() throws Exception {
        long orderId = issueSingleItemOrder();
        long itemId = firstItemId(orderId);
        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "10")))
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
