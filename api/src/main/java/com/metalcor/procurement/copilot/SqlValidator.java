package com.metalcor.procurement.copilot;

import com.metalcor.procurement.common.BadRequestException;
import java.util.regex.Pattern;

/**
 * Guards the copilot's generated SQL before it ever reaches the read-only connection. Belt and
 * braces: metalcor_readonly cannot write either (V10__roles_and_privileges.sql), but the model's
 * output is untrusted text and is never executed as-is without this check.
 *
 * <p>All checks run on a "masked" copy of the SQL, same length as the original, where string
 * literals and comments are blanked out. That way a {@code ;} or a word like DELETE inside a text
 * literal is not mistaken for SQL, and a keyword hidden in a comment is not mistaken for absent.
 */
final class SqlValidator {

    private static final Pattern SELECT_START = Pattern.compile("\\Aselect\\b", Pattern.CASE_INSENSITIVE);
    // INTO covers SELECT ... INTO, which creates a table. dblink*, pg_sleep* and pg_read_* also cover
    // their variants (dblink_exec, pg_sleep_for, pg_read_binary_file...).
    private static final Pattern FORBIDDEN_WORD = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|GRANT|REVOKE|COPY|INTO|SET_CONFIG|DBLINK\\w*|PG_SLEEP\\w*|PG_READ_\\w+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_CLAUSE = Pattern.compile(
            "\\b(limit|fetch\\s+(first|next))\\b", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_ROW_LIMIT = 50;

    private SqlValidator() {
    }

    /**
     * Rejects anything but a single SELECT statement and adds a LIMIT when the outermost query has none.
     *
     * @throws BadRequestException when the SQL is rejected; the rejected text is never returned.
     */
    static String validateAndLimit(String rawSql) {
        if (rawSql == null || rawSql.isBlank()) {
            throw new BadRequestException("The copilot did not return a SQL query.");
        }
        String sql = rawSql.strip();
        String masked = mask(sql);

        // A single trailing semicolon is tolerated; any other one outside a literal or comment
        // would separate a second statement.
        String trimmedMasked = masked.stripTrailing();
        if (trimmedMasked.endsWith(";")) {
            int end = trimmedMasked.length() - 1;
            sql = sql.substring(0, end).stripTrailing();
            masked = masked.substring(0, end).stripTrailing();
        }
        if (masked.contains(";")) {
            throw new BadRequestException("Only a single SQL statement is allowed.");
        }

        if (!SELECT_START.matcher(masked.stripLeading()).find()) {
            throw new BadRequestException("Only SELECT queries are allowed.");
        }

        if (FORBIDDEN_WORD.matcher(masked).find()) {
            throw new BadRequestException("The query contains a keyword that is not allowed.");
        }

        if (!hasTopLevelLimit(masked)) {
            // Newline first: a trailing -- comment would otherwise swallow the LIMIT.
            sql = sql + "\nLIMIT " + DEFAULT_ROW_LIMIT;
        }
        return sql;
    }

    /** True when a LIMIT (or FETCH FIRST) appears outside any parentheses, i.e. on the outermost query. */
    private static boolean hasTopLevelLimit(String masked) {
        StringBuilder topLevel = new StringBuilder(masked.length());
        int depth = 0;
        for (int i = 0; i < masked.length(); i++) {
            char c = masked.charAt(i);
            if (c == '(') {
                depth++;
                topLevel.append(' ');
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
                topLevel.append(' ');
            } else {
                topLevel.append(depth == 0 ? c : ' ');
            }
        }
        return LIMIT_CLAUSE.matcher(topLevel).find();
    }

    /**
     * Same-length copy of the SQL with the inside of quoted strings, dollar-quoted strings and
     * comments replaced by spaces. Double-quoted identifiers keep their text (only the quotes are
     * blanked), so a quoted function name such as "pg_sleep" is still seen by the keyword check.
     *
     * @throws BadRequestException on an unterminated literal, identifier or comment.
     */
    private static String mask(String sql) {
        char[] out = sql.toCharArray();
        int n = out.length;
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i);
                end = end < 0 ? n : end;
                blank(out, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = endOfBlockComment(sql, i);
                blank(out, i, end);
                i = end;
            } else if (c == '\'') {
                boolean backslashEscapes = i > 0 && (sql.charAt(i - 1) == 'E' || sql.charAt(i - 1) == 'e')
                        && (i == 1 || !isIdentifierChar(sql.charAt(i - 2)));
                int end = endOfString(sql, i, backslashEscapes);
                blank(out, i, end);
                i = end;
            } else if (c == '"') {
                int close = sql.indexOf('"', i + 1);
                if (close < 0) {
                    throw new BadRequestException("The query has an unterminated quoted identifier.");
                }
                out[i] = ' ';
                out[close] = ' ';
                i = close + 1;
            } else if (c == '$') {
                int tagEnd = dollarTagEnd(sql, i);
                if (tagEnd < 0) {
                    i++;
                } else {
                    String tag = sql.substring(i, tagEnd);
                    int close = sql.indexOf(tag, tagEnd);
                    if (close < 0) {
                        throw new BadRequestException("The query has an unterminated dollar-quoted string.");
                    }
                    blank(out, i, close + tag.length());
                    i = close + tag.length();
                }
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** End (exclusive) of a possibly nested block comment starting at {@code start}. */
    private static int endOfBlockComment(String sql, int start) {
        int depth = 0;
        int i = start;
        while (i < sql.length()) {
            if (sql.startsWith("/*", i)) {
                depth++;
                i += 2;
            } else if (sql.startsWith("*/", i)) {
                depth--;
                i += 2;
                if (depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        throw new BadRequestException("The query has an unterminated comment.");
    }

    /** End (exclusive) of the quoted literal starting at {@code start}; a doubled quote is an escaped quote. */
    private static int endOfString(String sql, int start, boolean backslashEscapes) {
        int i = start + 1;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (backslashEscapes && c == '\\') {
                i += 2;
            } else if (c == '\'') {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i += 2;
                } else {
                    return i + 1;
                }
            } else {
                i++;
            }
        }
        throw new BadRequestException("The query has an unterminated string literal.");
    }

    /** If a dollar-quote tag ($$ or $name$) starts at {@code start}, its end (exclusive); otherwise -1. */
    private static int dollarTagEnd(String sql, int start) {
        // $1, $2... are parameters, and an identifier such as a$b is not a tag.
        if (start > 0 && isIdentifierChar(sql.charAt(start - 1))) {
            return -1;
        }
        int i = start + 1;
        while (i < sql.length() && (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_'
                || (i > start + 1 && Character.isDigit(sql.charAt(i))))) {
            i++;
        }
        return i < sql.length() && sql.charAt(i) == '$' ? i + 1 : -1;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static void blank(char[] chars, int from, int to) {
        for (int k = from; k < to; k++) {
            chars[k] = ' ';
        }
    }
}
