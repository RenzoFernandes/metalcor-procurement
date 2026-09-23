package com.metalcor.procurement.receipt;

import com.metalcor.procurement.requisition.MaterialRef;
import com.metalcor.procurement.requisition.PlantRef;
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
public class ReceiptRepository {

    private static final String HEADER_SQL = """
            SELECT gr.id, gr.document_number, gr.receipt_date, gr.delivery_note_number, gr.status,
                   po.document_number AS purchase_order_number,
                   p.id AS plant_id, p.code AS plant_code, p.name AS plant_name,
                   u.id AS received_by_id, u.name AS received_by_name, u.role AS received_by_role
              FROM goods_receipts gr
              JOIN purchase_orders po ON po.id = gr.purchase_order_id
              JOIN plants p ON p.id = gr.plant_id
              JOIN app_users u ON u.id = gr.received_by
             WHERE gr.id = :id
            """;

    private static final String ITEMS_SQL = """
            SELECT gri.quantity_received,
                   m.id AS material_id, m.code AS material_code, m.description AS material_description
              FROM goods_receipt_items gri
              JOIN purchase_order_items poi ON poi.id = gri.purchase_order_item_id
              JOIN materials m ON m.id = poi.material_id
             WHERE gri.goods_receipt_id = :id
             ORDER BY gri.line_number
            """;

    private final JdbcClient jdbc;

    public ReceiptRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OrderInfo> findOrderStatusAndPlant(long orderId) {
        return jdbc.sql("SELECT status, plant_id FROM purchase_orders WHERE id = :id")
                .param("id", orderId)
                .query((rs, rowNum) -> new OrderInfo(rs.getString("status"), rs.getLong("plant_id")))
                .optional();
    }

    /** Ordered quantity and quantity already received (posted receipts only) for every line of the order. */
    public List<ItemProgress> findOrderItemProgress(long orderId) {
        return jdbc.sql("""
                SELECT poi.id, poi.quantity AS ordered_quantity,
                       COALESCE(SUM(gri.quantity_received) FILTER (WHERE gr.status = 'posted'), 0) AS received_quantity
                  FROM purchase_order_items poi
                  LEFT JOIN goods_receipt_items gri ON gri.purchase_order_item_id = poi.id
                  LEFT JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
                 WHERE poi.purchase_order_id = :orderId
                 GROUP BY poi.id, poi.quantity
                """)
                .param("orderId", orderId)
                .query((rs, rowNum) -> new ItemProgress(
                        rs.getLong("id"), rs.getBigDecimal("ordered_quantity"), rs.getBigDecimal("received_quantity")))
                .list();
    }

    public long insertHeader(long orderId, long plantId, long receivedBy, String deliveryNoteNumber) {
        return jdbc.sql("""
                INSERT INTO goods_receipts (purchase_order_id, plant_id, received_by, delivery_note_number)
                VALUES (:orderId, :plantId, :receivedBy, :deliveryNoteNumber)
                RETURNING id
                """)
                .param("orderId", orderId)
                .param("plantId", plantId)
                .param("receivedBy", receivedBy)
                .param("deliveryNoteNumber", deliveryNoteNumber, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    public void insertItem(long receiptId, int lineNumber, long purchaseOrderItemId, BigDecimal quantityReceived) {
        jdbc.sql("""
                INSERT INTO goods_receipt_items (goods_receipt_id, line_number, purchase_order_item_id, quantity_received)
                VALUES (:receiptId, :lineNumber, :purchaseOrderItemId, :quantityReceived)
                """)
                .param("receiptId", receiptId)
                .param("lineNumber", lineNumber)
                .param("purchaseOrderItemId", purchaseOrderItemId)
                .param("quantityReceived", quantityReceived)
                .update();
    }

    public void updateOrderStatus(long orderId, String status) {
        jdbc.sql("UPDATE purchase_orders SET status = :status WHERE id = :id")
                .param("status", status)
                .param("id", orderId)
                .update();
    }

    public Optional<GoodsReceiptResponse> findById(long id) {
        Optional<HeaderRow> header = jdbc.sql(HEADER_SQL)
                .param("id", id)
                .query(ReceiptRepository::mapHeader)
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<GoodsReceiptItemResponse> items = jdbc.sql(ITEMS_SQL)
                .param("id", id)
                .query(ReceiptRepository::mapItem)
                .list();
        return Optional.of(header.get().toResponse(items));
    }

    private static HeaderRow mapHeader(ResultSet rs, int rowNum) throws SQLException {
        return new HeaderRow(
                rs.getLong("id"),
                rs.getString("document_number"),
                rs.getString("purchase_order_number"),
                new PlantRef(rs.getLong("plant_id"), rs.getString("plant_code"), rs.getString("plant_name")),
                new UserRef(rs.getLong("received_by_id"), rs.getString("received_by_name"), rs.getString("received_by_role")),
                rs.getObject("receipt_date", LocalDate.class),
                rs.getString("delivery_note_number"),
                rs.getString("status"));
    }

    private static GoodsReceiptItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new GoodsReceiptItemResponse(
                new MaterialRef(rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_description")),
                rs.getBigDecimal("quantity_received"));
    }

    /** Status and plant of a purchase order. */
    public record OrderInfo(String status, long plantId) {
    }

    /** Ordered quantity and quantity already received (posted receipts) for one purchase order line. */
    public record ItemProgress(long purchaseOrderItemId, BigDecimal orderedQuantity, BigDecimal receivedQuantity) {
    }

    private record HeaderRow(
            long id, String documentNumber, String purchaseOrderNumber, PlantRef plant, UserRef receivedBy,
            LocalDate receiptDate, String deliveryNoteNumber, String status) {

        GoodsReceiptResponse toResponse(List<GoodsReceiptItemResponse> items) {
            return new GoodsReceiptResponse(id, documentNumber, purchaseOrderNumber, plant, receivedBy,
                    receiptDate, deliveryNoteNumber, status, items);
        }
    }
}
