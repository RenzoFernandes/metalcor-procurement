package com.metalcor.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** GET /api/v1/dashboard/export.xlsx: the manager panel as a downloadable workbook. */
@Order(0)
class DashboardExportTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/dashboard";
    private static final List<String> EXPECTED_SHEETS = List.of(
            "Resumo", "Gasto por mês", "Exceções", "Atrasos", "Fornecedores", "Faturas paradas", "SQL");

    @Test
    void exportRespondsWithXlsxContentTypeAndFileName() throws Exception {
        mockMvc.perform(get(BASE + "/export.xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("metalcor-painel-")));
    }

    @Test
    void workbookHasAllSevenSheets() throws Exception {
        byte[] bytes = mockMvc.perform(get(BASE + "/export.xlsx"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(EXPECTED_SHEETS.size());
            for (String name : EXPECTED_SHEETS) {
                assertThat(workbook.getSheet(name)).as("sheet " + name).isNotNull();
            }
        }
    }

    @Test
    void spendByMonthColumnSumsToTheSameKpiTotal() throws Exception {
        MvcResult kpiResult = mockMvc.perform(get(BASE + "/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend.data").isNumber())
                .andReturn();
        BigDecimal kpiTotal = new BigDecimal(
                JsonPath.read(kpiResult.getResponse().getContentAsString(), "$.totalSpend.data").toString());

        byte[] bytes = mockMvc.perform(get(BASE + "/export.xlsx"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Gasto por mês");
            BigDecimal sum = BigDecimal.ZERO;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Cell valueCell = row.getCell(5);
                if (valueCell != null) {
                    sum = sum.add(BigDecimal.valueOf(valueCell.getNumericCellValue()));
                }
            }
            assertThat(sum.setScale(2, RoundingMode.HALF_UP))
                    .isEqualByComparingTo(kpiTotal.setScale(2, RoundingMode.HALF_UP));
        }
    }

    @Test
    void sqlSheetHasOneNonEmptyRowPerQuery() throws Exception {
        byte[] bytes = mockMvc.perform(get(BASE + "/export.xlsx"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("SQL");
            assertThat(sheet.getLastRowNum()).isGreaterThan(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                assertThat(row).as("row " + r).isNotNull();
                assertThat(row.getCell(0).getStringCellValue()).as("query name, row " + r).isNotBlank();
                assertThat(row.getCell(1).getStringCellValue()).as("sql, row " + r).isNotBlank();
            }
        }
    }
}