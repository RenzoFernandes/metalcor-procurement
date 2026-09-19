-- V6_smoke_test.sql
-- Smoke test for V6 (approvals). Everything runs inside a transaction that is rolled back,
-- so no data is left behind. Run after V1..V6 have been applied
-- (uses the reference approval rules loaded by V2).

BEGIN;

-- Minimal master data -------------------------------------------------------
INSERT INTO plants (code, name, city, state)
VALUES ('SMK1', 'Smoke Plant', 'Sorocaba', 'SP');

INSERT INTO cost_centers (code, name, plant_id)
VALUES ('SMKCC1', 'Smoke Cost Center', (SELECT id FROM plants WHERE code = 'SMK1'));

INSERT INTO units_of_measure (code, description)
VALUES ('SMKKG', 'Kilogram (smoke test)');

INSERT INTO material_categories (code, name)
VALUES ('SMKCAT', 'Smoke Category');

INSERT INTO materials (code, description, material_category_id, unit_of_measure_id, standard_price)
VALUES ('SMKMAT1', 'Bobina de aço (smoke test)',
        (SELECT id FROM material_categories WHERE code = 'SMKCAT'),
        (SELECT id FROM units_of_measure WHERE code = 'SMKKG'),
        5.0000);

INSERT INTO suppliers (code, name, city, state)
VALUES ('SMKSUP1', 'Fornecedor Smoke', 'Belo Horizonte', 'MG');

INSERT INTO app_users (name, email, role, plant_id)
VALUES ('Smoke Requester', 'smoke.requester@example.com', 'requester', (SELECT id FROM plants WHERE code = 'SMK1')),
       ('Smoke Buyer',     'smoke.buyer@example.com',     'buyer',     (SELECT id FROM plants WHERE code = 'SMK1'));

-- One requisition (1 item, 5250.00) and one purchase order ------------------
INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by, status)
VALUES ((SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM cost_centers WHERE code = 'SMKCC1'),
        (SELECT id FROM app_users WHERE email = 'smoke.requester@example.com'),
        current_date + 30,
        'pending_approval');

INSERT INTO purchase_requisition_items
    (purchase_requisition_id, line_number, material_id, quantity, unit_of_measure_id, estimated_unit_price)
SELECT r.id, 1, m.id, 1000.000, m.unit_of_measure_id, 5.2500
  FROM purchase_requisitions r
 CROSS JOIN materials m
 WHERE m.code = 'SMKMAT1';

INSERT INTO purchase_orders (supplier_id, plant_id, buyer_id, expected_delivery_date, payment_terms_days)
VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        current_date + 20, 30);

-- Approved decision on the requisition (rule 0..10000 -> buyer) --------------
INSERT INTO approvals (purchase_requisition_id, required_role, approval_rule_id, decided_by, decision, amount_evaluated)
VALUES ((SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1),
        'buyer',
        (SELECT id FROM approval_rules WHERE document_type = 'purchase_requisition' AND min_amount = 0),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        'approved',
        (SELECT sum(estimated_total) FROM purchase_requisition_items
          WHERE purchase_requisition_id = (SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1)));

-- Expected: PR-<year>-000001, step 1, buyer, approved, amount_evaluated 5250.00
SELECT r.document_number, a.step, a.required_role, a.decision, a.amount_evaluated, u.email AS decided_by
  FROM approvals a
  JOIN purchase_requisitions r ON r.id = a.purchase_requisition_id
  JOIN app_users u ON u.id = a.decided_by;

-- Failure cases: each must be rejected ------------------------------------------
-- (a) UPDATE is blocked
DO $$
BEGIN
    UPDATE approvals SET comment = 'tampering';
    RAISE EXCEPTION 'SMOKE TEST FAILED: UPDATE on approvals was accepted';
EXCEPTION
    WHEN raise_exception THEN
        IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
        RAISE NOTICE 'Expected failure OK (UPDATE): %', SQLERRM;
END;
$$;

-- (b) DELETE is blocked
DO $$
BEGIN
    DELETE FROM approvals;
    RAISE EXCEPTION 'SMOKE TEST FAILED: DELETE on approvals was accepted';
EXCEPTION
    WHEN raise_exception THEN
        IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
        RAISE NOTICE 'Expected failure OK (DELETE): %', SQLERRM;
END;
$$;

-- (c) TRUNCATE is blocked
DO $$
BEGIN
    TRUNCATE approvals;
    RAISE EXCEPTION 'SMOKE TEST FAILED: TRUNCATE on approvals was accepted';
EXCEPTION
    WHEN raise_exception THEN
        IF SQLERRM LIKE 'SMOKE TEST FAILED%' THEN RAISE; END IF;
        RAISE NOTICE 'Expected failure OK (TRUNCATE): %', SQLERRM;
END;
$$;

-- (d) rejection without a comment
DO $$
BEGIN
    INSERT INTO approvals (purchase_requisition_id, required_role, decided_by, decision, amount_evaluated)
    VALUES ((SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1),
            'buyer',
            (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
            'rejected',
            5250.00);
    RAISE EXCEPTION 'SMOKE TEST FAILED: rejection without comment was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (rejection without comment): %', SQLERRM;
END;
$$;

-- (e) decision linked to two documents at once
DO $$
BEGIN
    INSERT INTO approvals (purchase_requisition_id, purchase_order_id, required_role, decided_by, decision, amount_evaluated)
    VALUES ((SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1),
            (SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
            'buyer',
            (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
            'approved',
            5250.00);
    RAISE EXCEPTION 'SMOKE TEST FAILED: decision linked to two documents was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (two documents): %', SQLERRM;
END;
$$;

-- (f) decision linked to no document
DO $$
BEGIN
    INSERT INTO approvals (required_role, decided_by, decision, amount_evaluated)
    VALUES ('buyer',
            (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
            'approved',
            5250.00);
    RAISE EXCEPTION 'SMOKE TEST FAILED: decision without document was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (no document): %', SQLERRM;
END;
$$;

ROLLBACK;