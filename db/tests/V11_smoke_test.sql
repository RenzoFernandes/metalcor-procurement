-- V11_smoke_test.sql
-- Smoke test for V11 (purchase_requisitions.status gains 'closed'). Run after V1..V11.
-- Everything runs inside a transaction that is rolled back.

BEGIN;

-- Minimal master data ------------------------------------------------------
INSERT INTO plants (code, name, city, state)
VALUES ('SMK11', 'Smoke Plant V11', 'Sorocaba', 'SP');

INSERT INTO cost_centers (code, name, plant_id)
VALUES ('SMK11CC', 'Smoke Cost Center V11', (SELECT id FROM plants WHERE code = 'SMK11'));

INSERT INTO app_users (name, email, role, plant_id)
VALUES ('Smoke Buyer V11', 'smoke.buyer.v11@example.com', 'buyer', (SELECT id FROM plants WHERE code = 'SMK11'));

-- 'closed' is now accepted ---------------------------------------------------
INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by, status, approved_by, approved_at)
VALUES ((SELECT id FROM plants WHERE code = 'SMK11'),
        (SELECT id FROM cost_centers WHERE code = 'SMK11CC'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer.v11@example.com'),
        current_date + 30, 'closed',
        (SELECT id FROM app_users WHERE email = 'smoke.buyer.v11@example.com'), now());

DO $$
DECLARE
    v_status text;
BEGIN
    SELECT status INTO v_status
      FROM purchase_requisitions
     WHERE plant_id = (SELECT id FROM plants WHERE code = 'SMK11');

    IF v_status <> 'closed' THEN
        RAISE EXCEPTION 'SMOKE TEST FAILED: expected status closed, got %', v_status;
    END IF;

    RAISE NOTICE 'closed status accepted OK';
END
$$;

-- Every other status value from V4 is still accepted -------------------------
DO $$
DECLARE
    v_status text;
BEGIN
    FOREACH v_status IN ARRAY ARRAY['draft', 'pending_approval', 'approved', 'rejected', 'cancelled']
    LOOP
        UPDATE purchase_requisitions
           SET status = v_status,
               approved_by = CASE WHEN v_status = 'approved' THEN
                   (SELECT id FROM app_users WHERE email = 'smoke.buyer.v11@example.com') ELSE NULL END,
               approved_at = CASE WHEN v_status = 'approved' THEN now() ELSE NULL END
         WHERE plant_id = (SELECT id FROM plants WHERE code = 'SMK11');
    END LOOP;
    RAISE NOTICE 'pre-existing status values still accepted OK';
END
$$;

-- Still rejected: anything outside the list -----------------------------------
DO $$
BEGIN
    UPDATE purchase_requisitions
       SET status = 'bogus_status'
     WHERE plant_id = (SELECT id FROM plants WHERE code = 'SMK11');
    RAISE EXCEPTION 'SMOKE TEST FAILED: invalid status was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END
$$;

ROLLBACK;
