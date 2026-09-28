package com.metalcor.procurement.dashboard;

import com.metalcor.procurement.supplier.SupplierRepository;
import com.metalcor.procurement.supplier.SupplierScorecardDto;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries behind the manager panel. Every query is a fixed constant, never built from user input. */
@Repository
@Transactional(readOnly = true)
public class DashboardRepository {

    private static final String SPEND_BY_MONTH_SQL = """
            SELECT order_month, category_code, category_name, orders, items, total_value, suppliers
              FROM vw_spend_by_month_category
             ORDER BY order_month, category_code
            """.strip();

    // Aggregates the per-month view across all months: one row per (exception_type, resolution).
    private static final String EXCEPTION_SUMMARY_SQL = """
            SELECT exception_type, resolution, sum(invoice_count) AS invoice_count, sum(gross_amount) AS gross_amount
              FROM vw_match_exception_summary
             GROUP BY exception_type, resolution
             ORDER BY exception_type, resolution
            """.strip();

    private static final String LATE_PAYMENTS_SQL = """
            SELECT date_trunc('month', due_date)::date AS due_month,
                   count(*) AS payments,
                   count(*) FILTER (WHERE is_late) AS late_payments,
                   round(avg(days_late) FILTER (WHERE is_late), 1) AS avg_days_late
              FROM vw_payment_timeliness
             GROUP BY date_trunc('month', due_date)::date
             ORDER BY due_month
            """.strip();

    private static final String STALE_INVOICES_SQL = """
            SELECT invoice_number, amount, age_days, age_band, block_reason
              FROM vw_stale_blocked_invoices
             WHERE is_stale AND NOT is_duplicate_candidate
             ORDER BY age_days DESC
            """.strip();

    private static final String KPI_TOTAL_SPEND_SQL = """
            SELECT sum(line_total) AS total_spend FROM purchase_order_items
            """.strip();

    private static final String KPI_ORDERS_SQL = """
            SELECT count(*) AS orders FROM purchase_orders
            """.strip();

    private static final String KPI_OPEN_EXCEPTION_INVOICES_SQL = """
            SELECT count(*) AS open_exception_invoices FROM vw_invoice_match WHERE resolution = 'open'
            """.strip();

    private static final String KPI_BLOCKED_AMOUNT_SQL = """
            SELECT sum(gross_amount) AS blocked_amount FROM vw_invoice_match WHERE resolution = 'open'
            """.strip();

    // Weighted by orders: rows without a delivery (on_time_delivery_pct null) drop out of both sides.
    private static final String KPI_OVERALL_ON_TIME_DELIVERY_PCT_SQL = """
            SELECT round(sum(on_time_delivery_pct * orders)
                         / NULLIF(sum(orders) FILTER (WHERE on_time_delivery_pct IS NOT NULL), 0), 1) AS overall_on_time_pct
              FROM vw_supplier_scorecard
            """.strip();

    private final JdbcClient jdbc;
    private final SupplierRepository suppliers;

    public DashboardRepository(JdbcClient jdbc, SupplierRepository suppliers) {
        this.jdbc = jdbc;
        this.suppliers = suppliers;
    }

    public DashboardResult<List<SpendByMonthCategoryDto>> spendByMonth() {
        List<SpendByMonthCategoryDto> data = jdbc.sql(SPEND_BY_MONTH_SQL)
                .query((rs, rowNum) -> new SpendByMonthCategoryDto(
                        rs.getObject("order_month", java.time.LocalDate.class),
                        rs.getString("category_code"),
                        rs.getString("category_name"),
                        rs.getLong("orders"),
                        rs.getLong("items"),
                        rs.getBigDecimal("total_value"),
                        rs.getLong("suppliers")))
                .list();
        return new DashboardResult<>(data, SPEND_BY_MONTH_SQL);
    }

    public DashboardResult<List<ExceptionSummaryDto>> exceptionSummary() {
        List<ExceptionSummaryDto> data = jdbc.sql(EXCEPTION_SUMMARY_SQL)
                .query((rs, rowNum) -> new ExceptionSummaryDto(
                        rs.getString("exception_type"),
                        rs.getString("resolution"),
                        rs.getLong("invoice_count"),
                        rs.getBigDecimal("gross_amount")))
                .list();
        return new DashboardResult<>(data, EXCEPTION_SUMMARY_SQL);
    }

    public DashboardResult<List<LatePaymentsByMonthDto>> latePayments() {
        List<LatePaymentsByMonthDto> data = jdbc.sql(LATE_PAYMENTS_SQL)
                .query((rs, rowNum) -> new LatePaymentsByMonthDto(
                        rs.getObject("due_month", java.time.LocalDate.class),
                        rs.getLong("payments"),
                        rs.getLong("late_payments"),
                        rs.getBigDecimal("avg_days_late")))
                .list();
        return new DashboardResult<>(data, LATE_PAYMENTS_SQL);
    }

    public DashboardResult<List<StaleInvoiceDto>> staleInvoices() {
        List<StaleInvoiceDto> data = jdbc.sql(STALE_INVOICES_SQL)
                .query((rs, rowNum) -> new StaleInvoiceDto(
                        rs.getString("invoice_number"),
                        rs.getBigDecimal("amount"),
                        rs.getLong("age_days"),
                        rs.getString("age_band"),
                        rs.getString("block_reason")))
                .list();
        return new DashboardResult<>(data, STALE_INVOICES_SQL);
    }

    public DashboardResult<List<SupplierScorecardDto>> supplierScorecard() {
        return new DashboardResult<>(suppliers.findScorecard(), SupplierRepository.SCORECARD_SQL);
    }

    public DashboardKpisDto kpis() {
        BigDecimal totalSpend = jdbc.sql(KPI_TOTAL_SPEND_SQL).query(BigDecimal.class).single();
        long orders = jdbc.sql(KPI_ORDERS_SQL).query(Long.class).single();
        long openExceptionInvoices = jdbc.sql(KPI_OPEN_EXCEPTION_INVOICES_SQL).query(Long.class).single();
        BigDecimal blockedAmount = jdbc.sql(KPI_BLOCKED_AMOUNT_SQL).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);
        BigDecimal overallOnTimeDeliveryPct = jdbc.sql(KPI_OVERALL_ON_TIME_DELIVERY_PCT_SQL).query(BigDecimal.class).single();

        return new DashboardKpisDto(
                new DashboardResult<>(totalSpend, KPI_TOTAL_SPEND_SQL),
                new DashboardResult<>(orders, KPI_ORDERS_SQL),
                new DashboardResult<>(openExceptionInvoices, KPI_OPEN_EXCEPTION_INVOICES_SQL),
                new DashboardResult<>(blockedAmount, KPI_BLOCKED_AMOUNT_SQL),
                new DashboardResult<>(overallOnTimeDeliveryPct, KPI_OVERALL_ON_TIME_DELIVERY_PCT_SQL));
    }
}
