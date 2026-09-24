package com.metalcor.procurement.requisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Seed users (db/seed/01_master_data.sql): 1 = requester (plant 1), 8 = buyer (plant 1),
 * 10 = approver (plant 1). Approval rule ranges: 0-10000 buyer, 10000-100000 approver, 100000+ manager.
 */
@Order(1)
class RequisitionControllerTest extends AbstractIntegrationTest {

    private static final String REQUESTER_ID = "1";
    private static final String BUYER_ID = "8";

    @Autowired
    JdbcClient jdbc;

    @Test
    void createsRequisitionSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/requisitions")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smallRequisitionBody()))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.requestedBy.id").value(1))
                .andExpect(jsonPath("$.total").value(1000.0))
                .andExpect(jsonPath("$.items[0].lineNumber").value(1));
    }

    @Test
    void createWithUnknownMaterialReturnsBadRequest() throws Exception {
        String body = """
                {
                  "plantId": 1,
                  "costCenterId": 1,
                  "neededBy": "2026-10-15",
                  "items": [
                    {"materialId": 999999, "quantity": 10, "unitOfMeasureId": 1, "estimatedUnitPrice": 100}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/requisitions")
                        .header("X-User-Id", REQUESTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithoutUserHeaderReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/requisitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(smallRequisitionBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitMovesDraftToPendingApproval() throws Exception {
        long id = createRequisition(smallRequisitionBody());

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/submit")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending_approval"));
    }

    @Test
    void submitTwiceReturnsConflict() throws Exception {
        long id = createRequisition(smallRequisitionBody());
        submit(id);

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/submit")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void decideWithWrongRoleReturnsForbidden() throws Exception {
        // total 500 x 100 = 50000 -> requires approver, not buyer
        long id = createRequisition(midRequisitionBody());
        submit(id);

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/decide")
                        .header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void decideApproveWithCorrectRoleUpdatesStatusAndApprovals() throws Exception {
        long id = createRequisition(smallRequisitionBody());
        submit(id);

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/decide")
                        .header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.approvedBy.id").value(8));

        Long approvalCount = jdbc.sql("""
                        SELECT count(*) FROM approvals
                         WHERE purchase_requisition_id = :id AND decision = 'approved'
                        """)
                .param("id", id)
                .query(Long.class)
                .single();
        assertThat(approvalCount).isEqualTo(1L);
    }

    @Test
    void decideRejectWithoutCommentReturnsBadRequest() throws Exception {
        long id = createRequisition(smallRequisitionBody());
        submit(id);

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/decide")
                        .header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"reject\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void decideRejectWithCommentUpdatesStatus() throws Exception {
        long id = createRequisition(smallRequisitionBody());
        submit(id);

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/decide")
                        .header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"reject\", \"comment\": \"Budget not available\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectionReason").value("Budget not available"));
    }

    @Test
    void approvalDecisionIsRecordedInAuditLogWithDecidingUser() throws Exception {
        long id = createRequisition(smallRequisitionBody());
        submit(id);

        mockMvc.perform(post("/api/v1/requisitions/" + id + "/decide")
                        .header("X-User-Id", BUYER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"approve\"}"))
                .andExpect(status().isOk());

        Long changedBy = jdbc.sql("""
                        SELECT changed_by FROM audit_log
                         WHERE table_name = 'purchase_requisitions' AND record_id = :id AND operation = 'UPDATE'
                         ORDER BY changed_at DESC
                         LIMIT 1
                        """)
                .param("id", id)
                .query(Long.class)
                .single();
        assertThat(changedBy).isEqualTo(8L);
    }

    private static String smallRequisitionBody() {
        return """
                {
                  "plantId": 1,
                  "costCenterId": 1,
                  "neededBy": "2026-10-15",
                  "notes": "Test requisition",
                  "items": [
                    {"materialId": 1, "quantity": 10, "unitOfMeasureId": 1, "estimatedUnitPrice": 100}
                  ]
                }
                """;
    }

    private static String midRequisitionBody() {
        return """
                {
                  "plantId": 1,
                  "costCenterId": 1,
                  "neededBy": "2026-10-15",
                  "items": [
                    {"materialId": 1, "quantity": 500, "unitOfMeasureId": 1, "estimatedUnitPrice": 100}
                  ]
                }
                """;
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

    private void submit(long id) throws Exception {
        mockMvc.perform(post("/api/v1/requisitions/" + id + "/submit")
                        .header("X-User-Id", REQUESTER_ID))
                .andExpect(status().isOk());
    }
}