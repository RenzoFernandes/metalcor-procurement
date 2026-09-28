package com.metalcor.procurement.dashboard;

import com.metalcor.procurement.supplier.SupplierScorecardDto;
import java.util.List;

/** Everything the Excel export needs, read in one transaction so all sheets reflect the same snapshot. */
public record DashboardExportBundle(
        DashboardKpisDto kpis,
        DashboardResult<List<SpendByMonthCategoryDto>> spendByMonth,
        DashboardResult<List<ExceptionSummaryDto>> exceptionSummary,
        DashboardResult<List<LatePaymentsByMonthDto>> latePayments,
        DashboardResult<List<SupplierScorecardDto>> supplierScorecard,
        DashboardResult<List<StaleInvoiceDto>> staleInvoices) {
}