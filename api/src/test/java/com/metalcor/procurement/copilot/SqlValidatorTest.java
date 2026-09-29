package com.metalcor.procurement.copilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.metalcor.procurement.common.BadRequestException;
import org.junit.jupiter.api.Test;

/** Unit tests for the validator alone, no Spring context and no database. */
class SqlValidatorTest {

    @Test
    void validSelectPassesAndKeepsItsLimit() {
        String sql = SqlValidator.validateAndLimit("select * from vw_invoice_match limit 10");
        assertThat(sql).isEqualTo("select * from vw_invoice_match limit 10");
    }

    @Test
    void addsDefaultLimitWhenMissing() {
        String sql = SqlValidator.validateAndLimit("select * from vw_invoice_match");
        assertThat(sql).endsWith("LIMIT 50");
    }

    @Test
    void toleratesASingleTrailingSemicolon() {
        String sql = SqlValidator.validateAndLimit("select * from vw_invoice_match;");
        assertThat(sql).doesNotContain(";");
    }

    @Test
    void rejectsMultipleStatements() {
        assertThatThrownBy(() -> SqlValidator.validateAndLimit(
                "select * from vw_invoice_match; select * from app_users"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsNonSelectStatements() {
        assertThatThrownBy(() -> SqlValidator.validateAndLimit("update materials set price = 1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsForbiddenWordsEvenInsideASelect() {
        assertThatThrownBy(() -> SqlValidator.validateAndLimit(
                "select * from materials where 1=1; drop table materials"))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> SqlValidator.validateAndLimit("delete from materials"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void blankOrNullSqlIsRejected() {
        assertThatThrownBy(() -> SqlValidator.validateAndLimit(""))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SqlValidator.validateAndLimit(null))
                .isInstanceOf(BadRequestException.class);
    }
}