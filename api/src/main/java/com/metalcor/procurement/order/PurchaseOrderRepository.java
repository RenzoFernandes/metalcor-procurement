package com.metalcor.procurement.order;

import com.metalcor.procurement.requisition.MaterialRef;
import com.metalcor.procurement.requisition.PlantRef;
import com.metalcor.procurement.requisition.SupplierRef;
import com.metalcor.procurement.requisition.UserRef;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PurchaseOrderRepository {

    private static final String HEADER_SQL = """
            SELECT o.id, o.document_number, o.status, o.order_date, o.expected_delivery_date, o.payment_terms_days,
                   s.id AS supplier_id, s.code AS supplier_code, s.name AS supplier_name,
                   r.document_number AS requisition_number,
                   p.id AS plant_id, p.code AS plant_code, p.name AS plant_name,
                   bu.id AS buyer_id, bu.name AS buyer_name, bu.role AS buyer_role
              FROM purchase_orders o
              JOIN suppliers s ON s.id = o.supplier_id
              LEFT JOIN purchase_requisitions r ON r.id = o.purchase_requisition_id
              JOIN plants p ON p.id = o.plant_id
              JOIN app_users bu ON bu.id = o.buyer_id
             WHERE o.id = :id
            """;

    private static final String ITEMS_SQL = """
            SELECT i.id, i.quantity, i.unit_price, i.line_total,
                   m.id AS material_id, m.code AS material_code, m.description AS material_description
              FROM purchase_order_items i
              JOIN materials m ON m.id = i.material_id
             WHERE i.purchase_order_id = :id
             ORDER BY i.line_number
            """;

    private final JdbcClient jdbc;

    public PurchaseOrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Current price and lead time for a supplier/material pair, if the supplier sells the material today. */
    public Optional<SupplierPrice> findCurrentPrice(long supplierId, long materialId) {
        return jdbc.sql("""
                SELECT supplier_id, unit_price, lead_time_days
                  FROM supplier_materials
                 WHERE supplier_id = :supplierId AND material_id = :materialId
                   AND valid_from <= current_date
                   AND (valid_to IS NULL OR valid_to >= current_date)
                 ORDER BY valid_from DESC
                 LIMIT 1
                """)
                .param("supplierId", supplierId)
                .param("materialId", materialId)
                .query(PurchaseOrderRepository::mapSupplierPrice)
                .optional();
    }

    /** Cheapest current price for a material, across every supplier that sells it today. */
    public Optional<SupplierPrice> findCheapestCurrentPrice(long materialId) {
        return jdbc.sql("""
                SELECT supplier_id, unit_price, lead_time_days
                  FROM supplier_materials
                 WHERE material_id = :materialId
                   AND valid_from <= current_date
                   AND (valid_to IS NULL OR valid_to >= current_date)
                 ORDER BY unit_price ASC, valid_from DESC
                 LIMIT 1
                """)
                .param("materialId", materialId)
                .query(PurchaseOrderRepository::mapSupplierPrice)
                .optional();
    }

    public int findPaymentTermsDays(long supplierId) {
        return jdbc.sql("SELECT payment_terms_days FROM suppliers WHERE id = :id")
                .param("id", supplierId)
                .query(Integer.class)
                .single();
    }

    public long insertHeader(long supplierId, long requisitionId, long plantId, long buyerId,
            int paymentTermsDays, int maxLeadTimeDays) {
        return jdbc.sql("""
                INSERT INTO purchase_orders
                    (supplier_id, purchase_requisition_id, plant_id, buyer_id,
                     expected_delivery_date, payment_terms_days, status)
                VALUES (:supplierId, :requisitionId, :plantId, :buyerId,
                        current_date + :maxLeadTimeDays, :paymentTermsDays, 'issued')
                RETURNING id
                """)
                .param("supplierId", supplierId)
                .param("requisitionId", requisitionId)
                .param("plantId", plantId)
                .param("buyerId", buyerId)
                .param("maxLeadTimeDays", maxLeadTimeDays)
                .param("paymentTermsDays", paymentTermsDays)
                .query(Long.class)
                .single();
    }

    public void insertItem(long orderId, int lineNumber, long materialId, Long requisitionItemId,
            BigDecimal quantity, long unitOfMeasureId, BigDecimal unitPrice) {
        jdbc.sql("""
                INSERT INTO purchase_order_items
                    (purchase_order_id, line_number, material_id, requisition_item_id,
                     quantity, unit_of_measure_id, unit_price)
                VALUES (:orderId, :lineNumber, :materialId, :requisitionItemId,
                        :quantity, :unitOfMeasureId, :unitPrice)
                """)
                .param("orderId", orderId)
                .param("lineNumber", lineNumber)
                .param("materialId", materialId)
                .param("requisitionItemId", requisitionItemId, Types.BIGINT)
                .param("quantity", quantity)
                .param("unitOfMeasureId", unitOfMeasureId)
                .param("unitPrice", unitPrice)
                .update();
    }

    public Optional<PurchaseOrderResponse> findById(long id) {
        Optional<HeaderRow> header = jdbc.sql(HEADER_SQL)
                .param("id", id)
                .query(PurchaseOrderRepository::mapHeader)
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<PurchaseOrderItemResponse> items = jdbc.sql(ITEMS_SQL)
                .param("id", id)
                .query(PurchaseOrderRepository::mapItem)
                .list();
        BigDecimal total = items.stream()
                .map(PurchaseOrderItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(header.get().toResponse(items, total));
    }

    private static SupplierPrice mapSupplierPrice(ResultSet rs, int rowNum) throws SQLException {
        return new SupplierPrice(rs.getLong("supplier_id"), rs.getBigDecimal("unit_price"), rs.getInt("lead_time_days"));
    }

    private static HeaderRow mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new HeaderRow(
                rs.getLong("id"),
                rs.getString("document_number"),
                new SupplierRef(rs.getLong("supplier_id"), rs.getString("supplier_code"), rs.getString("supplier_name")),
                rs.getString("requisition_number"),
                new PlantRef(rs.getLong("plant_id"), rs.getString("plant_code"), rs.getString("plant_name")),
                new UserRef(rs.getLong("buyer_id"), rs.getString("buyer_name"), rs.getString("buyer_role")),
                rs.getObject("order_date", LocalDate.class),
                rs.getObject("expected_delivery_date", LocalDate.class),
                rs.getInt("payment_terms_days"),
                rs.getString("status"));
    }

    private static PurchaseOrderItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new PurchaseOrderItemResponse(
                rs.getLong("id"),
                new MaterialRef(rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_description")),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total"));
    }

    /** Price and lead time offered by a supplier for a material, valid today. */
    public record SupplierPrice(long supplierId, BigDecimal unitPrice, int leadTimeDays) {
    }

    private record HeaderRow(
            long id, String documentNumber, SupplierRef supplier, String requisitionNumber, PlantRef plant,
            UserRef buyer, LocalDate orderDate, LocalDate expectedDeliveryDate, int paymentTermsDays, String status) {

        PurchaseOrderResponse toResponse(List<PurchaseOrderItemResponse> items, BigDecimal total) {
            return new PurchaseOrderResponse(id, documentNumber, supplier, requisitionNumber, plant, buyer,
                    orderDate, expectedDeliveryDate, paymentTermsDays, status, items, total);
        }
    }
}
