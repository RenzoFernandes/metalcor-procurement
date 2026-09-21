package com.metalcor.procurement.supplier;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class SupplierRepository {

    // The view already orders by total spend, then supplier code.
    private static final String SCORECARD_SQL = """
            SELECT supplier_code, supplier_name, orders, total_spend, on_time_delivery_pct, avg_days_late,
                   invoices, exception_rate_pct, avg_days_to_resolve, open_exceptions
              FROM vw_supplier_scorecard
            """;

    private final JdbcClient jdbc;

    public SupplierRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<SupplierScorecardDto> findScorecard() {
        return jdbc.sql(SCORECARD_SQL)
                .query((rs, rowNum) -> new SupplierScorecardDto(
                        rs.getString("supplier_code"),
                        rs.getString("supplier_name"),
                        rs.getLong("orders"),
                        rs.getBigDecimal("total_spend"),
                        rs.getBigDecimal("on_time_delivery_pct"),
                        rs.getBigDecimal("avg_days_late"),
                        rs.getLong("invoices"),
                        rs.getBigDecimal("exception_rate_pct"),
                        rs.getBigDecimal("avg_days_to_resolve"),
                        rs.getLong("open_exceptions")))
                .list();
    }
}
