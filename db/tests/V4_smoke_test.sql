-- V4_smoke_test.sql
-- Smoke test for V1..V4. Everything runs inside a transaction that is rolled back,
-- so no data is left behind. Run after V1..V4 have been applied.

BEGIN;

-- Minimal master data ------------------------------------------------------
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

-- Requisition with 2 items -------------------------------------------------
INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by)
VALUES ((SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM cost_centers WHERE code = 'SMKCC1'),
        (SELECT id FROM app_users WHERE email = 'smoke.requester@example.com'),
        current_date + 30);

INSERT INTO purchase_requisition_items
    (purchase_requisition_id, line_number, material_id, quantity, unit_of_measure_id, estimated_unit_price, suggested_supplier_id)
SELECT r.id, v.line_number, m.id, v.quantity, m.unit_of_measure_id, v.price, s.id
  FROM purchase_requisitions r
 CROSS JOIN materials m
 CROSS JOIN suppliers s
 CROSS JOIN (VALUES (1, 1000.000, 5.2500), (2, 250.500, 5.3333)) AS v (line_number, quantity, price)
 WHERE m.code = 'SMKMAT1' AND s.code = 'SMKSUP1';

-- Purchase order with 1 item, traced to requisition line 1 -----------------
INSERT INTO purchase_orders (supplier_id, purchase_requisition_id, plant_id, buyer_id, expected_delivery_date, payment_terms_days)
VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
        (SELECT id FROM purchase_requisitions ORDER BY id DESC LIMIT 1),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        current_date + 20,
        30);

INSERT INTO purchase_order_items
    (purchase_order_id, line_number, material_id, requisition_item_id, quantity, unit_of_measure_id, unit_price)
SELECT o.id, 1, m.id, ri.id, 1000.000, m.unit_of_measure_id, 5.2000
  FROM purchase_orders o
 CROSS JOIN materials m
  JOIN purchase_requisition_items ri ON ri.purchase_requisition_id = o.purchase_requisition_id AND ri.line_number = 1
 WHERE m.code = 'SMKMAT1';

-- Results ------------------------------------------------------------------
-- Expected: PR-<year>-000001, line totals 5250.00 and 1336.07 (250.5 * 5.3333 = 1336.06665 -> 1336.07)
SELECT r.document_number, r.status, i.line_number, i.quantity, i.estimated_unit_price, i.estimated_total
  FROM purchase_requisitions r
  JOIN purchase_requisition_items i ON i.purchase_requisition_id = r.id
 ORDER BY i.line_number;

-- Expected: requisition total 6586.07
SELECT r.document_number, sum(i.estimated_total) AS estimated_total
  FROM purchase_requisitions r
  JOIN purchase_requisition_items i ON i.purchase_requisition_id = r.id
 GROUP BY r.document_number;

-- Expected: PO-<year>-000001, line_total 5200.00
SELECT o.document_number, o.status, o.currency, i.line_number, i.quantity, i.unit_price, i.line_total
  FROM purchase_orders o
  JOIN purchase_order_items i ON i.purchase_order_id = o.id;

-- Failure case: approved requisition without approved_by must be rejected --
DO $$
BEGIN
    INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by, status)
    VALUES ((SELECT id FROM plants WHERE code = 'SMK1'),
            (SELECT id FROM cost_centers WHERE code = 'SMKCC1'),
            (SELECT id FROM app_users WHERE email = 'smoke.requester@example.com'),
            current_date + 30,
            'approved');
    RAISE EXCEPTION 'SMOKE TEST FAILED: approved requisition without approved_by was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK: %', SQLERRM;
END;
$$;

ROLLBACK;