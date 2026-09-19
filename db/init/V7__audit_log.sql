-- V7__audit_log.sql
-- Metalcor Procurement v0.1, block 4b: audit log.
-- Requires V1..V6 (uses prevent_modification() from V6 and audits tables from V1, V2, V3, V4 and V5).
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- audit_log
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id               bigint GENERATED ALWAYS AS IDENTITY,
    table_name       text        NOT NULL,
    record_id        bigint      NOT NULL,
    operation        varchar(10) NOT NULL,
    old_data         jsonb,
    new_data         jsonb,
    changed_columns  text[],
    changed_by       bigint,
    db_user          text        NOT NULL DEFAULT session_user,
    transaction_id   bigint      NOT NULL DEFAULT txid_current(),
    changed_at       timestamptz NOT NULL DEFAULT now(),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_operation CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
);

CREATE INDEX idx_audit_log_table_record   ON audit_log (table_name, record_id);
CREATE INDEX idx_audit_log_changed_at     ON audit_log (changed_at);
CREATE INDEX idx_audit_log_changed_by     ON audit_log (changed_by);
CREATE INDEX idx_audit_log_transaction_id ON audit_log (transaction_id);

-- Append-only: no set_updated_at trigger, no updated_at column.
CREATE TRIGGER trg_audit_log_prevent_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION prevent_modification();

CREATE TRIGGER trg_audit_log_prevent_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_modification();

COMMENT ON TABLE  audit_log                 IS 'Immutable row-level change history of business tables, filled by the audit_row_change() trigger. Auditing adds write cost: every audited INSERT, UPDATE or DELETE also writes one row here.';
COMMENT ON COLUMN audit_log.table_name      IS 'Name of the audited table.';
COMMENT ON COLUMN audit_log.record_id       IS 'Value of the id column of the changed row.';
COMMENT ON COLUMN audit_log.operation       IS 'INSERT, UPDATE or DELETE.';
COMMENT ON COLUMN audit_log.old_data        IS 'Row as it was before the change (UPDATE and DELETE).';
COMMENT ON COLUMN audit_log.new_data        IS 'Row as it is after the change (INSERT and UPDATE).';
COMMENT ON COLUMN audit_log.changed_columns IS 'Columns whose value changed, excluding updated_at (UPDATE only).';
COMMENT ON COLUMN audit_log.changed_by      IS 'app_users.id of the acting user, from the app.current_user_id setting. No foreign key on purpose, so the history survives changes to users. Null when the setting was not defined.';
COMMENT ON COLUMN audit_log.db_user         IS 'Database login (session_user) that ran the statement.';
COMMENT ON COLUMN audit_log.transaction_id  IS 'Transaction id, to group all changes made by one transaction.';
COMMENT ON COLUMN audit_log.changed_at      IS 'When the change was made.';

-- ---------------------------------------------------------------------------
-- audit_row_change: AFTER row trigger function
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit_row_change()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_old      jsonb;
    v_new      jsonb;
    v_changed  text[];
    v_id       bigint;
    v_user     bigint := nullif(current_setting('app.current_user_id', true), '')::bigint;
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_new := to_jsonb(NEW);
        v_id  := NEW.id;
    ELSIF TG_OP = 'DELETE' THEN
        v_old := to_jsonb(OLD);
        v_id  := OLD.id;
    ELSE
        v_old := to_jsonb(OLD);
        v_new := to_jsonb(NEW);
        v_id  := NEW.id;

        SELECT array_agg(n.key ORDER BY n.key)
          INTO v_changed
          FROM jsonb_each(v_new) AS n
         WHERE n.key <> 'updated_at'
           AND n.value IS DISTINCT FROM (v_old -> n.key);

        -- Nothing changed besides updated_at: not worth a row.
        IF v_changed IS NULL THEN
            RETURN NULL;
        END IF;
    END IF;

    INSERT INTO audit_log (table_name, record_id, operation, old_data, new_data, changed_columns, changed_by)
    VALUES (TG_TABLE_NAME, v_id, TG_OP, v_old, v_new, v_changed, v_user);

    RETURN NULL;
END;
$$;

COMMENT ON FUNCTION audit_row_change() IS
    'AFTER row trigger function that records INSERT, UPDATE and DELETE in audit_log (INSERT stores new_data, DELETE stores old_data, UPDATE stores both plus changed_columns; an UPDATE that changes nothing but updated_at is skipped). '
    'The acting user comes from the app.current_user_id setting: the API must define it with set_config(''app.current_user_id'', <user id>, true) at the start of every transaction. '
    'SECURITY DEFINER exists so that the application, which in the future will not have write privileges on audit_log, can still generate audit rows through this function but cannot tamper with them directly. '
    'db_user uses session_user, which is not changed by SECURITY DEFINER, so it still shows the real login.';

-- ---------------------------------------------------------------------------
-- Audit triggers
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_purchase_requisitions_audit
    AFTER INSERT OR UPDATE OR DELETE ON purchase_requisitions
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_purchase_requisition_items_audit
    AFTER INSERT OR UPDATE OR DELETE ON purchase_requisition_items
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_purchase_orders_audit
    AFTER INSERT OR UPDATE OR DELETE ON purchase_orders
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_purchase_order_items_audit
    AFTER INSERT OR UPDATE OR DELETE ON purchase_order_items
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_goods_receipts_audit
    AFTER INSERT OR UPDATE OR DELETE ON goods_receipts
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_goods_receipt_items_audit
    AFTER INSERT OR UPDATE OR DELETE ON goods_receipt_items
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_invoice_receipts_audit
    AFTER INSERT OR UPDATE OR DELETE ON invoice_receipts
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_invoice_receipt_items_audit
    AFTER INSERT OR UPDATE OR DELETE ON invoice_receipt_items
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_payments_audit
    AFTER INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_approval_rules_audit
    AFTER INSERT OR UPDATE OR DELETE ON approval_rules
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_match_tolerances_audit
    AFTER INSERT OR UPDATE OR DELETE ON match_tolerances
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER trg_app_users_audit
    AFTER INSERT OR UPDATE OR DELETE ON app_users
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

-- number_ranges: last_value changes on every issued number and is deliberately left out,
-- so numbering does not write to audit_log; only configuration changes are audited.
CREATE TRIGGER trg_number_ranges_audit
    AFTER INSERT OR DELETE OR UPDATE OF fiscal_year, prefix, padding, active ON number_ranges
    FOR EACH ROW EXECUTE FUNCTION audit_row_change();

-- approvals and audit_log are not audited: both are already append-only.