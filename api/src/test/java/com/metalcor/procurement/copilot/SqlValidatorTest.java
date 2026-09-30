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

    @Test
    void rejectsNewForbiddenFeatures() {
        for (String sql : new String[] {
                "copy materials to stdout",
                "select pg_sleep(10)",
                "select \"pg_sleep\"(10)",
                "select pg_read_file('/etc/passwd')",
                "select * from dblink('host=x', 'select 1') as t(a int)",
                "select set_config('app.current_user_id', '1', false)",
                "select * into new_table from materials"}) {
            assertThatThrownBy(() -> SqlValidator.validateAndLimit(sql))
                    .as(sql).isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void limitInsideASubqueryDoesNotCountForTheOuterQuery() {
        String sql = SqlValidator.validateAndLimit(
                "select * from (select * from vw_invoice_match limit 5) t");
        assertThat(sql).endsWith("LIMIT 50");
    }

    @Test
    void topLevelLimitIsKeptEvenWithASubqueryLimit() {
        String original = "select * from (select * from vw_invoice_match limit 5) t limit 7";
        assertThat(SqlValidator.validateAndLimit(original)).isEqualTo(original);
    }

    @Test
    void limitWordInsideALiteralDoesNotCount() {
        String sql = SqlValidator.validateAndLimit("select * from suppliers where name = 'no limit'");
        assertThat(sql).endsWith("LIMIT 50");
    }

    @Test
    void limitIsNotSwallowedByATrailingComment() {
        String sql = SqlValidator.validateAndLimit("select 1 -- note");
        assertThat(sql).endsWith("\nLIMIT 50");
    }

    @Test
    void semicolonInsideAStringLiteralIsAllowed() {
        String sql = SqlValidator.validateAndLimit("select * from suppliers where name = 'A;B'");
        assertThat(sql).contains("'A;B'");
    }

    @Test
    void semicolonAfterALiteralStillSeparatesStatements() {
        assertThatThrownBy(() -> SqlValidator.validateAndLimit("select 'a'; select 'b'"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void forbiddenWordInsideALiteralIsNotAFalsePositive() {
        assertThat(SqlValidator.validateAndLimit("select * from suppliers where name = 'Delete Co'"))
                .contains("'Delete Co'");
    }

    @Test
    void unterminatedLiteralIsRejected() {
        assertThatThrownBy(() -> SqlValidator.validateAndLimit("select 'abc"))
                .isInstanceOf(BadRequestException.class);
    }
}
