package com.metalcor.procurement.dashboard;

import com.metalcor.procurement.supplier.SupplierScorecardDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints behind the manager panel and its technical mode. Every response carries the
 * exact SQL that produced it, so the technical mode can show it without a second, hand-kept copy.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardRepository dashboard;
    private final ExcelExportService excelExport;

    public DashboardController(DashboardRepository dashboard, ExcelExportService excelExport) {
        this.dashboard = dashboard;
        this.excelExport = excelExport;
    }

    @GetMapping("/spend-by-month")
    @Operation(summary = "Spend by month and category",
            description = "Orders, items, total value and number of suppliers per order month and material category (view vw_spend_by_month_category), ordered by month.")
    public DashboardResult<List<SpendByMonthCategoryDto>> spendByMonth() {
        return dashboard.spendByMonth();
    }

    @GetMapping("/exception-summary")
    @Operation(summary = "Match exceptions by type and resolution",
            description = "Invoice count and gross amount per exception type and resolution, aggregated across all months (view vw_match_exception_summary), for the open x released chart.")
    public DashboardResult<List<ExceptionSummaryDto>> exceptionSummary() {
        return dashboard.exceptionSummary();
    }

    @GetMapping("/late-payments")
    @Operation(summary = "Payment timeliness by month",
            description = "Per month of the invoice due date: total payments, how many were late and the average days late of the late ones (view vw_payment_timeliness).")
    public DashboardResult<List<LatePaymentsByMonthDto>> latePayments() {
        return dashboard.latePayments();
    }

    @GetMapping("/stale-invoices")
    @Operation(summary = "Stale blocked invoices",
            description = "Blocked invoices open for more than 45 days due to a match exception (view vw_stale_blocked_invoices, is_stale and not a duplicate candidate), ordered by age descending.")
    public DashboardResult<List<StaleInvoiceDto>> staleInvoices() {
        return dashboard.staleInvoices();
    }

    @GetMapping("/supplier-scorecard")
    @Operation(summary = "Supplier scorecard (with SQL)",
            description = "Same data as GET /suppliers/scorecard, wrapped with the SQL that produced it, for the technical mode.")
    public DashboardResult<List<SupplierScorecardDto>> supplierScorecard() {
        return dashboard.supplierScorecard();
    }

    @GetMapping("/kpis")
    @Operation(summary = "Key numbers",
            description = "Total spend, number of orders, invoices with an open match exception, blocked amount and overall on-time delivery rate. Each number comes from its own query and carries its own SQL.")
    public DashboardKpisDto kpis() {
        return dashboard.kpis();
    }

    @GetMapping("/export.xlsx")
    @Operation(summary = "Export the manager panel as an Excel workbook",
            description = "Same data and SQL behind the panel (kpis, spend-by-month, exception-summary, late-payments, "
                    + "supplier-scorecard and stale-invoices), as a downloadable .xlsx with one sheet per topic plus a SQL sheet.")
    public ResponseEntity<byte[]> exportXlsx() {
        DashboardExportBundle bundle = dashboard.exportBundle();
        byte[] workbook = excelExport.build(bundle, LocalDate.now());
        String fileName = ExcelExportService.fileName(LocalDate.now());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(workbook);
    }
}