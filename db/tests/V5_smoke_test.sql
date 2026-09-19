-- V5_smoke_test.sql
-- Smoke test for V5 (receipts, invoices, payments). Everything runs inside a transaction
-- that is rolled back, so no data is left behind. Run after V1..V5 have been applied.

BEGIN;

-- Minimal master data + one purchase order with 1 item ---------------------
INSERT INTO plants (code, name, city, state)
VALUES ('SMK1', 'Smoke Plant', 'Sorocaba', 'SP');

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
VALUES ('Smoke Buyer',   'smoke.buyer@example.com',   'buyer',   (SELECT id FROM plants WHERE code = 'SMK1')),
       ('Smoke Finance', 'smoke.finance@example.com', 'finance', (SELECT id FROM plants WHERE code = 'SMK1'));

INSERT INTO purchase_orders (supplier_id, plant_id, buyer_id, expected_delivery_date, payment_terms_days)
VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        current_date + 20, 30);

INSERT INTO purchase_order_items (purchase_order_id, line_number, material_id, quantity, unit_of_measure_id, unit_price)
SELECT o.id, 1, m.id, 1000.000, m.unit_of_measure_id, 5.2000
  FROM purchase_orders o
 CROSS JOIN materials m
 WHERE m.code = 'SMKMAT1';

-- Goods receipt (partial: 600 of 1000) --------------------------------------
INSERT INTO goods_receipts (purchase_order_id, plant_id, received_by, delivery_note_number)
VALUES ((SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        'DN-0001');

INSERT INTO goods_receipt_items (goods_receipt_id, line_number, purchase_order_item_id, quantity_received)
VALUES ((SELECT id FROM goods_receipts ORDER BY id DESC LIMIT 1),
        1,
        (SELECT id FROM purchase_order_items ORDER BY id DESC LIMIT 1),
        600.000);

-- Invoice (full quantity) ---------------------------------------------------
INSERT INTO invoice_receipts (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date, gross_amount)
VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
        (SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
        'NF-1001', current_date, current_date + 30, 5200.00);

INSERT INTO invoice_receipt_items (invoice_receipt_id, line_number, purchase_order_item_id, quantity_invoiced, unit_price)
VALUES ((SELECT id FROM invoice_receipts ORDER BY id DESC LIMIT 1),
        1,
        (SELECT id FROM purchase_order_items ORDER BY id DESC LIMIT 1),
        1000.000, 5.2000);

-- Payment -------------------------------------------------------------------
INSERT INTO payments (invoice_receipt_id, amount, scheduled_for, payment_method, created_by)
VALUES ((SELECT id FROM invoice_receipts ORDER BY id DESC LIMIT 1),
        5200.00, current_date + 30, 'pix',
        (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com'));

-- Results ------------------------------------------------------------------
-- Expected: GR-<year>-000001, quantity_received 600.000
SELECT g.document_number, g.status, g.receipt_date, i.line_number, i.quantity_received
  FROM goods_receipts g
  JOIN goods_receipt_items i ON i.goods_receipt_id = g.id;

-- Expected: IR-<year>-000001, line_total 5200.00 = gross_amount
SELECT r.document_number, r.supplier_invoice_number, r.status, r.gross_amount, i.line_number, i.line_total
  FROM invoice_receipts r
  JOIN invoice_receipt_items i ON i.invoice_receipt_id = r.id;

-- Expected: PY-<year>-000001, scheduled, 5200.00
SELECT p.document_number, p.status, p.payment_method, p.amount, p.scheduled_for
  FROM payments p;

-- Failure cases: each must be rejected by its constraint -----------------------
DO $$
BEGIN
    INSERT INTO goods_receipts (purchase_order_id, plant_id, received_by, status)
    VALUES ((SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
            (SELECT id FROM plants WHERE code = 'SMK1'),
            (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
            'reversed');
    RAISE EXCEPTION 'SMOKE TEST FAILED: reversed receipt without reversal data was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (reversed receipt without reason): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    INSERT INTO invoice_receipts (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date, gross_amount)
    VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
            (SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
            'NF-1001', current_date, current_date + 30, 5200.00);
    RAISE EXCEPTION 'SMOKE TEST FAILED: duplicate supplier invoice number was accepted';
EXCEPTION
    WHEN unique_violation THEN
        RAISE NOTICE 'Expected failure OK (duplicate supplier invoice): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    INSERT INTO invoice_receipts (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date, gross_amount, status)
    VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
            (SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
            'NF-1002', current_date, current_date + 30, 100.00, 'blocked');
    RAISE EXCEPTION 'SMOKE TEST FAILED: blocked invoice without block_reason was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (blocked invoice without reason): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    INSERT INTO invoice_receipts (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date, gross_amount, status)
    VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
            (SELECT id FROM purchase_orders ORDER BY id DESC LIMIT 1),
            'NF-1003', current_date, current_date + 30, 100.00, 'approved');
    RAISE EXCEPTION 'SMOKE TEST FAILED: approved invoice without approved_by was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (approved invoice without approver): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    INSERT INTO payments (invoice_receipt_id, amount, scheduled_for, payment_method, created_by, status)
    VALUES ((SELECT id FROM invoice_receipts ORDER BY id DESC LIMIT 1),
            100.00, current_date, 'boleto',
            (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com'),
            'paid');
    RAISE EXCEPTION 'SMOKE TEST FAILED: paid payment without paid_at was accepted';
EXCEPTION
    WHEN check_violation THEN
        RAISE NOTICE 'Expected failure OK (paid payment without paid_at): %', SQLERRM;
END;
$$;

DO $$
BEGIN
    INSERT INTO payments (invoice_receipt_id, amount, scheduled_for, payment_method, created_by)
    VALUES ((SELECT id FROM invoice_receipts ORDER BY id DESC LIMIT 1),
            5200.00, current_date + 30, 'boleto',
            (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com'));
    RAISE EXCEPTION 'SMOKE TEST FAILED: second active payment for the same invoice was accepted';
EXCEPTION
    WHEN unique_violation THEN
        RAISE NOTICE 'Expected failure OK (second active payment for the same invoice): %', SQLERRM;
END;
$$;

ROLLBACK;