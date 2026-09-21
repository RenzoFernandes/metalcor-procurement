package com.metalcor.procurement.invoice;

import com.metalcor.procurement.common.PageResponse;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class InvoiceRepository {

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
