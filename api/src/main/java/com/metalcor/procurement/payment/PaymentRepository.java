package com.metalcor.procurement.payment;

import com.metalcor.procurement.requisition.UserRef;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository {

    private static final String HEADER_SQL = """
            SELECT p.id, p.document_number, ir.document_number AS invoice_number,
                   p.amount, p.scheduled_for, p.payment_method, p.status, p.paid_at,
                   u.id AS created_by_id, u.name AS created_by_name, u.role AS created_by_role,
                   p.reference
              FROM payments p
              JOIN invoice_receipts ir ON ir.id = p.invoice_receipt_id
              JOIN app_users u ON u.id = p.created_by
             WHERE p.id = :id
            """;

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Posts a payment immediately: status paid, paid_at now(). document_number comes from next_document_number(). */
    public long insert(long invoiceReceiptId, BigDecimal amount, LocalDate scheduledFor,
            String paymentMethod, long createdBy, String reference) {
        return jdbc.sql("""
                INSERT INTO payments
                    (invoice_receipt_id, amount, scheduled_for, payment_method, status, paid_at, created_by, reference)
                VALUES (:invoiceReceiptId, :amount, :scheduledFor, :paymentMethod, 'paid', now(), :createdBy, :reference)
                RETURNING id
                """)
                .param("invoiceReceiptId", invoiceReceiptId)
                .param("amount", amount)
                .param("scheduledFor", scheduledFor)
                .param("paymentMethod", paymentMethod)
                .param("createdBy", createdBy)
                .param("reference", reference, Types.VARCHAR)
                .query(Long.class)
                .single();
    }

    public Optional<PaymentResponse> findById(long id) {
        return jdbc.sql(HEADER_SQL)
                .param("id", id)
                .query(PaymentRepository::mapResponse)
                .optional();
    }

    /** The invoice's active (non-cancelled) payment, if any, for the invoice response. */
    public Optional<PaymentRef> findRefByInvoiceId(long invoiceId) {
        return jdbc.sql("""
                SELECT id, document_number, amount, scheduled_for, payment_method, status, paid_at
                  FROM payments
                 WHERE invoice_receipt_id = :invoiceId AND status <> 'cancelled'
                """)
                .param("invoiceId", invoiceId)
                .query(PaymentRepository::mapRef)
                .optional();
    }

    private static PaymentResponse mapResponse(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentResponse(
                rs.getLong("id"),
                rs.getString("document_number"),
                rs.getString("invoice_number"),
                rs.getBigDecimal("amount"),
                rs.getObject("scheduled_for", LocalDate.class),
                rs.getString("payment_method"),
                rs.getString("status"),
                rs.getObject("paid_at", OffsetDateTime.class),
                new UserRef(rs.getLong("created_by_id"), rs.getString("created_by_name"), rs.getString("created_by_role")),
                rs.getString("reference"));
    }

    private static PaymentRef mapRef(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentRef(
                rs.getLong("id"),
                rs.getString("document_number"),
                rs.getBigDecimal("amount"),
                rs.getObject("scheduled_for", LocalDate.class),
                rs.getString("payment_method"),
                rs.getString("status"),
                rs.getObject("paid_at", OffsetDateTime.class));
    }
}