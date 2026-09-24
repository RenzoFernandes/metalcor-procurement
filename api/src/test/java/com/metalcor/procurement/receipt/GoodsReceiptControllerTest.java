package com.metalcor.procurement.receipt;

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
 * Seed users (db/seed/01_master_data.sql): 1 = requester (plant 1), 2 = requester (plant 1, used
 * here as the receiver, any active user can receive), 8 = buyer (plant 1).
 * Seed prices (db/seed/01_master_data.sql): material 1 (ACO-001) is sold by supplier 2 at 6.4172
 * (cheapest, no supplier suggested); material 6 (ROL-001) by supplier 4 at 26.4882 (cheapest).
 */
@Order(3)
class GoodsReceiptControllerTest extends AbstractIntegrationTest {

    private static final String REQUESTER_ID = "1";
    private static final String BUYER_ID = "8";
    private static final String RECEIVER_ID = "2";

    @Autowired
    JdbcClient jdbc;

    @Test
    void receivingFullQuantityMarksOrderReceived() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "10")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseOrderStatus").value("received"))
                .andExpect(jsonPath("$.goodsReceipt.status").value("posted"))
                .andExpect(jsonPath("$.goodsReceipt.items[0].quantityReceived").value(10))
                .andExpect(jsonPath("$.goodsReceipt.receivedBy.id").value(2));

        mockMvc.perform(get("/api/v1/purchase-orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("received"));
    }

    @Test
    void partialThenCompletingReceiptTransitionsThroughPartiallyReceived() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseOrderStatus").value("partially_received"));

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purchaseOrderStatus").value("received"));

        Long receiptCount = jdbc.sql("SELECT count(*) FROM goods_receipts WHERE purchase_order_id = :id")
                .param("id", orderId).query(Long.class).single();
        assertThat(receiptCount).isEqualTo(2L);
    }

    @Test
    void receivingMoreThanRemainingReturnsBadRequestAndCreatesNothing() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "12")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("10")));

        Long receiptCount = jdbc.sql("SELECT count(*) FROM goods_receipts WHERE purchase_order_id = :id")
                .param("id", orderId).query(Long.class).single();
        assertThat(receiptCount).isEqualTo(0L);
        mockMvc.perform(get("/api/v1/purchase-orders/" + orderId))
                .andExpect(jsonPath("$.status").value("issued"));
    }

    @Test
    void itemFromAnotherOrderReturnsBadRequest() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long otherOrderId = issueSingleItemOrder(6, 3, 10);
        long otherItemId = firstItemId(otherOrderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(otherItemId, "5")))
                .andExpect(status().isBadRequest());

        Long receiptCount = jdbc.sql("SELECT count(*) FROM goods_receipts WHERE purchase_order_id = :id")
                .param("id", orderId).query(Long.class).single();
        assertThat(receiptCount).isEqualTo(0L);
    }

    @Test
    void receivingAgainstClosedOrderReturnsConflict() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long itemId = firstItemId(orderId);
        jdbc.sql("UPDATE purchase_orders SET status = 'closed' WHERE id = :id").param("id", orderId).update();

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "5")))
                .andExpect(status().isConflict());
    }

    @Test
    void receivingWithoutUserHeaderReturnsUnauthorized() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "5")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receiptIsRecordedInAuditLogWithReceiverForReceiptAndOrderUpdate() throws Exception {
        long orderId = issueSingleItemOrder(1, 1, 10);
        long itemId = firstItemId(orderId);

        mockMvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receipts")
                        .header("X-User-Id", RECEIVER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(receiptBody(itemId, "10")))
                .andExpect(status().isCreated());

        Long receiptChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'goods_receipts' AND operation = 'INSERT'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .query(Long.class).single();
        assertThat(receiptChangedBy).isEqualTo(2L);

        Long orderChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'purchase_orders' AND record_id = :id
                           AND operation = 'UPDATE'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .param("id", orderId)
                .query(Long.class).single();
        assertThat(orderChangedBy).isEqualTo(2L);
    }

    @Test
    void getUnknownGoodsReceiptReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/goods-receipts/999999"))
                .andExpect(status().isNotFound());
    }

    private static String receiptBody(long itemId, String quantity) {
        return """
                {
                  "deliveryNoteNumber": "DANFE-TEST-001",
                  "items": [{"purchaseOrderItemId": %d, "quantityReceived": %s}]
                }
                """.formatted(itemId, quantity);
    }

    private static String requisitionBody(String itemsJson) {
        return """
                {
                  "plantId": 1,
                  "costCenterId": 1,
                  "neededBy": "2026-10-15",
                  "items": [%s]
                }
                """.formatted(itemsJson);
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

    private long approveRequisition(String body) throws Exception {
        long id = createRequisition(body);
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

    private long issueSingleItemOrder(long materialId, long unitOfMeasureId, int quantity) throws Exception {
        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": " + materialId + ", \"quantity\": " + quantity
                        + ", \"unitOfMeasureId\": " + unitOfMeasureId + ", \"estimatedUnitPrice\": 50}"));

        MvcResult result = mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isCreated())
                .andReturn();
        Number orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orders[0].id");
        return orderId.longValue();
    }

    private long firstItemId(long orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/purchase-orders/" + orderId))
                .andExpect(status().isOk())
                .andReturn();
        Number itemId = JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].id");
        return itemId.longValue();
    }
}
