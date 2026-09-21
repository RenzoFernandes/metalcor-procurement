-- V10__roles_and_privileges.sql
-- Metalcor Procurement v0.3a: least-privilege database roles for the API and for read-only queries.
-- Requires V1..V9. Applied by Flyway, which fills the password placeholders (see docker-compose.yml).
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- (a) Roles
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'metalcor_app') THEN
        CREATE ROLE metalcor_app LOGIN PASSWORD '${app_db_password}'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 20;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'metalcor_readonly') THEN
        CREATE ROLE metalcor_readonly LOGIN PASSWORD '${readonly_db_password}'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 5;
    END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- (b) Per-role session settings (apply to new connections)
-- ---------------------------------------------------------------------------
ALTER ROLE metalcor_app SET statement_timeout = '30s';
ALTER ROLE metalcor_app SET idle_in_transaction_session_timeout = '60s';

ALTER ROLE metalcor_readonly SET statement_timeout = '5s';
ALTER ROLE metalcor_readonly SET idle_in_transaction_session_timeout = '10s';
ALTER ROLE metalcor_readonly SET default_transaction_read_only = on;

-- ---------------------------------------------------------------------------
-- (c) Database and schema access
-- ---------------------------------------------------------------------------
REVOKE ALL ON DATABASE metalcor FROM PUBLIC;
GRANT CONNECT ON DATABASE metalcor TO metalcor_app, metalcor_readonly;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO metalcor_app, metalcor_readonly;

-- ---------------------------------------------------------------------------
-- (d) Functions
-- ---------------------------------------------------------------------------
-- The API cannot write number_ranges directly, so the numbering function runs with the
-- owner's rights. search_path is pinned to avoid hijacking through a schema the caller controls.
ALTER FUNCTION next_document_number(text, date) SECURITY DEFINER SET search_path = public;

REVOKE EXECUTE ON FUNCTION next_document_number(text, date) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION set_updated_at()      FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION prevent_modification() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION audit_row_change()    FROM PUBLIC;

-- Trigger functions need no EXECUTE for the role that fires the trigger (checked at CREATE TRIGGER).
GRANT EXECUTE ON FUNCTION next_document_number(text, date) TO metalcor_app;

-- ---------------------------------------------------------------------------
-- (e) metalcor_app: read and write business data, never delete or change the schema
-- ---------------------------------------------------------------------------
-- No DELETE, TRUNCATE or DDL: documents are cancelled or reversed, not deleted.
GRANT SELECT, INSERT, UPDATE ON
    plants, cost_centers, units_of_measure, material_categories, materials,
    suppliers, supplier_materials, app_users,
    approval_rules, match_tolerances,
    purchase_requisitions, purchase_requisition_items,
    purchase_orders, purchase_order_items,
    goods_receipts, goods_receipt_items,
    invoice_receipts, invoice_receipt_items,
    payments
    TO metalcor_app;

GRANT SELECT, INSERT ON approvals TO metalcor_app;
GRANT SELECT ON audit_log, number_ranges TO metalcor_app;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO metalcor_app;

-- ---------------------------------------------------------------------------
-- (f) metalcor_readonly: read-only, without audit_log, number_ranges and user emails
-- ---------------------------------------------------------------------------
GRANT SELECT ON
    plants, cost_centers, units_of_measure, material_categories, materials,
    suppliers, supplier_materials,
    approval_rules, match_tolerances,
    purchase_requisitions, purchase_requisition_items,
    purchase_orders, purchase_order_items,
    goods_receipts, goods_receipt_items,
    invoice_receipts, invoice_receipt_items,
    payments, approvals
    TO metalcor_readonly;

GRANT SELECT (id, name, role, plant_id, active) ON app_users TO metalcor_readonly;

-- ---------------------------------------------------------------------------
-- (g) Views and demo_as_of_date() for both roles
-- ---------------------------------------------------------------------------
-- The views run with the owner's rights, so they read app_users, audit_log etc. on the caller's
-- behalf; V8 and V9 only use app_users.name, never email, audit_log or number_ranges.
GRANT SELECT ON
    vw_invoice_line_match, vw_invoice_match, vw_match_exception_summary,
    vw_order_approval_check, vw_duplicate_invoice_candidates, vw_duplicate_payment_check,
    vw_payment_timeliness, vw_stale_blocked_invoices, vw_receipt_exceptions,
    vw_supplier_scorecard, vw_spend_by_month_category
    TO metalcor_app, metalcor_readonly;

GRANT EXECUTE ON FUNCTION demo_as_of_date() TO metalcor_app, metalcor_readonly;

-- ---------------------------------------------------------------------------
-- (h) Comments
-- ---------------------------------------------------------------------------
COMMENT ON ROLE metalcor_app IS
    'Login used by the API. Reads and writes business tables, inserts approvals, reads audit_log and number_ranges, issues document numbers through next_document_number(). No DELETE, TRUNCATE or DDL.';
COMMENT ON ROLE metalcor_readonly IS
    'Login for read-only queries (technical mode and copilot). SELECT on business tables and views only; no audit_log, no number_ranges, no app_users.email. Read-only transactions by default, short statement timeout.';

-- Notes for future migrations:
-- 1. New tables, views and sequences get no privileges automatically. Every future migration must
--    GRANT explicitly to metalcor_app and metalcor_readonly, or the migration must first run
--    ALTER DEFAULT PRIVILEGES for the owner, which grants automatically on objects created later
--    by that owner. Explicit grants were chosen here so nothing becomes readable by accident.
-- 2. The owner "metalcor" is a superuser because the official Docker postgres image makes
--    POSTGRES_USER a superuser. In production the owner would be a plain role without SUPERUSER.
