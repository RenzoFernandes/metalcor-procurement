package com.metalcor.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;
import net.minidev.json.JSONArray;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/** Endpoints of the manager panel (GET /api/v1/dashboard/**). Read-only; X-User-Id is added by DefaultUserHeaderConfig. */
@Order(0)
class DashboardTest extends AbstractIntegrationTest {

    private static final String BASE = "/api/v1/dashboard";
    private static final Pattern SQL_START = Pattern.compile("^(SELECT|WITH)\\b", Pattern.CASE_INSENSITIVE);

    @Autowired
    JdbcClient jdbc;

    @Test
    void spendByMonthSumsToTheSameTotalAsPurchaseOrderItems() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/spend-by-month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn();
        assertSqlField(result);

        JSONArray totals = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].totalValue");
        BigDecimal fromEndpoint = totals.stream()
                .map(v -> new BigDecimal(v.toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal fromDatabase = jdbc.sql("SELECT sum(line_total) FROM purchase_order_items")
                .query(BigDecimal.class)
                .single();

        assertThat(fromEndpoint).isEqualByComparingTo(fromDatabase);
    }

    @Test
    void exceptionSummaryMatchesCurrentSeedCounts() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/exception-summary"))
                .andExpect(status().isOk())
                .andReturn();
        assertSqlField(result);

        String json = result.getResponse().getContentAsString();
        assertThat(invoiceCount(json, "price_variance", "open")).isEqualTo(6);
        assertThat(invoiceCount(json, "price_variance", "released")).isEqualTo(67);
        assertThat(invoiceCount(json, "quantity_variance", "open")).isEqualTo(2);
        assertThat(invoiceCount(json, "quantity_variance", "released")).isEqualTo(16);
        assertThat(invoiceCount(json, "invoice_before_receipt", "released")).isEqualTo(25);

        assertThat(invoiceCount(json, "price_variance", "open") + invoiceCount(json, "price_variance", "released"))
                .isEqualTo(73);
        assertThat(invoiceCount(json, "quantity_variance", "open") + invoiceCount(json, "quantity_variance", "released"))
                .isEqualTo(18);
    }

    @Test
    void latePaymentsRespondsWithData() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/late-payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[0].dueMonth").isNotEmpty())
                .andReturn();
        assertSqlField(result);
    }

    @Test
    void staleInvoicesRespondsWithDataOrderedByAgeDescending() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/stale-invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn();
        assertSqlField(result);

        JSONArray ages = JsonPath.read(result.getResponse().getContentAsString(), "$.data[*].ageDays");
        List<Long> ageDays = ages.stream().map(a -> ((Number) a).longValue()).toList();
        assertThat(ageDays).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    @Test
    void supplierScorecardRespondsWithTwelveSuppliersAndSql() throws Exception {
        MvcResult result = mockMvc.perform(get(BASE + "/supplier-scorecard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12))
                .andExpect(jsonPath("$.data[0].supplierCode").isNotEmpty())
                .andReturn();
        assertSqlField(result);
    }

    @Test
    void kpisRespondsWithFiveNumbersEachCarryingItsOwnSql() throws Exception {
        mockMvc.perform(get(BASE + "/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpend.data").isNumber())
                .andExpect(jsonPath("$.orders.data").isNumber())
                .andExpect(jsonPath("$.openExceptionInvoices.data").isNumber())
                .andExpect(jsonPath("$.blockedAmount.data").isNumber())
                .andExpect(jsonPath("$.overallOnTimeDeliveryPct.data").isNumber())
                .andExpect(jsonPath("$.totalSpend.sql", sqlMatcher()))
                .andExpect(jsonPath("$.orders.sql", sqlMatcher()))
                .andExpect(jsonPath("$.openExceptionInvoices.sql", sqlMatcher()))
                .andExpect(jsonPath("$.blockedAmount.sql", sqlMatcher()))
                .andExpect(jsonPath("$.overallOnTimeDeliveryPct.sql", sqlMatcher()));
    }

    private static long invoiceCount(String json, String exceptionType, String resolution) {
        JSONArray values = JsonPath.read(json, String.format(
                "$.data[?(@.exceptionType=='%s' && @.resolution=='%s')].invoiceCount", exceptionType, resolution));
        assertThat(values).as(exceptionType + "/" + resolution).hasSize(1);
        return ((Number) values.get(0)).longValue();
    }

    private static void assertSqlField(MvcResult result) throws Exception {
        String sql = JsonPath.read(result.getResponse().getContentAsString(), "$.sql");
        assertThat(sql).isNotBlank();
        assertThat(SQL_START.matcher(sql.strip()).find()).as("sql starts with SELECT or WITH: " + sql).isTrue();
        assertThat(sql).as("sql has no semicolon in the middle: " + sql).doesNotContain(";");
    }

    private static org.hamcrest.Matcher<Object> sqlMatcher() {
        return org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.notNullValue(),
                new org.hamcrest.CustomMatcher<>("a SELECT or WITH statement with no semicolon") {
                    @Override
                    public boolean matches(Object actual) {
                        return actual instanceof String s && !s.isBlank()
                                && SQL_START.matcher(s.strip()).find() && !s.contains(";");
                    }
                });
    }
}
