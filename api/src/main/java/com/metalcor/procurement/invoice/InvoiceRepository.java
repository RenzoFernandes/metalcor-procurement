package com.metalcor.procurement.invoice;

import com.metalcor.procurement.common.PageResponse;
import com.metalcor.procurement.payment.PaymentRef;
import com.metalcor.procurement.requisition.MaterialRef;
import com.metalcor.procurement.requisition.SupplierRef;
import com.metalcor.procurement.requisition.UserRef;
import java.math.BigDecimal;
import java.sql.Array;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class InvoiceRepository {

    private static final String HEADER_SQL = """
            SELECT ir.id, ir.document_number, po.document_number AS purchase_order_number,
                   s.id AS supplier_id, s.code AS supplier_code, s.name AS supplier_name,
                   ir.supplier_invoice_number, ir.invoice_date, ir.due_date, ir.posting_date,
                   ir.gross_amount, ir.status, ir.block_reason,
                   au.id AS approved_by_id, au.name AS approved_by_name, au.role AS approved_by_role,
                   ir.approved_at
              FROM invoice_receipts ir
              JOIN purchase_orders po ON po.id = ir.purchase_order_id
              JOIN suppliers s ON s.id = ir.supplier_id
              LEFT JOIN app_users au ON au.id = ir.approved_by
             WHERE ir.id = :id
            """;

    private static final String ITEMS_SQL = """
            SELECT iri.quantity_invoiced, iri.unit_price, iri.line_total,
                   m.id AS material_id, m.code AS material_code, m.description AS material_description,
                   vw.price_exception, vw.quantity_exception
              FROM invoice_receipt_items iri
              JOIN purchase_order_items poi ON poi.id = iri.purchase_order_item_id
              JOIN materials m ON m.id = poi.material_id
              JOIN vw_invoice_line_match vw
                ON vw.invoice_receipt_id = iri.invoice_receipt_id
               AND vw.purchase_order_item_id = iri.purchase_order_item_id
             WHERE iri.invoice_receipt_id = :id
             ORDER BY iri.line_number
            """;

    private static final String PAYMENT_SQL = """
            SELECT id, document_number, amount, scheduled_for, payment_method, status, paid_at
              FROM payments
             WHERE invoice_receipt_id = :id AND status <> 'cancelled'
            """;

    // Optional filters: a null parameter switches the condition off. All values are bound, never concatenated.
    private static final String FILTER = """
             WHERE has_exception
               AND (CAST(:resolution AS text) IS NULL OR resolution = CAST(:resolution AS text))
               AND (CAST(:exceptionType AS text) IS NULL
                    OR exception_types @> ARRAY[CAST(:exceptionType AS text)])
            """;

    private static final String COUNT_SQL = "SELECT count(*) FROM vw_invoice_match" + FILTER;

    private static final String PAGE_SQL = """
            SELECT invoice_number, supplier_id, supplier_name, po_number, invoice_date, posting_date, due_date,
                   gross_amount, invoice_status, max_abs_price_variance_pct, max_quantity_variance_pct,
                   price_exception, quantity_exception, invoice_before_receipt, exception_types, resolution,
                   approved_by, approved_at, days_to_resolve, block_reason, age_days, is_stale
              FROM vw_invoice_match
            """ + FILTER + """
             ORDER BY posting_date DESC, invoice_number
             LIMIT :size OFFSET :offset
            """;

    private final JdbcClient jdbc;

    public InvoiceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OrderInfo> findOrderInfo(long orderId) {
        return jdbc.sql("""
                SELECT o.status, o.supplier_id, s.payment_terms_days
                  FROM purchase_orders o
                  JOIN suppliers s ON s.id = o.supplier_id
                 WHERE o.id = :id
                """)
                .param("id", orderId)
                .query((rs, rowNum) -> new OrderInfo(
                        rs.getString("status"), rs.getLong("supplier_id"), rs.getInt("payment_terms_days")))
                .optional();
    }

    /** Ids of every line of the purchase order, to validate that the request references its own lines. */
    public Set<Long> findOrderItemIds(long orderId) {
        return new LinkedHashSet<>(jdbc.sql("SELECT id FROM purchase_order_items WHERE purchase_order_id = :orderId")
                .param("orderId", orderId)
                .query(Long.class)
                .list());
    }

    public long insertHeader(long supplierId, long orderId, String supplierInvoiceNumber, LocalDate invoiceDate,
            LocalDate dueDate, BigDecimal grossAmount, String status, String blockReason) {
        return jdbc.sql("""
                INSERT INTO invoice_receipts
                    (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date,
                     gross_amount, status, block_reason)
                VALUES (:supplierId, :orderId, :supplierInvoiceNumber, :invoiceDate, :dueDate,
                        :grossAmount, :status, :blockReason)
                RETURNING id
                """)
                .param("supplierId", supplierId)
                .param("orderId", orderId)
                .param("supplierInvoiceNumber", supplierInvoiceNumber)
                .param("invoiceDate", invoiceDate)
                .param("dueDate", dueDate)
                .param("grossAmount", grossAmount)
                .param("status", status)
                .param("blockReason", blockReason, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    public void insertItem(long invoiceId, int lineNumber, long purchaseOrderItemId,
            BigDecimal quantityInvoiced, BigDecimal unitPrice) {
        jdbc.sql("""
                INSERT INTO invoice_receipt_items
                    (invoice_receipt_id, line_number, purchase_order_item_id, quantity_invoiced, unit_price)
                VALUES (:invoiceId, :lineNumber, :purchaseOrderItemId, :quantityInvoiced, :unitPrice)
                """)
                .param("invoiceId", invoiceId)
                .param("lineNumber", lineNumber)
                .param("purchaseOrderItemId", purchaseOrderItemId)
                .param("quantityInvoiced", quantityInvoiced)
                .param("unitPrice", unitPrice)
                .update();
    }

    /** Three-way match outcome of every line of the invoice, from vw_invoice_line_match (same logic as the GET response). */
    public List<LineMatch> findLineMatches(long invoiceId) {
        return jdbc.sql("""
                SELECT price_exception, quantity_exception, price_tolerance_pct, quantity_tolerance_pct
                  FROM vw_invoice_line_match
                 WHERE invoice_receipt_id = :id
                """)
                .param("id", invoiceId)
                .query((rs, rowNum) -> new LineMatch(
                        rs.getBoolean("price_exception"), rs.getBoolean("quantity_exception"),
                        rs.getBigDecimal("price_tolerance_pct"), rs.getBigDecimal("quantity_tolerance_pct")))
                .list();
    }

    public void updateStatus(long invoiceId, String status, String blockReason) {
        jdbc.sql("UPDATE invoice_receipts SET status = :status, block_reason = :blockReason WHERE id = :id")
                .param("status", status)
                .param("blockReason", blockReason, Types.VARCHAR)
                .param("id", invoiceId)
                .update();
    }

    /**
     * Marks the invoice paid, leaving block_reason untouched (kept as history). approved_by/approved_at are set to
     * the payer only if still null (a matched invoice paid without a prior /approve); an earlier approval is kept.
     */
    public void updatePaid(long invoiceId, long payerId) {
        jdbc.sql("""
                UPDATE invoice_receipts
                   SET status = 'paid',
                       approved_by = COALESCE(approved_by, :payerId),
                       approved_at = COALESCE(approved_at, now())
                 WHERE id = :id
                """)
                .param("payerId", payerId)
                .param("id", invoiceId)
                .update();
    }

    public void updateApproval(long invoiceId, long approvedBy, String notes) {
        jdbc.sql("""
                UPDATE invoice_receipts
                   SET status = 'approved', approved_by = :approvedBy, approved_at = now(), notes = :notes
                 WHERE id = :id
                """)
                .param("approvedBy", approvedBy)
                .param("notes", notes, Types.VARCHAR)
                .param("id", invoiceId)
                .update();
    }

    /** Status, purchase order and billing fields needed to approve or pay an invoice. */
    public Optional<InvoiceInfo> findInvoiceInfo(long invoiceId) {
        return jdbc.sql("""
                SELECT status, purchase_order_id, gross_amount, due_date
                  FROM invoice_receipts
                 WHERE id = :id
                """)
                .param("id", invoiceId)
                .query((rs, rowNum) -> new InvoiceInfo(
                        rs.getString("status"), rs.getLong("purchase_order_id"),
                        rs.getBigDecimal("gross_amount"), rs.getObject("due_date", LocalDate.class)))
                .optional();
    }

    public Optional<InvoiceResponse> findById(long id) {
        Optional<HeaderRow> header = jdbc.sql(HEADER_SQL)
                .param("id", id)
                .query(InvoiceRepository::mapHeader)
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<InvoiceItemResponse> items = jdbc.sql(ITEMS_SQL)
                .param("id", id)
                .query(InvoiceRepository::mapItem)
                .list();
        PaymentRef payment = jdbc.sql(PAYMENT_SQL)
                .param("id", id)
                .query(InvoiceRepository::mapPayment)
                .optional()
                .orElse(null);
        return Optional.of(header.get().toResponse(items, payment));
    }

    private static HeaderRow mapHeader(ResultSet rs, int rowNum) throws SQLException {
        Long approvedById = rs.getObject("approved_by_id", Long.class);
        UserRef approvedBy = approvedById == null
                ? null
                : new UserRef(approvedById, rs.getString("approved_by_name"), rs.getString("approved_by_role"));
        return new HeaderRow(
                rs.getLong("id"),
                rs.getString("document_number"),
                rs.getString("purchase_order_number"),
                new SupplierRef(rs.getLong("supplier_id"), rs.getString("supplier_code"), rs.getString("supplier_name")),
                rs.getString("supplier_invoice_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getObject("posting_date", LocalDate.class),
                rs.getBigDecimal("gross_amount"),
                rs.getString("status"),
                rs.getString("block_reason"),
                approvedBy,
                rs.getObject("approved_at", OffsetDateTime.class));
    }

    private static InvoiceItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new InvoiceItemResponse(
                new MaterialRef(rs.getLong("material_id"), rs.getString("material_code"), rs.getString("material_description")),
                rs.getBigDecimal("quantity_invoiced"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("line_total"),
                rs.getBoolean("price_exception"),
                rs.getBoolean("quantity_exception"));
    }

    private static PaymentRef mapPayment(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentRef(
                rs.getLong("id"),
                rs.getString("document_number"),
                rs.getBigDecimal("amount"),
                rs.getObject("scheduled_for", LocalDate.class),
                rs.getString("payment_method"),
                rs.getString("status"),
                rs.getObject("paid_at", OffsetDateTime.class));
    }

    /** Status, supplier and the supplier's payment terms of a purchase order. */
    public record OrderInfo(String status, long supplierId, int paymentTermsDays) {
    }

    /** Three-way match outcome of one invoice line, from vw_invoice_line_match. */
    public record LineMatch(boolean priceException, boolean quantityException,
            BigDecimal priceTolerancePct, BigDecimal quantityTolerancePct) {
    }

    /** Status, purchase order and billing fields of an invoice, needed to approve or pay it. */
    public record InvoiceInfo(String status, long purchaseOrderId, BigDecimal grossAmount, LocalDate dueDate) {
    }

    private record HeaderRow(
            long id, String documentNumber, String purchaseOrderNumber, SupplierRef supplier,
            String supplierInvoiceNumber, LocalDate invoiceDate, LocalDate dueDate, LocalDate postingDate,
            BigDecimal grossAmount, String status, String blockReason, UserRef approvedBy, OffsetDateTime approvedAt) {

        InvoiceResponse toResponse(List<InvoiceItemResponse> items, PaymentRef payment) {
            return new InvoiceResponse(id, documentNumber, purchaseOrderNumber, supplier, supplierInvoiceNumber,
                    invoiceDate, dueDate, postingDate, grossAmount, status, blockReason, approvedBy, approvedAt,
                    items, payment);
        }
    }

    public PageResponse<InvoiceExceptionDto> findExceptions(String resolution, String exceptionType, int page, int size) {
        long total = jdbc.sql(COUNT_SQL)
                .param("resolution", resolution, Types.VARCHAR)
                .param("exceptionType", exceptionType, Types.VARCHAR)
                .query(Long.class)
                .single();

        List<InvoiceExceptionDto> items = jdbc.sql(PAGE_SQL)
                .param("resolution", resolution, Types.VARCHAR)
                .param("exceptionType", exceptionType, Types.VARCHAR)
                .param("size", size)
                .param("offset", (long) page * size)
                .query(InvoiceRepository::mapRow)
                .list();

        return PageResponse.of(items, page, size, total);
    }

    private static InvoiceExceptionDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        Array types = rs.getArray("exception_types");
        return new InvoiceExceptionDto(
                rs.getString("invoice_number"),
                rs.getLong("supplier_id"),
                rs.getString("supplier_name"),
                rs.getString("po_number"),
                rs.getObject("invoice_date", LocalDate.class),
                rs.getObject("posting_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                rs.getBigDecimal("gross_amount"),
                rs.getString("invoice_status"),
                rs.getBigDecimal("max_abs_price_variance_pct"),
                rs.getBigDecimal("max_quantity_variance_pct"),
                rs.getBoolean("price_exception"),
                rs.getBoolean("quantity_exception"),
                rs.getBoolean("invoice_before_receipt"),
                List.of((String[]) types.getArray()),
                rs.getString("resolution"),
                rs.getString("approved_by"),
                rs.getObject("approved_at", OffsetDateTime.class),
                rs.getObject("days_to_resolve", Integer.class),
                rs.getString("block_reason"),
                rs.getObject("age_days", Integer.class),
                rs.getBoolean("is_stale"));
    }
}
