-- V10_smoke_test.sql
-- Smoke test for V10 (database roles and privileges). Run as the database owner after V1..V10.
-- Everything runs inside a transaction that is rolled back. Roles are switched with
-- SET LOCAL ROLE, so no passwords and no seed data are needed.

BEGIN;

-- Master data, created as the owner ----------------------------------------
INSERT INTO plants (code, name, city, state)
VALUES ('SMK10', 'Smoke Plant V10', 'Sorocaba', 'SP');

INSERT INTO cost_centers (code, name, plant_id)
VALUES ('SMK10CC', 'Smoke Cost Center V10', (SELECT id FROM plants WHERE code = 'SMK10'));

INSERT INTO app_users (name, email, role, plant_id)
VALUES ('Smoke Requester V10', 'smoke.v10@example.com', 'requester', (SELECT id FROM plants WHERE code = 'SMK10'));

-- audit_log size before the application acts (kept in a transaction-local setting)
SELECT set_config('smoke.audit_before', (SELECT count(*)::text FROM audit_log), true) AS audit_rows_before;

-- ===========================================================================
-- metalcor_app: allowed operations
-- ===========================================================================
SET LOCAL ROLE metalcor_app;

-- The document number comes from next_document_number(), which runs as its definer
INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by)
VALUES ((SELECT id FROM plants WHERE code = 'SMK10'),
        (SELECT id FROM cost_centers WHERE code = 'SMK10CC'),
        (SELECT id FROM app_users WHERE email = 'smoke.v10@example.com'),
        current_date + 30);

UPDATE purchase_requisitions
   SET status = 'pending_approval'
 WHERE requested_by = (SELECT id FROM app_users WHERE email = 'smoke.v10@example.com');

DO $$
DECLARE
    v_number text;
    v_status text;
    v_before bigint := current_setting('smoke.audit_before')::bigint;
    v_after  bigint;
BEGIN
    SELECT document_number, status INTO v_number, v_status
      FROM purchase_requisitions
     WHERE requested_by = (SELECT id FROM app_users WHERE email = 'smoke.v10@example.com');

    IF v_number IS NULL OR v_number NOT LIKE 'PR-%' THEN
        RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected document number %', v_number;
    END IF;
    IF v_status <> 'pending_approval' THEN
        RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected status %', v_status;
    END IF;

    SELECT count(*) INTO v_after FROM audit_log;
    IF v_after < v_before + 2 THEN
        RAISE EXCEPTION 'SMOKE TEST FAILED: audit_log grew by % rows, expected at least 2 (INSERT and UPDATE)', v_after - v_before;
    END IF;

    RAISE NOTICE 'metalcor_app OK: % created and updated, audit_log +% rows', v_number, v_after - v_before;
END
$$;

-- A view works for metalcor_app
SELECT count(*) AS app_view_rows FROM vw_invoice_match;

-- ---------------------------------------------------------------------------
-- metalcor_app: expected failures
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    INSERT INTO audit_log (table_name, record_id, operation) VALUES ('plants', 1, 'INSERT');
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_app inserted into audit_log';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

DO $$
BEGIN
    UPDATE number_ranges SET last_value = 0;
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_app updated number_ranges';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

DO $$
BEGIN
    DELETE FROM purchase_orders;
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_app deleted from purchase_orders';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

DO $$
BEGIN
    EXECUTE 'CREATE TABLE smoke_v10_should_not_exist (id int)';
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_app created a table';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

DO $$
BEGIN
    EXECUTE 'DROP TABLE plants';
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_app dropped plants';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

RESET ROLE;

-- ===========================================================================
-- metalcor_readonly
-- ===========================================================================
SET LOCAL ROLE metalcor_readonly;

-- Allowed: a view, and the app_users columns that were granted (email is not among them)
SELECT count(*) AS readonly_view_rows FROM vw_invoice_match;
SELECT id, name, role, plant_id, active FROM app_users WHERE id = -1;

-- Expected failures
DO $$
BEGIN
    INSERT INTO plants (code, name, city, state) VALUES ('SMK10B', 'Should not exist', 'Sorocaba', 'SP');
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_readonly inserted into plants';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

DO $$
DECLARE
    v_count bigint;
BEGIN
    SELECT count(*) INTO v_count FROM audit_log;
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_readonly read audit_log';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

DO $$
DECLARE
    v_email text;
BEGIN
    SELECT email INTO v_email FROM app_users LIMIT 1;
    RAISE EXCEPTION 'SMOKE TEST FAILED: metalcor_readonly read app_users.email';
EXCEPTION WHEN OTHERS THEN
    IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
    IF SQLSTATE <> '42501' THEN RAISE EXCEPTION 'SMOKE TEST FAILED: unexpected error % (%)', SQLERRM, SQLSTATE; END IF;
    RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

RESET ROLE;

-- ===========================================================================
-- Role attributes. Expected: both rolsuper = false, rolcanlogin = true, rolconnlimit 20 and 5.
-- ===========================================================================
SELECT rolname, rolsuper, rolcanlogin, rolconnlimit
  FROM pg_roles
 WHERE rolname IN ('metalcor_app', 'metalcor_readonly')
 ORDER BY rolname;

ROLLBACK;
