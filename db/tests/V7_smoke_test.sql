-- V7_smoke_test.sql
-- Smoke test for V7 (audit log). Everything runs inside a transaction that is rolled back,
-- so no data is left behind. Run after V1..V7 have been applied.

BEGIN;

-- Master data first (app_users rows are audited with changed_by = NULL: no user context yet)
INSERT INTO plants (code, name, city, state)
VALUES ('SMK1', 'Smoke Plant', 'Sorocaba', 'SP');

INSERT INTO cost_centers (code, name, plant_id)
VALUES ('SMKCC1', 'Smoke Cost Center', (SELECT id FROM plants WHERE code = 'SMK1'));

INSERT INTO app_users (name, email, role, plant_id)
VALUES ('Smoke Requester', 'smoke.requester@example.com', 'requester', (SELECT id FROM plants WHERE code = 'SMK1'));

-- The API would do this at the start of each transaction (true = local to the transaction)
SELECT set_config('app.current_user_id',
                  (SELECT id::text FROM app_users WHERE email = 'smoke.requester@example.com'),
                  true) AS current_user_id;

-- A requisition, then a status change ---------------------------------------
INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by)
VALUES ((SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM cost_centers WHERE code = 'SMKCC1'),
        (SELECT id FROM app_users WHERE email = 'smoke.requester@example.com'),
        current_date + 30);

UPDATE purchase_requisitions
   SET status = 'pending_approval'
 WHERE id = (SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1);

-- Expected: app_users INSERT (changed_by NULL); purchase_requisitions INSERT and UPDATE
-- with changed_columns = {status} and changed_by = the requester id. plants and cost_centers are not audited.
SELECT table_name, operation, changed_columns, changed_by
  FROM audit_log
 ORDER BY id;

-- (a) An UPDATE that only touches updated_at must not create an audit row --------
DO $$
DECLARE
    v_before bigint;
    v_after  bigint;
BEGIN
    SELECT count(*) INTO v_before FROM audit_log WHERE table_name = 'purchase_requisitions';

    UPDATE purchase_requisitions
       SET updated_at = now()
     WHERE id = (SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1);

    SELECT count(*) INTO v_after FROM audit_log WHERE table_name = 'purchase_requisitions';

    IF v_after <> v_before THEN
        RAISE EXCEPTION 'SMOKE TEST FAILED: updated_at-only UPDATE wrote % audit row(s)', v_after - v_before;
    END IF;
    RAISE NOTICE 'OK (a): UPDATE touching only updated_at wrote no audit row (audit rows for the table: % before, % after)', v_before, v_after;
END;
$$;

-- (b) next_document_number must not write to audit_log (last_value is not audited) ---
DO $$
DECLARE
    v_before bigint;
    v_after  bigint;
    v_number text;
BEGIN
    SELECT count(*) INTO v_before FROM audit_log;

    v_number := next_document_number('purchase_order');

    SELECT count(*) INTO v_after FROM audit_log;

    IF v_after <> v_before THEN
        RAISE EXCEPTION 'SMOKE TEST FAILED: next_document_number wrote % audit row(s)', v_after - v_before;
    END IF;
    RAISE NOTICE 'OK (b): next_document_number issued % and wrote no audit row (audit_log rows: % before, % after)', v_number, v_before, v_after;
END;
$$;

-- Failure cases: audit_log is append-only ----------------------------------------
DO $$
BEGIN
    UPDATE audit_log SET table_name = 'tampering';
    RAISE EXCEPTION 'SMOKE TEST FAILED: UPDATE on audit_log was accepted';
EXCEPTION
    WHEN raise_exception THEN
        IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
        RAISE NOTICE 'Expected failure OK (UPDATE): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    DELETE FROM audit_log;
    RAISE EXCEPTION 'SMOKE TEST FAILED: DELETE on audit_log was accepted';
EXCEPTION
    WHEN raise_exception THEN
        IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
        RAISE NOTICE 'Expected failure OK (DELETE): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    TRUNCATE audit_log;
    RAISE EXCEPTION 'SMOKE TEST FAILED: TRUNCATE on audit_log was accepted';
EXCEPTION
    WHEN raise_exception THEN
        IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
        RAISE NOTICE 'Expected failure OK (TRUNCATE): %', SQLERRM;
END;
$$;

ROLLBACK;