package com.metalcor.procurement.requisition;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RequisitionRepository {

    private static final String HEADER_SQL = """
            SELECT r.id, r.document_number, r.status, r.needed_by, r.notes, r.rejection_reason, r.approved_at,
                   p.id AS plant_id, p.code AS plant_code, p.name AS plant_name,
                   cc.id AS cost_center_id, cc.code AS cost_center_code, cc.name AS cost_center_name,
                   ru.id AS requested_by_id, ru.name AS requested_by_name, ru.role AS requested_by_role,
                   au.id AS approved_by_id, au.name AS approved_by_name, au.role AS approved_by_role
              FROM purchase_requisitions r
              JOIN plants p ON p.id = r.plant_id
              JOIN cost_centers cc ON cc.id = r.cost_center_id
              JOIN app_users ru ON ru.id = r.requested_by
              LEFT JOIN app_users au ON au.id = r.approved_by
             WHERE r.id = :id
            """;

    private static final String ITEMS_SQL = """
            SELECT i.id, i.line_number, i.quantity, i.estimated_unit_price, i.estimated_total, i.needed_by,
                   m.id AS material_id, m.code AS material_code, m.description AS material_description,
                   u.id AS unit_id, u.code AS unit_code,
                   s.id AS supplier_id, s.code AS supplier_code, s.name AS supplier_name
              FROM purchase_requisition_items i
              JOIN materials m ON m.id = i.material_id
              JOIN units_of_measure u ON u.id = i.unit_of_measure_id
              LEFT JOIN suppliers s ON s.id = i.suggested_supplier_id
             WHERE i.purchase_requisition_id = :id
             ORDER BY i.line_number
            """;

    private final JdbcClient jdbc;

    public RequisitionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Set<Long> existingMaterialIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.sql("SELECT id FROM materials WHERE id IN (:ids)")
                .param("ids", ids)
                .query(Long.class)
                .list());
    }

    public Set<Long> existingUnitOfMeasureIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.sql("SELECT id FROM units_of_measure WHERE id IN (:ids)")
                .param("ids", ids)
                .query(Long.class)
                .list());
    }

    public long insertHeader(long plantId, long costCenterId, long requestedBy, LocalDate neededBy, String notes) {
        return jdbc.sql("""
                INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by, notes)
                VALUES (:plantId, :costCenterId, :requestedBy, :neededBy, :notes)
                RETURNING id
                """)
                .param("plantId", plantId)
                .param("costCenterId", costCenterId)
                .param("requestedBy", requestedBy)
                .param("neededBy", neededBy)
                .param("notes", notes, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    public void insertItem(long requisitionId, int lineNumber, long materialId, BigDecimal quantity,
            long unitOfMeasureId, BigDecimal estimatedUnitPrice, Long suggestedSupplierId, LocalDate neededBy) {
        jdbc.sql("""
                INSERT INTO purchase_requisition_items
                    (purchase_requisition_id, line_number, material_id, quantity, unit_of_measure_id,
                     estimated_unit_price, suggested_supplier_id, needed_by)
                VALUES (:requisitionId, :lineNumber, :materialId, :quantity, :unitOfMeasureId,
                        :estimatedUnitPrice, :suggestedSupplierId, :neededBy)
                """)
                .param("requisitionId", requisitionId)
                .param("lineNumber", lineNumber)
                .param("materialId", materialId)
                .param("quantity", quantity)
                .param("unitOfMeasureId", unitOfMeasureId)
                .param("estimatedUnitPrice", estimatedUnitPrice)
                .param("suggestedSupplierId", suggestedSupplierId, Types.BIGINT)
                .param("neededBy", neededBy, Types.DATE)
                .update();
    }

    public Optional<RequisitionResponse> findById(long id) {
        Optional<HeaderRow> header = jdbc.sql(HEADER_SQL)
                .param("id", id)
                .query(RequisitionRepository::mapHeader)
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<RequisitionItemResponse> items = jdbc.sql(ITEMS_SQL)
                .param("id", id)
                .query(RequisitionRepository::mapItem)
                .list();
        BigDecimal total = items.stream()
                .map(RequisitionItemResponse::estimatedTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(header.get().toResponse(items, total));
    }

    public Optional<String> findStatus(long id) {
        return jdbc.sql("SELECT status FROM purchase_requisitions WHERE id = :id")
                .param("id", id)
                .query(String.class)
                .optional();
    }

    public void submit(long id) {
        jdbc.sql("UPDATE purchase_requisitions SET status = 'pending_approval' WHERE id = :id")
                .param("id", id)
                .update();
    }

    public Optional<StatusAndTotal> findStatusAndTotal(long id) {
        return jdbc.sql("""
                SELECT r.status, COALESCE(SUM(i.estimated_total), 0) AS total
                  FROM purchase_requisitions r
                  LEFT JOIN purchase_requisition_items i ON i.purchase_requisition_id = r.id
                 WHERE r.id = :id
                 GROUP BY r.status
                """)
                .param("id", id)
                .query((rs, rowNum) -> new StatusAndTotal(rs.getString("status"), rs.getBigDecimal("total")))
                .optional();
    }

    public Optional<ApprovalRule> findApprovalRule(BigDecimal total) {
        return jdbc.sql("""
                SELECT id, required_role
                  FROM approval_rules
                 WHERE document_type = 'purchase_requisition'
                   AND active
                   AND min_amount <= :total
                   AND (max_amount IS NULL OR max_amount > :total)
                 ORDER BY min_amount
                 LIMIT 1
                """)
                .param("total", total)
                .query((rs, rowNum) -> new ApprovalRule(rs.getLong("id"), rs.getString("required_role")))
                .optional();
    }

    public void insertApproval(long requisitionId, String requiredRole, long approvalRuleId, long decidedBy,
            String decision, String comment, BigDecimal amountEvaluated) {
        jdbc.sql("""
                INSERT INTO approvals
                    (purchase_requisition_id, step, required_role, approval_rule_id, decided_by, decision, comment, amount_evaluated)
                VALUES (:requisitionId, 1, :requiredRole, :approvalRuleId, :decidedBy, :decision, :comment, :amountEvaluated)
                """)
                .param("requisitionId", requisitionId)
                .param("requiredRole", requiredRole)
                .param("approvalRuleId", approvalRuleId)
                .param("decidedBy", decidedBy)
                .param("decision", decision)
                .param("comment", comment, Types.VARCHAR)
                .param("amountEvaluated", amountEvaluated)
                .update();
    }

    public void approve(long id, long approvedBy) {
        jdbc.sql("""
                UPDATE purchase_requisitions
                   SET status = 'approved', approved_by = :approvedBy, approved_at = now()
                 WHERE id = :id
                """)
                .param("id", id)
                .param("approvedBy", approvedBy)
                .update();
    }

    public void reject(long id, String reason) {
        jdbc.sql("""
                UPDATE purchase_requisitions
                   SET status = 'rejected', rejection_reason = :reason
                 WHERE id = :id
                """)
                .param("id", id)
                .param("reason", reason)
                .update();
    }

    private static HeaderRow mapHeader(ResultSet rs, int rowNum) throws SQLException {
        Long approvedById = rs.getObject("approved_by_id", Long.class);
        UserRef approvedBy = approvedById == null
                ? null
                : new UserRef(approvedById, rs.getString("approved_by_name"), rs.getString("approved_by_role"));
        return new HeaderRow(
                rs.getLong("id"),
                rs.getString("document_number"),
                rs.getString("status"),
                new PlantRef(rs.getLong("plant_id"), rs.getString("plant_code"), rs.getString("plant_name")),
                new CostCenterRef(rs.getLong("cost_center_id"), rs.getString("cost_center_code"), rs.getString("cost_center_name")),
                new UserRef(rs.getLong("requested_by_id"), rs.getString("requested_by_name"), rs.getString("requested_by_role")),
                rs.getObject("needed_by", LocalDate.class),
                rs.getString("notes"),
                approvedBy,
                rs.getObject("approved_at", OffsetDateTime.class),
                rs.getString("rejection_reason"));
    }

    private static RequisitionItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        Long supplierId = rs.getObject("supplier_id", Long.class);
        SupplierRef supplier = supplierId == null
                ? null
                : new SupplierRef(supplierId, rs.getString("supplier_code"), rs.getString("supplier_name"));
        return new RequisitionItemResponse(
                rs.getLong("id"),
                rs.getInt("line_number"),
                new MaterialRef(rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_description")),
                rs.getBigDecimal("quantity"),
                new UnitOfMeasureRef(rs.getLong("unit_id"), rs.getString("unit_code")),
                rs.getBigDecimal("estimated_unit_price"),
                rs.getBigDecimal("estimated_total"),
                supplier,
                rs.getObject("needed_by", LocalDate.class));
    }

    private record HeaderRow(
            long id, String documentNumber, String status,
            PlantRef plant, CostCenterRef costCenter, UserRef requestedBy,
            LocalDate neededBy, String notes, UserRef approvedBy, OffsetDateTime approvedAt, String rejectionReason) {

        RequisitionResponse toResponse(List<RequisitionItemResponse> items, BigDecimal total) {
            return new RequisitionResponse(id, documentNumber, status, plant, costCenter, requestedBy,
                    neededBy, notes, approvedBy, approvedAt, rejectionReason, items, total);
        }
    }

    public record StatusAndTotal(String status, BigDecimal total) {
    }

    public record ApprovalRule(long id, String requiredRole) {
    }
}