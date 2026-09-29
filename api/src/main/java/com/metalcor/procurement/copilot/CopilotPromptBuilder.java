package com.metalcor.procurement.copilot;

/**
 * Builds the prompt sent to Ollama: PostgreSQL dialect, single-SELECT instruction, a short schema
 * context and the user's question. The schema context mirrors the brief purpose already given in
 * each view's COMMENT ON VIEW (V8/V9) and shown on the manager dashboard.
 */
final class CopilotPromptBuilder {

    // Column names and types match the views exactly as created in db/init/V8__three_way_match_views.sql
    // and V9__operational_exception_views.sql; keep this in sync when those views change.
    private static final String SCHEMA_CONTEXT = """
            Available views (read-only, PostgreSQL):

            vw_spend_by_month_category(order_month date, category_code text, category_name text,
                orders bigint, items bigint, total_value numeric, suppliers bigint)
              -- Purchase order spend per month and material category.

            vw_invoice_match(invoice_number text, supplier_id bigint, supplier_name text,
                po_number text, invoice_date date, posting_date date, due_date date,
                gross_amount numeric, invoice_status text, max_abs_price_variance_pct numeric,
                max_quantity_variance_pct numeric, price_exception boolean, quantity_exception boolean,
                invoice_before_receipt boolean, exception_types text[], has_exception boolean,
                resolution text, approved_by text, approved_at timestamptz, days_to_resolve integer,
                block_reason text, age_days integer, is_stale boolean)
              -- Three-way match result per invoice: purchase order x goods receipt x invoice.

            vw_supplier_scorecard(supplier_code text, supplier_name text, orders bigint,
                total_spend numeric, on_time_delivery_pct numeric, avg_days_late numeric,
                invoices bigint, exception_rate_pct numeric, avg_days_to_resolve numeric,
                open_exceptions bigint)
              -- Spend, delivery punctuality and invoice exceptions per supplier.

            vw_payment_timeliness(payment_number text, invoice text, supplier_name text,
                due_date date, due_business_day date, scheduled_for date, paid_at timestamptz,
                days_late integer, is_late boolean, status text)
              -- Whether each payment was made on or before its due date.

            vw_stale_blocked_invoices(invoice_number text, supplier_name text, po_number text,
                posting_date date, age_days integer, age_band text, block_reason text,
                amount numeric, is_stale boolean, is_duplicate_candidate boolean,
                has_match_exception boolean)
              -- Invoices stuck in an open match exception, with how long they have been open.
            """.strip();

    private CopilotPromptBuilder() {
    }

    static String build(String question) {
        return """
                You write PostgreSQL queries for a fictional procurement database (a study project, not SAP).
                Answer with exactly one read-only SELECT statement inside a ```sql code block, and nothing else.
                Never write INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, GRANT or REVOKE.
                Only use the views listed below; do not invent tables or columns.
                Use only the columns listed below for each view. Do not JOIN any view that is not in the FROM clause.
                If the question cannot be answered with the available views, write a simple query against the closest
                view instead of inventing columns or tables.

                %s

                Question: %s
                """.formatted(SCHEMA_CONTEXT, question).strip();
    }
}