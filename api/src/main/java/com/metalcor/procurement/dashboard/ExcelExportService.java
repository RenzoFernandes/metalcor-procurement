package com.metalcor.procurement.dashboard;

import com.metalcor.procurement.supplier.SupplierScorecardDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Builds the manager panel as a workbook, reusing the same data and SQL the API already exposes.
 * One method per sheet, laid out from an export bundle read in a single read-only transaction.
 */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final byte[] NAVY = {(byte) 0x1F, (byte) 0x2A, (byte) 0x44};
    private static final byte[] AMBER = {(byte) 0xFD, (byte) 0xE9, (byte) 0xC8};
    private static final byte[] KPI_LABEL_BG = {(byte) 0xF2, (byte) 0xF4, (byte) 0xF7};
    private static final byte[] BORDER_GRAY = {(byte) 0xD9, (byte) 0xD9, (byte) 0xD9};
    private static final byte[] ZEBRA_GRAY = {(byte) 0xF7, (byte) 0xF8, (byte) 0xFA};

    private static final byte[] TAB_RESUMO = NAVY;
    private static final byte[] TAB_SPEND = {(byte) 0x44, (byte) 0x72, (byte) 0xC4};
    private static final byte[] TAB_EXCEPTIONS = {(byte) 0xE8, (byte) 0xA3, (byte) 0x3D};
    private static final byte[] TAB_LATE = {(byte) 0xD9, (byte) 0x82, (byte) 0x2B};
    private static final byte[] TAB_SUPPLIERS = {(byte) 0x2E, (byte) 0x8B, (byte) 0x8B};
    private static final byte[] TAB_STALE = {(byte) 0xC4, (byte) 0x7A, (byte) 0x0E};
    private static final byte[] TAB_SQL = {(byte) 0x8C, (byte) 0x8C, (byte) 0x8C};

    public byte[] build(DashboardExportBundle bundle, LocalDate generatedAt) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);

            buildSummarySheet(workbook, styles, bundle.kpis(), generatedAt);
            buildSpendByMonthSheet(workbook, styles, bundle.spendByMonth().data());
            buildExceptionsSheet(workbook, styles, bundle.exceptionSummary().data());
            buildLatePaymentsSheet(workbook, styles, bundle.latePayments().data());
            buildSuppliersSheet(workbook, styles, bundle.supplierScorecard().data());
            buildStaleInvoicesSheet(workbook, styles, bundle.staleInvoices().data());
            buildSqlSheet(workbook, styles, bundle);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String fileName(LocalDate today) {
        return "metalcor-painel-" + FILE_DATE.format(today) + ".xlsx";
    }

    // ---- Resumo ------------------------------------------------------------------------------

    private void buildSummarySheet(XSSFWorkbook workbook, Styles styles, DashboardKpisDto kpis, LocalDate generatedAt) {
        Sheet sheet = workbook.createSheet("Resumo");
        setTabColor(sheet, TAB_RESUMO);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28);
        Cell title = titleRow.createCell(0);
        title.setCellValue("Metalcor Autopeças – Painel de Compras");
        title.setCellStyle(styles.title);
        for (int c = 1; c <= 4; c++) {
            Cell fill = titleRow.createCell(c);
            fill.setCellStyle(styles.title);
        }
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        Row noticeRow = sheet.createRow(1);
        Cell notice = noticeRow.createCell(0);
        notice.setCellValue("Dados fictícios");
        notice.setCellStyle(styles.notice);

        Row dateRow = sheet.createRow(2);
        Cell dateNote = dateRow.createCell(0);
        dateNote.setCellValue("Gerado em " + generatedAt);
        dateNote.setCellStyle(styles.notice);

        String[] labels = {
                "Gasto total", "Pedidos", "Faturas com exceção aberta", "Valor bloqueado", "Pontualidade média de entrega",
        };
        BigDecimal[] values = {
                kpis.totalSpend().data(),
                BigDecimal.valueOf(kpis.orders().data()),
                BigDecimal.valueOf(kpis.openExceptionInvoices().data()),
                kpis.blockedAmount().data(),
                kpis.overallOnTimeDeliveryPct().data(),
        };
        CellStyle[] valueStyles = {styles.money, styles.integer, styles.integer, styles.money, styles.percent};

        int[][] cardCols = {{0, 1}, {3, 4}};
        int startRow = 4;
        for (int i = 0; i < labels.length; i++) {
            int gridRow = i / 2;
            int gridCol = i % 2;
            int labelRowIdx = startRow + gridRow * 3;
            int valueRowIdx = labelRowIdx + 1;
            int colFrom = cardCols[gridCol][0];
            int colTo = cardCols[gridCol][1];
            kpiCard(sheet, styles, labelRowIdx, valueRowIdx, colFrom, colTo, labels[i], values[i], valueStyles[i]);
        }

        for (int c = 0; c <= 4; c++) {
            sheet.setColumnWidth(c, (c == 2 ? 4 : 22) * 256);
        }
    }

    private void kpiCard(Sheet sheet, Styles styles, int labelRowIdx, int valueRowIdx, int colFrom, int colTo,
            String label, BigDecimal value, CellStyle valueFormat) {
        Row labelRow = sheet.getRow(labelRowIdx) == null ? sheet.createRow(labelRowIdx) : sheet.getRow(labelRowIdx);
        labelRow.setHeightInPoints(18);
        Cell labelCell = labelRow.createCell(colFrom);
        labelCell.setCellValue(label.toUpperCase());
        labelCell.setCellStyle(styles.kpiLabel);
        for (int c = colFrom + 1; c <= colTo; c++) {
            labelRow.createCell(c).setCellStyle(styles.kpiLabel);
        }
        sheet.addMergedRegion(new CellRangeAddress(labelRowIdx, labelRowIdx, colFrom, colTo));

        Row valueRow = sheet.getRow(valueRowIdx) == null ? sheet.createRow(valueRowIdx) : sheet.getRow(valueRowIdx);
        valueRow.setHeightInPoints(30);
        Cell valueCell = valueRow.createCell(colFrom);
        valueCell.setCellValue(value == null ? 0d : value.doubleValue());
        CellStyle bigValueStyle = sheet.getWorkbook().createCellStyle();
        bigValueStyle.cloneStyleFrom(valueFormat);
        bigValueStyle.setFont(styles.kpiValueFont);
        valueCell.setCellStyle(bigValueStyle);
        for (int c = colFrom + 1; c <= colTo; c++) {
            valueRow.createCell(c).setCellStyle(bigValueStyle);
        }
        sheet.addMergedRegion(new CellRangeAddress(valueRowIdx, valueRowIdx, colFrom, colTo));

        CellRangeAddress box = new CellRangeAddress(labelRowIdx, valueRowIdx, colFrom, colTo);
        RegionUtil.setBorderTop(BorderStyle.THIN, box, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, box, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, box, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, box, sheet);
        RegionUtil.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex(), box, sheet);
        RegionUtil.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex(), box, sheet);
        RegionUtil.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex(), box, sheet);
        RegionUtil.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex(), box, sheet);
    }

    // ---- Gasto por mês -------------------------------------------------------------------------

    private void buildSpendByMonthSheet(XSSFWorkbook workbook, Styles styles, List<SpendByMonthCategoryDto> data) {
        Sheet sheet = workbook.createSheet("Gasto por mês");
        setTabColor(sheet, TAB_SPEND);
        String[] headers = {"Mês", "Categoria", "Nome da categoria", "Pedidos", "Itens", "Valor total", "Fornecedores"};
        writeHeader(sheet, styles, headers);

        int rowNum = 1;
        for (SpendByMonthCategoryDto d : data) {
            Row row = sheet.createRow(rowNum++);
            setDate(row, 0, d.orderMonth(), styles.date);
            setText(row, 1, d.categoryCode());
            setText(row, 2, d.categoryName());
            setInt(row, 3, d.orders(), styles.integer);
            setInt(row, 4, d.items(), styles.integer);
            setMoney(row, 5, d.totalValue(), styles.money);
            setInt(row, 6, d.suppliers(), styles.integer);
        }
        finishDataSheet(sheet, headers.length, rowNum);
        applyGridAndZebra(sheet, styles, headers.length, rowNum);
        setWidths(sheet, 12, 12, 26, 10, 10, 16, 12);
    }

    // ---- Exceções --------------------------------------------------------------------------

    private void buildExceptionsSheet(XSSFWorkbook workbook, Styles styles, List<ExceptionSummaryDto> data) {
        Sheet sheet = workbook.createSheet("Exceções");
        setTabColor(sheet, TAB_EXCEPTIONS);
        String[] headers = {"Tipo de exceção", "Resolução", "Faturas", "Valor bruto"};
        writeHeader(sheet, styles, headers);

        int rowNum = 1;
        for (ExceptionSummaryDto d : data) {
            Row row = sheet.createRow(rowNum++);
            setText(row, 0, d.exceptionType());
            setText(row, 1, d.resolution());
            setInt(row, 2, d.invoiceCount(), styles.integer);
            setMoney(row, 3, d.grossAmount(), styles.money);
        }
        finishDataSheet(sheet, headers.length, rowNum);
        applyGridAndZebra(sheet, styles, headers.length, rowNum);
        setWidths(sheet, 22, 14, 12, 16);
    }

    // ---- Atrasos ---------------------------------------------------------------------------

    private void buildLatePaymentsSheet(XSSFWorkbook workbook, Styles styles, List<LatePaymentsByMonthDto> data) {
        Sheet sheet = workbook.createSheet("Atrasos");
        setTabColor(sheet, TAB_LATE);
        String[] headers = {"Mês de vencimento", "Pagamentos", "Pagamentos atrasados", "Média de dias de atraso"};
        writeHeader(sheet, styles, headers);

        int rowNum = 1;
        for (LatePaymentsByMonthDto d : data) {
            Row row = sheet.createRow(rowNum++);
            setDate(row, 0, d.dueMonth(), styles.date);
            setInt(row, 1, d.payments(), styles.integer);
            setInt(row, 2, d.latePayments(), styles.integer);
            if (d.avgDaysLate() != null) {
                setMoneyLikeNumber(row, 3, d.avgDaysLate(), styles.number);
            }
        }
        finishDataSheet(sheet, headers.length, rowNum);
        applyGridAndZebra(sheet, styles, headers.length, rowNum);
        setWidths(sheet, 16, 12, 18, 20);
    }

    // ---- Fornecedores -----------------------------------------------------------------------

    private void buildSuppliersSheet(XSSFWorkbook workbook, Styles styles, List<SupplierScorecardDto> data) {
        Sheet sheet = workbook.createSheet("Fornecedores");
        setTabColor(sheet, TAB_SUPPLIERS);
        String[] headers = {"Código", "Nome", "Pedidos", "Gasto total", "% Entregas no prazo", "Média de dias de atraso",
                "Faturas", "% Taxa de exceção", "Média de dias p/ resolver", "Exceções abertas"};
        writeHeader(sheet, styles, headers);

        int rowNum = 1;
        for (SupplierScorecardDto d : data) {
            Row row = sheet.createRow(rowNum++);
            setText(row, 0, d.supplierCode());
            setText(row, 1, d.supplierName());
            setInt(row, 2, d.orders(), styles.integer);
            setMoney(row, 3, d.totalSpend(), styles.money);
            if (d.onTimeDeliveryPct() != null) {
                setPercent(row, 4, d.onTimeDeliveryPct(), styles.percent);
            }
            if (d.avgDaysLate() != null) {
                setMoneyLikeNumber(row, 5, d.avgDaysLate(), styles.number);
            }
            setInt(row, 6, d.invoices(), styles.integer);
            setPercent(row, 7, d.exceptionRatePct(), styles.percent);
            if (d.avgDaysToResolve() != null) {
                setMoneyLikeNumber(row, 8, d.avgDaysToResolve(), styles.number);
            }
            setInt(row, 9, d.openExceptions(), styles.integer);
        }
        finishDataSheet(sheet, headers.length, rowNum);
        applyGridAndZebra(sheet, styles, headers.length, rowNum);
        setWidths(sheet, 10, 26, 10, 16, 18, 20, 10, 16, 22, 16);

        if (rowNum > 1) {
            addOnTimeDeliveryDataBar(sheet, rowNum);
        }
    }

    private void addOnTimeDeliveryDataBar(Sheet sheet, int rowNum) {
        SheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();
        ConditionalFormattingRule rule = scf.createConditionalFormattingRule(new XSSFColor(TAB_SUPPLIERS, null));
        CellRangeAddress[] regions = {new CellRangeAddress(1, rowNum - 1, 4, 4)};
        scf.addConditionalFormatting(regions, rule);
    }

    // ---- Faturas paradas --------------------------------------------------------------------

    private void buildStaleInvoicesSheet(XSSFWorkbook workbook, Styles styles, List<StaleInvoiceDto> data) {
        Sheet sheet = workbook.createSheet("Faturas paradas");
        setTabColor(sheet, TAB_STALE);
        String[] headers = {"Número da fatura", "Valor", "Dias em aberto", "Faixa de idade", "Motivo do bloqueio"};
        writeHeader(sheet, styles, headers);

        int rowNum = 1;
        for (StaleInvoiceDto d : data) {
            boolean old = d.ageDays() > 90;
            Row row = sheet.createRow(rowNum++);
            setText(row, 0, d.invoiceNumber(), old ? styles.amberText : null);
            setMoney(row, 1, d.amount(), old ? styles.amberMoney : styles.money);
            setInt(row, 2, d.ageDays(), old ? styles.amberInteger : styles.integer);
            setText(row, 3, d.ageBand(), old ? styles.amberText : null);
            setText(row, 4, d.blockReason(), old ? styles.amberText : null);
        }
        finishDataSheet(sheet, headers.length, rowNum);
        applyGridAndZebra(sheet, styles, headers.length, rowNum);
        setWidths(sheet, 18, 16, 14, 14, 30);
    }

    // ---- SQL ---------------------------------------------------------------------------------

    private void buildSqlSheet(XSSFWorkbook workbook, Styles styles, DashboardExportBundle bundle) {
        Sheet sheet = workbook.createSheet("SQL");
        setTabColor(sheet, TAB_SQL);
        String[] headers = {"Consulta", "SQL"};
        writeHeader(sheet, styles, headers);

        Object[][] queries = {
                {"Resumo – gasto total", bundle.kpis().totalSpend().sql()},
                {"Resumo – pedidos", bundle.kpis().orders().sql()},
                {"Resumo – faturas com exceção aberta", bundle.kpis().openExceptionInvoices().sql()},
                {"Resumo – valor bloqueado", bundle.kpis().blockedAmount().sql()},
                {"Resumo – pontualidade média de entrega", bundle.kpis().overallOnTimeDeliveryPct().sql()},
                {"Gasto por mês", bundle.spendByMonth().sql()},
                {"Exceções", bundle.exceptionSummary().sql()},
                {"Atrasos", bundle.latePayments().sql()},
                {"Fornecedores", bundle.supplierScorecard().sql()},
                {"Faturas paradas", bundle.staleInvoices().sql()},
        };

        int rowNum = 1;
        for (Object[] q : queries) {
            Row row = sheet.createRow(rowNum++);
            setText(row, 0, (String) q[0]);
            Cell sqlCell = row.createCell(1);
            sqlCell.setCellValue((String) q[1]);
            sqlCell.setCellStyle(styles.wrapped);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, headers.length - 1));
        sheet.setColumnWidth(0, 32 * 256);
        sheet.setColumnWidth(1, 100 * 256);
    }

    // ---- Shared helpers ------------------------------------------------------------------------

    private void setTabColor(Sheet sheet, byte[] rgb) {
        if (sheet instanceof XSSFSheet xssfSheet) {
            xssfSheet.setTabColor(new XSSFColor(rgb, null));
        }
    }

    private void writeHeader(Sheet sheet, Styles styles, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
    }

    private void finishDataSheet(Sheet sheet, int columnCount, int rowCount) {
        sheet.createFreezePane(0, 1);
        if (rowCount > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, columnCount - 1));
        }
    }

    /**
     * Adds a light-gray grid border to every data cell and zebra striping to alternate rows,
     * applied after the sheet is fully populated so it never overrides an existing conditional
     * highlight such as the amber fill on stale invoices.
     */
    private void applyGridAndZebra(Sheet sheet, Styles styles, int columnCount, int rowCount) {
        for (int r = 1; r < rowCount; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            boolean stripe = r % 2 == 0;
            for (int c = 0; c < columnCount; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                CellStyle base = cell.getCellStyle();
                boolean hasFill = base != null && base.getFillPattern() == FillPatternType.SOLID_FOREGROUND;
                CellStyle gridded = styles.griddedVariant(base, stripe && !hasFill);
                cell.setCellStyle(gridded);
            }
        }
        CellRangeAddress headerRange = new CellRangeAddress(0, 0, 0, columnCount - 1);
        RegionUtil.setBorderBottom(BorderStyle.MEDIUM, headerRange, sheet);
        RegionUtil.setBottomBorderColor(IndexedColors.WHITE.getIndex(), headerRange, sheet);
    }

    private void setWidths(Sheet sheet, int... charWidths) {
        for (int i = 0; i < charWidths.length; i++) {
            sheet.setColumnWidth(i, charWidths[i] * 256);
        }
    }

    private void setText(Row row, int col, String value) {
        row.createCell(col).setCellValue(value == null ? "" : value);
    }

    private void setText(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private void setInt(Row row, int col, long value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setMoney(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void setMoneyLikeNumber(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }

    private void setPercent(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? 0d : value.doubleValue());
        cell.setCellStyle(style);
    }

    private void setDate(Row row, int col, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
        }
        cell.setCellStyle(style);
    }

    /** Cell styles built once per workbook and reused across sheets. */
    private static final class Styles {
        final XSSFWorkbook workbook;
        final CellStyle title;
        final CellStyle notice;
        final CellStyle kpiLabel;
        final XSSFFont kpiValueFont;
        final CellStyle header;
        final CellStyle money;
        final CellStyle percent;
        final CellStyle integer;
        final CellStyle number;
        final CellStyle date;
        final CellStyle wrapped;
        final CellStyle amberText;
        final CellStyle amberMoney;
        final CellStyle amberInteger;
        private final java.util.Map<CacheKey, CellStyle> griddedCache = new java.util.HashMap<>();

        Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;

            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(new XSSFColor(NAVY, null));
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setAlignment(HorizontalAlignment.LEFT);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            title = titleStyle;

            Font noticeFont = workbook.createFont();
            noticeFont.setItalic(true);
            noticeFont.setFontHeightInPoints((short) 9);
            noticeFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            notice = workbook.createCellStyle();
            notice.setFont(noticeFont);

            Font kpiLabelFont = workbook.createFont();
            kpiLabelFont.setFontHeightInPoints((short) 10);
            kpiLabelFont.setBold(true);
            kpiLabelFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            XSSFCellStyle kpiLabelStyle = workbook.createCellStyle();
            kpiLabelStyle.setFont(kpiLabelFont);
            kpiLabelStyle.setFillForegroundColor(new XSSFColor(KPI_LABEL_BG, null));
            kpiLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            kpiLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            kpiLabel = kpiLabelStyle;

            kpiValueFont = workbook.createFont();
            kpiValueFont.setBold(true);
            kpiValueFont.setFontHeightInPoints((short) 20);
            kpiValueFont.setColor(new XSSFColor(NAVY, null));

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle navyHeader = workbook.createCellStyle();
            navyHeader.setFont(headerFont);
            navyHeader.setFillForegroundColor(new XSSFColor(NAVY, null));
            navyHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            navyHeader.setAlignment(HorizontalAlignment.CENTER);
            header = navyHeader;

            org.apache.poi.ss.usermodel.DataFormat format = workbook.createDataFormat();

            money = workbook.createCellStyle();
            money.setDataFormat(format.getFormat("\"R$\" #,##0.00"));

            percent = workbook.createCellStyle();
            percent.setDataFormat(format.getFormat("0.0\"%\""));

            integer = workbook.createCellStyle();
            integer.setDataFormat(format.getFormat("#,##0"));

            number = workbook.createCellStyle();
            number.setDataFormat(format.getFormat("#,##0.0"));

            date = workbook.createCellStyle();
            date.setDataFormat(format.getFormat("dd/mm/yyyy"));

            wrapped = workbook.createCellStyle();
            wrapped.setWrapText(true);
            wrapped.setVerticalAlignment(VerticalAlignment.TOP);

            XSSFCellStyle amberFill = workbook.createCellStyle();
            amberFill.setFillForegroundColor(new XSSFColor(AMBER, null));
            amberFill.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            amberText = amberFill;

            XSSFCellStyle amberMoneyStyle = workbook.createCellStyle();
            amberMoneyStyle.cloneStyleFrom(money);
            amberMoneyStyle.setFillForegroundColor(new XSSFColor(AMBER, null));
            amberMoneyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            amberMoney = amberMoneyStyle;

            XSSFCellStyle amberIntegerStyle = workbook.createCellStyle();
            amberIntegerStyle.cloneStyleFrom(integer);
            amberIntegerStyle.setFillForegroundColor(new XSSFColor(AMBER, null));
            amberIntegerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            amberInteger = amberIntegerStyle;
        }

        /**
         * Returns a variant of {@code base} with a light-gray outline on all four sides and,
         * optionally, zebra-stripe shading — reusing a cached style per (base, stripe) pair so the
         * workbook does not accumulate a new style object for every cell.
         */
        CellStyle griddedVariant(CellStyle base, boolean stripe) {
            CacheKey key = new CacheKey(base == null ? -1 : base.getIndex(), stripe);
            return griddedCache.computeIfAbsent(key, k -> {
                XSSFCellStyle variant = workbook.createCellStyle();
                if (base != null) {
                    variant.cloneStyleFrom(base);
                }
                variant.setBorderTop(BorderStyle.THIN);
                variant.setBorderBottom(BorderStyle.THIN);
                variant.setBorderLeft(BorderStyle.THIN);
                variant.setBorderRight(BorderStyle.THIN);
                variant.setTopBorderColor(new XSSFColor(BORDER_GRAY, null));
                variant.setBottomBorderColor(new XSSFColor(BORDER_GRAY, null));
                variant.setLeftBorderColor(new XSSFColor(BORDER_GRAY, null));
                variant.setRightBorderColor(new XSSFColor(BORDER_GRAY, null));
                if (stripe) {
                    variant.setFillForegroundColor(new XSSFColor(ZEBRA_GRAY, null));
                    variant.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                return variant;
            });
        }

        private record CacheKey(int baseStyleIndex, boolean stripe) {
        }
    }
}
