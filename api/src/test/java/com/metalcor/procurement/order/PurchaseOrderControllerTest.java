package com.metalcor.procurement.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.metalcor.procurement.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Seed users (db/seed/01_master_data.sql): 1 = requester (plant 1), 8 = buyer (plant 1).
 * Seed prices (db/seed/01_master_data.sql, all currently valid): material 1 (ACO-001) is sold by
 * supplier 1 at 7.1136 and supplier 2 at 6.4172 (cheapest); material 6 (ROL-001) is sold by
 * supplier 3 at 51.6870 and supplier 4 at 26.4882 (cheapest). Requisition totals below stay under
 * 10,000, the buyer approval threshold (approval_rules).
 */
class PurchaseOrderControllerTest extends AbstractIntegrationTest {

    private static final String REQUESTER_ID = "1";
    private static final String BUYER_ID = "8";

    @Autowired
    JdbcClient jdbc;

    @Test
    void issueOrderPicksCheapestSupplierWhenNoneSuggested() throws Exception {
        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": 1, \"quantity\": 10, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50}"));

        MvcResult result = mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requisitionStatus").value("closed"))
                .andExpect(jsonPath("$.orders.length()").value(1))
                .andExpect(jsonPath("$.orders[0].supplier.id").value(2))
                .andExpect(jsonPath("$.orders[0].status").value("issued"))
                .andExpect(jsonPath("$.orders[0].items[0].unitPrice").value(6.4172))
                .andReturn();

        Number orderId = JsonPath.read(result.getResponse().getContentAsString(), "$.orders[0].id");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/purchase-orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requisitionNumber").exists())
                .andExpect(jsonPath("$.total").value(64.17));

        String requisitionStatus = jdbc.sql("SELECT status FROM purchase_requisitions WHERE id = :id")
                .param("id", requisitionId).query(String.class).single();
        assertThat(requisitionStatus).isEqualTo("closed");
    }

    @Test
    void issueOrderGroupsItemsByCategoryIntoTwoOrders() throws Exception {
        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": 1, \"quantity\": 10, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50},"
                        + "{\"materialId\": 6, \"quantity\": 10, \"unitOfMeasureId\": 3, \"estimatedUnitPrice\": 50}"));

        mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orders.length()").value(2));

        Long orderCount = jdbc.sql("SELECT count(*) FROM purchase_orders WHERE purchase_requisition_id = :id")
                .param("id", requisitionId).query(Long.class).single();
        assertThat(orderCount).isEqualTo(2L);
    }

    @Test
    void issueOrderFromDraftReturnsConflict() throws Exception {
        long requisitionId = createRequisition(requisitionBody(
                "{\"materialId\": 1, \"quantity\": 10, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50}"));

        mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void issueOrderWithNonBuyerReturnsForbidden() throws Exception {
        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": 1, \"quantity\": 10, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50}"));

        mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isForbidden());

        String requisitionStatus = jdbc.sql("SELECT status FROM purchase_requisitions WHERE id = :id")
                .param("id", requisitionId).query(String.class).single();
        assertThat(requisitionStatus).isEqualTo("approved");
    }

    @Test
    void issueOrderWithSuggestedSupplierWithoutCurrentPriceReturnsBadRequestAndCreatesNothing() throws Exception {
        // Supplier 3 does not sell material 1 (it sells materials 6-10).
        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": 1, \"quantity\": 10, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50, \"suggestedSupplierId\": 3}"));

        mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isBadRequest());

        Long orderCount = jdbc.sql("SELECT count(*) FROM purchase_orders WHERE purchase_requisition_id = :id")
                .param("id", requisitionId).query(Long.class).single();
        assertThat(orderCount).isEqualTo(0L);
        String requisitionStatus = jdbc.sql("SELECT status FROM purchase_requisitions WHERE id = :id")
                .param("id", requisitionId).query(String.class).single();
        assertThat(requisitionStatus).isEqualTo("approved");
    }

    @Test
    void issueOrderWithMaterialWithoutAnySupplierReturnsBadRequestAndCreatesNothing() throws Exception {
        long materialId = jdbc.sql("""
                        INSERT INTO materials (code, description, material_category_id, unit_of_measure_id, standard_price)
                        VALUES ('TEST-NOSUP', 'Material sem fornecedor (test)', 1, 1, 10.0000)
                        RETURNING id
                        """)
                .query(Long.class).single();

        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": " + materialId + ", \"quantity\": 5, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50}"));

        mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("TEST-NOSUP")));

        Long orderCount = jdbc.sql("SELECT count(*) FROM purchase_orders WHERE purchase_requisition_id = :id")
                .param("id", requisitionId).query(Long.class).single();
        assertThat(orderCount).isEqualTo(0L);
    }

    @Test
    void issueOrderIsRecordedInAuditLogWithBuyerForOrderAndRequisitionClose() throws Exception {
        long requisitionId = approveRequisition(requisitionBody(
                "{\"materialId\": 1, \"quantity\": 10, \"unitOfMeasureId\": 1, \"estimatedUnitPrice\": 50}"));

        mockMvc.perform(post("/api/v1/requisitions/" + requisitionId + "/issue-order")
                        .header("X-User-Id", BUYER_ID))
                .andExpect(status().isCreated());

        Long orderChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'purchase_orders' AND operation = 'INSERT'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .query(Long.class).single();
        assertThat(orderChangedBy).isEqualTo(8L);

        Long requisitionChangedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'purchase_requisitions' AND record_id = :id
                           AND operation = 'UPDATE'
                         ORDER BY changed_at DESC LIMIT 1
                        """)
                .param("id", requisitionId)
                .query(Long.class).single();
        assertThat(requisitionChangedBy).isEqualTo(8L);
    }

    @Test
    void getUnknownPurchaseOrderReturnsNotFound() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/purchase-orders/999999"))
                .andExpect(status().isNotFound());
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
}
