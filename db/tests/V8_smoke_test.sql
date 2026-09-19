-- V8_smoke_test.sql
-- Smoke test for V8 (three-way match views). Everything runs inside a transaction that is
-- rolled back, so no data is left behind. Run after V1..V8 have been applied, ideally with
-- psql -v ON_ERROR_STOP=1 so the first failed check stops the script.
-- Uses its own SMK* master data and does not depend on the seeds. It relies on the default
-- match tolerance from V2 (2% price, 5% quantity).
--
-- Notes: (supplier, supplier_invoice_number) is unique, so every test invoice has its own number.
-- "One invoice per purchase order" is an application rule, not a constraint, so several test
-- invoices share the same order here on purpose.

BEGIN;

-- Minimal master data ------------------------------------------------------
INSERT INTO plants (code, name, city, state)
VALUES ('SMK1', 'Smoke Plant', 'Sorocaba', 'SP');

INSERT INTO units_of_measure (code, description)
VALUES ('SMKUN', 'Unit (smoke test)');

INSERT INTO material_categories (code, name)
VALUES ('SMKCAT', 'Smoke Category');

INSERT INTO materials (code, description, material_category_id, unit_of_measure_id, standard_price)
VALUES ('SMKMAT1', 'Parafuso (smoke test)',
        (SELECT id FROM material_categories WHERE code = 'SMKCAT'),
        (SELECT id FROM units_of_measure WHERE code = 'SMKUN'),
        10.0000);

INSERT INTO suppliers (code, name, city, state)
VALUES ('SMKSUP1', 'Fornecedor Smoke', 'Belo Horizonte', 'MG');

INSERT INTO app_users (name, email, role, plant_id)
VALUES ('Smoke Buyer',   'smoke.buyer@example.com',   'buyer',   (SELECT id FROM plants WHERE code = 'SMK1')),
       ('Smoke Finance', 'smoke.finance@example.com', 'finance', (SELECT id FROM plants WHERE code = 'SMK1'));

-- PO 1: 1000 UN at 10.0000, fully received (posted) on 2026-03-10 ----------
INSERT INTO purchase_orders (supplier_id, plant_id, buyer_id, order_date, expected_delivery_date, payment_terms_days, notes)
VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        '2026-03-01', '2026-03-10', 30, 'SMK-PO1');

INSERT INTO purchase_order_items (purchase_order_id, line_number, material_id, quantity, unit_of_measure_id, unit_price)
SELECT o.id, 1, m.id, 1000.000, m.unit_of_measure_id, 10.0000
  FROM purchase_orders o CROSS JOIN materials m
 WHERE o.notes = 'SMK-PO1' AND m.code = 'SMKMAT1';

INSERT INTO goods_receipts (purchase_order_id, plant_id, received_by, receipt_date, status)
VALUES ((SELECT id FROM purchase_orders WHERE notes = 'SMK-PO1'),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        '2026-03-10', 'posted');

INSERT INTO goods_receipt_items (goods_receipt_id, line_number, purchase_order_item_id, quantity_received)
SELECT gr.id, 1, poi.id, 1000.000
  FROM goods_receipts gr
  JOIN purchase_order_items poi ON poi.purchase_order_id = gr.purchase_order_id;

-- PO 2: 500 UN at 10.0000, never received -----------------------------------
INSERT INTO purchase_orders (supplier_id, plant_id, buyer_id, order_date, expected_delivery_date, payment_terms_days, notes)
VALUES ((SELECT id FROM suppliers WHERE code = 'SMKSUP1'),
        (SELECT id FROM plants WHERE code = 'SMK1'),
        (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
        '2026-03-01', '2026-03-10', 30, 'SMK-PO2');

INSERT INTO purchase_order_items (purchase_order_id, line_number, material_id, quantity, unit_of_measure_id, unit_price)
SELECT o.id, 1, m.id, 500.000, m.unit_of_measure_id, 10.0000
  FROM purchase_orders o CROSS JOIN materials m
 WHERE o.notes = 'SMK-PO2' AND m.code = 'SMKMAT1';

-- Test invoices --------------------------------------------------------------
-- header: number, PO note, invoice_date, posting_date, gross, status, block_reason, approved
INSERT INTO invoice_receipts
    (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date, posting_date,
     gross_amount, status, block_reason, approved_by, approved_at)
SELECT s.id, po.id, v.num, v.inv_date, v.inv_date + 30, v.post_date,
       v.gross, v.status, v.block_reason,
       CASE WHEN v.status = 'approved' THEN (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com') END,
       CASE WHEN v.status = 'approved' THEN TIMESTAMPTZ '2026-03-20 12:00:00+00' END
  FROM (VALUES
        ('NF-A', 'SMK-PO1', DATE '2026-03-15', DATE '2026-03-16', 10150.00, 'received', NULL,                  1000.000, 10.1500),  -- price +1.5%
        ('NF-B', 'SMK-PO1', DATE '2026-09-05', DATE '2026-09-10', 10300.00, 'blocked',  'Price difference',    1000.000, 10.3000),  -- price +3%
        ('NF-C', 'SMK-PO1', DATE '2026-03-15', DATE '2026-03-16', 10400.00, 'received', NULL,                  1040.000, 10.0000),  -- qty +4%
        ('NF-D', 'SMK-PO1', DATE '2026-03-15', DATE '2026-03-16', 10800.00, 'received', NULL,                  1080.000, 10.0000),  -- qty +8%
        ('NF-E', 'SMK-PO1', DATE '2026-03-05', DATE '2026-03-16', 10000.00, 'received', NULL,                  1000.000, 10.0000),  -- before receipt
        ('NF-F', 'SMK-PO2', DATE '2026-03-15', DATE '2026-03-16',  5000.00, 'received', NULL,                   500.000, 10.0000),  -- nothing received
        ('NF-G', 'SMK-PO1', DATE '2026-03-15', DATE '2026-03-16', 10300.00, 'approved', NULL,                  1000.000, 10.3000)   -- exception, approved
       ) AS v(num, po_note, inv_date, post_date, gross, status, block_reason, qty, price)
  JOIN suppliers       s  ON s.code = 'SMKSUP1'
  JOIN purchase_orders po ON po.notes = v.po_note;

INSERT INTO invoice_receipt_items (invoice_receipt_id, line_number, purchase_order_item_id, quantity_invoiced, unit_price)
SELECT ir.id, 1, poi.id, v.qty, v.price
  FROM (VALUES
        ('NF-A', 1000.000, 10.1500),
        ('NF-B', 1000.000, 10.3000),
        ('NF-C', 1040.000, 10.0000),
        ('NF-D', 1080.000, 10.0000),
        ('NF-E', 1000.000, 10.0000),
        ('NF-F',  500.000, 10.0000),
        ('NF-G', 1000.000, 10.3000)
       ) AS v(num, qty, price)
  JOIN invoice_receipts     ir  ON ir.supplier_invoice_number = v.num
  JOIN purchase_order_items poi ON poi.purchase_order_id = ir.purchase_order_id;

-- Show the results -----------------------------------------------------------
SELECT supplier_invoice_number, quantity_received, quantity_invoiced,
       price_variance_pct, quantity_variance_pct,
       price_tolerance_pct, quantity_tolerance_pct,
       price_exception, quantity_exception, not_received
  FROM vw_invoice_line_match
 WHERE supplier_invoice_number LIKE 'NF-%'
   AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1')
 ORDER BY supplier_invoice_number;

SELECT m.invoice_number, ir.supplier_invoice_number, m.invoice_status,
       m.max_abs_price_variance_pct, m.max_quantity_variance_pct,
       m.exception_types, m.resolution, m.days_to_resolve, m.age_days, m.is_stale
  FROM vw_invoice_match m
  JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
 WHERE m.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1')
 ORDER BY ir.supplier_invoice_number;

SELECT * FROM vw_match_exception_summary
 WHERE posting_month IN (DATE '2026-03-01', DATE '2026-09-01');

-- Checks ---------------------------------------------------------------------
DO $$
BEGIN
    IF demo_as_of_date() = DATE '2026-09-18' THEN
        RAISE NOTICE 'OK: demo_as_of_date() returns 2026-09-18';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: demo_as_of_date() returned %', demo_as_of_date();
    END IF;
END $$;

-- Line level: price +1.5% passes, +3% is an exception.
DO $$
DECLARE
    v_a vw_invoice_line_match%ROWTYPE;
    v_b vw_invoice_line_match%ROWTYPE;
BEGIN
    SELECT * INTO v_a FROM vw_invoice_line_match WHERE supplier_invoice_number = 'NF-A' AND po_number IS NOT NULL
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');
    SELECT * INTO v_b FROM vw_invoice_line_match WHERE supplier_invoice_number = 'NF-B'
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

    IF v_a.price_variance_pct = 1.50 AND NOT v_a.price_exception AND v_a.quantity_received = 1000 AND NOT v_a.quantity_exception THEN
        RAISE NOTICE 'OK: price +1.5%% is within the 2%% tolerance';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-A (price +1.5%%): variance %, exception %, received %',
            v_a.price_variance_pct, v_a.price_exception, v_a.quantity_received;
    END IF;

    IF v_b.price_variance_pct = 3.00 AND v_b.price_exception AND NOT v_b.quantity_exception THEN
        RAISE NOTICE 'OK: price +3%% is a price_exception';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-B (price +3%%): variance %, exception %',
            v_b.price_variance_pct, v_b.price_exception;
    END IF;
END $$;

-- Line level: quantity +4% passes, +8% is an exception.
DO $$
DECLARE
    v_c vw_invoice_line_match%ROWTYPE;
    v_d vw_invoice_line_match%ROWTYPE;
BEGIN
    SELECT * INTO v_c FROM vw_invoice_line_match WHERE supplier_invoice_number = 'NF-C'
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');
    SELECT * INTO v_d FROM vw_invoice_line_match WHERE supplier_invoice_number = 'NF-D'
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

    IF v_c.quantity_variance_pct = 4.00 AND NOT v_c.quantity_exception AND NOT v_c.price_exception THEN
        RAISE NOTICE 'OK: quantity +4%% is within the 5%% tolerance';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-C (qty +4%%): variance %, exception %',
            v_c.quantity_variance_pct, v_c.quantity_exception;
    END IF;

    IF v_d.quantity_variance_pct = 8.00 AND v_d.quantity_exception AND NOT v_d.price_exception THEN
        RAISE NOTICE 'OK: quantity +8%% is a quantity_exception';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-D (qty +8%%): variance %, exception %',
            v_d.quantity_variance_pct, v_d.quantity_exception;
    END IF;
END $$;

-- Line level: nothing received.
DO $$
DECLARE
    v_f vw_invoice_line_match%ROWTYPE;
BEGIN
    SELECT * INTO v_f FROM vw_invoice_line_match WHERE supplier_invoice_number = 'NF-F'
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

    IF v_f.not_received AND v_f.quantity_received = 0 AND v_f.quantity_variance_pct IS NULL AND v_f.quantity_exception THEN
        RAISE NOTICE 'OK: item without receipt is not_received (variance null, quantity_exception true)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-F: not_received %, received %, variance %, exception %',
            v_f.not_received, v_f.quantity_received, v_f.quantity_variance_pct, v_f.quantity_exception;
    END IF;
END $$;

-- Invoice level: exception types, resolution, ageing.
DO $$
DECLARE
    v_sup bigint := (SELECT id FROM suppliers WHERE code = 'SMKSUP1');
    v_n   int;
    r     vw_invoice_match%ROWTYPE;
BEGIN
    SELECT count(*) INTO v_n FROM vw_invoice_match WHERE supplier_id = v_sup;
    IF v_n = 7 THEN
        RAISE NOTICE 'OK: vw_invoice_match has one row per invoice (7)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: expected 7 invoices in vw_invoice_match, got %', v_n;
    END IF;

    -- NF-A and NF-C: no exception.
    SELECT count(*) INTO v_n
      FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number IN ('NF-A', 'NF-C')
       AND NOT m.has_exception AND m.resolution = 'none' AND m.exception_types = '{}';
    IF v_n = 2 THEN
        RAISE NOTICE 'OK: invoices within tolerance have no exception (resolution none)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-A/NF-C should have no exception, matched % of 2', v_n;
    END IF;

    -- NF-B: price exception, blocked -> open, not stale (age 8 days).
    SELECT m.* INTO r FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number = 'NF-B';
    IF r.exception_types = ARRAY['price_variance'] AND r.resolution = 'open'
       AND r.age_days = 8 AND NOT r.is_stale AND r.days_to_resolve IS NULL AND r.max_abs_price_variance_pct = 3.00 THEN
        RAISE NOTICE 'OK: NF-B is price_variance, open, age 8 days, not stale';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-B: types %, resolution %, age %, stale %',
            r.exception_types, r.resolution, r.age_days, r.is_stale;
    END IF;

    -- NF-D: quantity exception only.
    SELECT m.* INTO r FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number = 'NF-D';
    IF r.exception_types = ARRAY['quantity_variance'] AND r.max_quantity_variance_pct = 8.00 THEN
        RAISE NOTICE 'OK: NF-D is quantity_variance only';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-D: types %, max qty variance %', r.exception_types, r.max_quantity_variance_pct;
    END IF;

    -- NF-E: invoice dated before the receipt.
    SELECT m.* INTO r FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number = 'NF-E';
    IF r.invoice_before_receipt AND r.exception_types = ARRAY['invoice_before_receipt']
       AND NOT r.price_exception AND NOT r.quantity_exception AND r.resolution = 'open' THEN
        RAISE NOTICE 'OK: NF-E is invoice_before_receipt only';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-E: types %, resolution %', r.exception_types, r.resolution;
    END IF;

    -- NF-A (dated after the receipt) is not flagged.
    SELECT m.* INTO r FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number = 'NF-A';
    IF NOT r.invoice_before_receipt THEN
        RAISE NOTICE 'OK: invoice dated after the receipt is not flagged';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-A wrongly flagged as invoice_before_receipt';
    END IF;

    -- NF-F: order without posted receipt -> quantity_variance + invoice_before_receipt, open and stale.
    SELECT m.* INTO r FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number = 'NF-F';
    IF r.exception_types = ARRAY['quantity_variance', 'invoice_before_receipt']
       AND r.resolution = 'open' AND r.age_days = DATE '2026-09-18' - DATE '2026-03-16' AND r.is_stale THEN
        RAISE NOTICE 'OK: NF-F (nothing received) is open and stale (age % days)', r.age_days;
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-F: types %, resolution %, age %, stale %',
            r.exception_types, r.resolution, r.age_days, r.is_stale;
    END IF;

    -- NF-G: exception with status approved -> released, 4 days to resolve.
    SELECT m.* INTO r FROM vw_invoice_match m JOIN invoice_receipts ir ON ir.document_number = m.invoice_number
     WHERE m.supplier_id = v_sup AND ir.supplier_invoice_number = 'NF-G';
    IF r.resolution = 'released' AND r.days_to_resolve = 4 AND r.age_days IS NULL AND NOT r.is_stale
       AND r.approved_by = 'Smoke Finance' THEN
        RAISE NOTICE 'OK: NF-G is released after 4 days, approved by Smoke Finance';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-G: resolution %, days_to_resolve %, age %, approved_by %',
            r.resolution, r.days_to_resolve, r.age_days, r.approved_by;
    END IF;
END $$;

-- Summary: counts by month, type and resolution, restricted to the SMK invoices.
DO $$
DECLARE
    v_open_price   int;
    v_released_price int;
    v_open_before  int;
BEGIN
    -- Other data may exist outside the transaction, so compare against vw_invoice_match itself.
    SELECT count(*) INTO v_open_price
      FROM vw_invoice_match
     WHERE supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1')
       AND resolution = 'open' AND 'price_variance' = ANY (exception_types);
    SELECT count(*) INTO v_released_price
      FROM vw_invoice_match
     WHERE supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1')
       AND resolution = 'released' AND 'price_variance' = ANY (exception_types);
    SELECT count(*) INTO v_open_before
      FROM vw_invoice_match
     WHERE supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1')
       AND resolution = 'open' AND 'invoice_before_receipt' = ANY (exception_types);

    IF v_open_price = 1 AND v_released_price = 1 AND v_open_before = 2 THEN
        RAISE NOTICE 'OK: SMK exception counts (price open 1, price released 1, before receipt open 2)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: counts price open %, price released %, before receipt open %',
            v_open_price, v_released_price, v_open_before;
    END IF;

    -- The summary must agree with the invoice view for the test month (March 2026, SMK rows only
    -- are checked by type: at least the released price_variance row must exist).
    IF EXISTS (SELECT 1 FROM vw_match_exception_summary
                WHERE posting_month = DATE '2026-03-01' AND exception_type = 'price_variance'
                  AND resolution = 'released' AND invoice_count >= 1 AND gross_amount >= 10300.00) THEN
        RAISE NOTICE 'OK: vw_match_exception_summary lists price_variance / released for 2026-03';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: summary row (2026-03, price_variance, released) not found';
    END IF;
END $$;

ROLLBACK;