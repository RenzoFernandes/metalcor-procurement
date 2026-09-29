package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.BadRequestException;
import java.util.regex.Pattern;

/**
 * Guards the copilot's generated SQL before it ever reaches the read-only connection. Belt and
 * braces: metalcor_readonly cannot write either (V10__roles_and_privileges.sql), but the model's
 * output is untrusted text and is never executed as-is without this check.
 */
final class SqlValidator {

    private static final Pattern SELECT_START = Pattern.compile("\\Aselect\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORBIDDEN_WORD = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|GRANT|REVOKE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_CLAUSE = Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_ROW_LIMIT = 50;

    private SqlValidator() {
    }

    /**
     * Rejects anything but a single SELECT statement and adds a LIMIT when the query has none.
     *
     * @throws BadRequestException when the SQL is rejected; the rejected text is never returned.
     */
    static String validateAndLimit(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new BadRequestException("The copilot did not return a SQL query.");
        }
        String sql = rawSql.strip();

        // A single trailing semicolon is tolerated; anything left after stripping it must not
        // contain another one, or it would be more than one statement.
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).stripTrailing();
        }
        if (sql.contains(";")) {
            throw new BadRequestException("Only a single SQL statement is allowed.");
        }

        if (!SELECT_START.matcher(stripLeadingComments(sql)).find()) {
            throw new BadRequestException("Only SELECT queries are allowed.");
        }

        if (FORBIDDEN_WORD.matcher(sql).find()) {
            throw new BadRequestException("The query contains a keyword that is not allowed.");
        }

        if (!LIMIT_CLAUSE.matcher(sql).find()) {
            sql = sql + " LIMIT " + DEFAULT_ROW_LIMIT;
        }
        return sql;
    }

    /** Drops leading whitespace and leading {@code --} / {@code slash-star} comments so the SELECT check is not fooled by them. */
    private static String stripLeadingComments(String sql) {
        String s = sql;
        boolean changed = true;
        while (changed) {
            changed = false;
            String trimmed = s.stripLeading();
            if (trimmed.startsWith("--")) {
                int newline = trimmed.indexOf('\n');
                s = newline >= 0 ? trimmed.substring(newline + 1) : "";
                changed = true;
            } else if (trimmed.startsWith("/*")) {
                int end = trimmed.indexOf("*/");
                s = end >= 0 ? trimmed.substring(end + 2) : "";
                changed = true;
            } else {
                s = trimmed;
            }
        }
        return s;
    }
}