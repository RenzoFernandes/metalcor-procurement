-- V9_smoke_test.sql
-- Smoke test for V9 (operational exception views). Everything runs inside a transaction that is
-- rolled back, so no data is left behind. Run after V1..V9 have been applied, ideally with
-- psql -v ON_ERROR_STOP=1 so the first failed check stops the script.
-- Uses its own SMK* master data and does not depend on the seeds. It relies on the approval
-- rules from V2 (buyer up to R$ 10,000, approver up to R$ 100,000, manager above) and on
-- demo_as_of_date() from V8 (2026-09-18).
--
-- Cases (all on SMKSUP1 unless noted):
--   1 order approval          NOREQ (no requisition, 12,000), REQ (approved requisition), REQ2 (approved
--                             requisition without approvals row, exactly 100,000)
--   2 duplicate invoice       'NF-004512' x 'NF 4512' on the same order, amount and date; 'NF-9999' is a control
--   3 duplicate payment       second payment on the duplicate invoice of another order
--   4 payment timeliness      due on Friday paid on Monday (late) x due on Saturday paid on Monday (not late)
--   5 stale blocked invoice   price divergence posted 60 days before demo_as_of_date(); exactly 45 days
--   6 receipt exceptions      two posted receipts, one reversed receipt, one partially_received order
--   7 scorecard               SMKSUP2, 5 counted orders, 5 invoices
--   8 spend by month          SMKCAT, March and April 2026
--
-- Notes: (supplier, supplier_invoice_number) is unique, so every test invoice has its own number.
-- "One invoice per purchase order" is an application rule, not a constraint, so several test
-- invoices share the same order here on purpose. Invoices without lines are used where the
-- three-way match is not the point of the case.

BEGIN;

-- Minimal master data ------------------------------------------------------
INSERT INTO plants (code, name, city, state)
VALUES ('SMK1', 'Smoke Plant', 'Sorocaba', 'SP');

INSERT INTO cost_centers (code, name, plant_id)
VALUES ('SMKCC', 'Smoke Cost Center', (SELECT id FROM plants WHERE code = 'SMK1'));

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
VALUES ('SMKSUP1', 'Fornecedor Smoke 1', 'Belo Horizonte', 'MG'),
       ('SMKSUP2', 'Fornecedor Smoke 2', 'Curitiba', 'PR');

INSERT INTO app_users (name, email, role, plant_id)
VALUES ('Smoke Buyer',    'smoke.buyer@example.com',    'buyer',    (SELECT id FROM plants WHERE code = 'SMK1')),
       ('Smoke Finance',  'smoke.finance@example.com',  'finance',  (SELECT id FROM plants WHERE code = 'SMK1')),
       ('Smoke Approver', 'smoke.approver@example.com', 'approver', (SELECT id FROM plants WHERE code = 'SMK1'));

-- Requisitions --------------------------------------------------------------
INSERT INTO purchase_requisitions (plant_id, cost_center_id, requested_by, needed_by, status, approved_by, approved_at, notes)
SELECT (SELECT id FROM plants WHERE code = 'SMK1'),
       (SELECT id FROM cost_centers WHERE code = 'SMKCC'),
       (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
       DATE '2026-03-10', 'approved',
       (SELECT id FROM app_users WHERE email = 'smoke.approver@example.com'),
       TIMESTAMPTZ '2026-02-27 12:00:00+00', n
  FROM (VALUES ('SMK-REQ-OK'), ('SMK-REQ-NOAPPR')) AS v(n);

-- Only SMK-REQ-OK has an approval decision in the history.
INSERT INTO approvals (purchase_requisition_id, step, required_role, decided_by, decision, amount_evaluated, decided_at)
VALUES ((SELECT id FROM purchase_requisitions WHERE notes = 'SMK-REQ-OK'), 1, 'buyer',
        (SELECT id FROM app_users WHERE email = 'smoke.approver@example.com'), 'approved', 5000.00,
        TIMESTAMPTZ '2026-02-27 12:00:00+00');

-- Purchase orders: one line of SMKMAT1 at 10.0000 each --------------------------
-- note, supplier, requisition note, order_date, expected date, status, quantity
WITH v (note, supplier_code, req_note, order_date, expected, status, qty) AS (
    VALUES
    ('SMK-PO-NOREQ',   'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'issued',    1200.000),
    ('SMK-PO-REQ',     'SMKSUP1', 'SMK-REQ-OK',      DATE '2026-03-01', DATE '2026-03-10', 'issued',     500.000),
    ('SMK-PO-REQ2',    'SMKSUP1', 'SMK-REQ-NOAPPR',  DATE '2026-03-01', DATE '2026-03-10', 'issued',   10000.000),
    ('SMK-PO-DUP',     'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'received',  1000.000),
    ('SMK-PO-DUPPAY',  'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'received',  1000.000),
    ('SMK-PO-FRI',     'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'received',  1000.000),
    ('SMK-PO-SAT',     'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'received',  1000.000),
    ('SMK-PO-STALE',   'SMKSUP1', NULL,             DATE '2026-04-02', DATE '2026-04-09', 'received',  1000.000),
    ('SMK-PO-PART',    'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'received',  1000.000),
    ('SMK-PO-PARTIAL', 'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'partially_received', 1000.000),
    ('SMK-PO-REV',     'SMKSUP1', NULL,             DATE '2026-03-01', DATE '2026-03-10', 'received',  1000.000),
    ('SMK-PO-S2-O1',   'SMKSUP2', NULL,             DATE '2026-03-01', DATE '2026-03-13', 'received',   100.000),
    ('SMK-PO-S2-O2',   'SMKSUP2', NULL,             DATE '2026-03-01', DATE '2026-03-11', 'received',   100.000),
    ('SMK-PO-S2-O3',   'SMKSUP2', NULL,             DATE '2026-03-01', DATE '2026-03-11', 'received',   100.000),
    ('SMK-PO-S2-O4',   'SMKSUP2', NULL,             DATE '2026-03-01', DATE '2026-03-11', 'received',   100.000),
    ('SMK-PO-S2-O5',   'SMKSUP2', NULL,             DATE '2026-03-01', DATE '2026-09-30', 'issued',     200.000),
    ('SMK-PO-S2-O6',   'SMKSUP2', NULL,             DATE '2026-03-01', DATE '2026-03-11', 'cancelled',  999.000)
),
new_orders AS (
    INSERT INTO purchase_orders (supplier_id, purchase_requisition_id, plant_id, buyer_id, order_date,
                                 expected_delivery_date, payment_terms_days, status, notes)
    SELECT s.id, pr.id,
           (SELECT id FROM plants WHERE code = 'SMK1'),
           (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
           v.order_date, v.expected, 30, v.status, v.note
      FROM v
      JOIN suppliers s ON s.code = v.supplier_code
      LEFT JOIN purchase_requisitions pr ON pr.notes = v.req_note
    RETURNING id, notes
)
INSERT INTO purchase_order_items (purchase_order_id, line_number, material_id, quantity, unit_of_measure_id, unit_price)
SELECT n.id, 1, m.id, v.qty, m.unit_of_measure_id, 10.0000
  FROM new_orders n
  JOIN v ON v.note = n.notes
  CROSS JOIN materials m
 WHERE m.code = 'SMKMAT1';

-- Goods receipts ---------------------------------------------------------------
-- tag (kept in delivery_note_number), order note, date, status, quantity
WITH v (tag, po_note, receipt_date, status, qty) AS (
    VALUES
    ('SMK-R-DUP',     'SMK-PO-DUP',     DATE '2026-03-10', 'posted',   1000.000),
    ('SMK-R-DUPPAY',  'SMK-PO-DUPPAY',  DATE '2026-03-10', 'posted',   1000.000),
    ('SMK-R-FRI',     'SMK-PO-FRI',     DATE '2026-03-10', 'posted',   1000.000),
    ('SMK-R-SAT',     'SMK-PO-SAT',     DATE '2026-03-10', 'posted',   1000.000),
    ('SMK-R-STALE',   'SMK-PO-STALE',   DATE '2026-04-10', 'posted',   1000.000),
    ('SMK-R-PART1',   'SMK-PO-PART',    DATE '2026-03-10', 'posted',    400.000),
    ('SMK-R-PART2',   'SMK-PO-PART',    DATE '2026-03-17', 'posted',    600.000),
    ('SMK-R-PARTIAL', 'SMK-PO-PARTIAL', DATE '2026-03-10', 'posted',    400.000),
    ('SMK-R-REV1',    'SMK-PO-REV',     DATE '2026-03-10', 'reversed', 1000.000),
    ('SMK-R-REV2',    'SMK-PO-REV',     DATE '2026-03-10', 'posted',   1000.000),
    ('SMK-R-S2-O1',   'SMK-PO-S2-O1',   DATE '2026-03-16', 'posted',    100.000),  -- Friday due, Monday in: on time (grace)
    ('SMK-R-S2-O2',   'SMK-PO-S2-O2',   DATE '2026-03-13', 'posted',    100.000),  -- 2 days late
    ('SMK-R-S2-O3',   'SMK-PO-S2-O3',   DATE '2026-03-17', 'posted',    100.000),  -- 6 days late
    ('SMK-R-S2-O4',   'SMK-PO-S2-O4',   DATE '2026-03-11', 'posted',    100.000)   -- on the date
),
new_receipts AS (
    INSERT INTO goods_receipts (purchase_order_id, plant_id, received_by, receipt_date, delivery_note_number,
                                status, reversed_by, reversed_at, reversal_reason)
    SELECT po.id,
           (SELECT id FROM plants WHERE code = 'SMK1'),
           (SELECT id FROM app_users WHERE email = 'smoke.buyer@example.com'),
           v.receipt_date, v.tag, v.status,
           CASE WHEN v.status = 'reversed' THEN (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com') END,
           CASE WHEN v.status = 'reversed' THEN TIMESTAMPTZ '2026-03-11 12:00:00+00' END,
           CASE WHEN v.status = 'reversed' THEN 'Quantidade lançada errada' END
      FROM v
      JOIN purchase_orders po ON po.notes = v.po_note
    RETURNING id, purchase_order_id, delivery_note_number
)
INSERT INTO goods_receipt_items (goods_receipt_id, line_number, purchase_order_item_id, quantity_received)
SELECT nr.id, 1, poi.id, v.qty
  FROM new_receipts nr
  JOIN v ON v.tag = nr.delivery_note_number
  JOIN purchase_order_items poi ON poi.purchase_order_id = nr.purchase_order_id;

-- Invoices ---------------------------------------------------------------------
-- number, order note, invoice date, due date, posting date, gross, status, block reason,
-- approved_at (only approved/paid), line quantity and price (null = invoice without lines)
WITH v (num, po_note, inv_date, due_date, post_date, gross, status, block_reason, approved_at, qty, price) AS (
    VALUES
    -- case 2: duplicate invoice (original approved, duplicate blocked, control with another number)
    ('NF-004512', 'SMK-PO-DUP',    DATE '2026-03-15', DATE '2026-04-14', demo_as_of_date() - 75, 10000.00, 'approved', NULL,
        TIMESTAMPTZ '2026-03-20 12:00:00+00', NULL::numeric, NULL::numeric),
    ('NF 4512',    'SMK-PO-DUP',    DATE '2026-03-15', DATE '2026-04-14', demo_as_of_date() - 70, 10000.00, 'blocked',  'Possível fatura duplicada',
        NULL::timestamptz, NULL, NULL),
    ('NF-9999',    'SMK-PO-DUP',    DATE '2026-03-15', DATE '2026-04-14', DATE '2026-03-16',      10000.00, 'received', NULL,
        NULL, NULL, NULL),
    -- case 3: duplicate payment
    ('NF-7001',    'SMK-PO-DUPPAY', DATE '2026-03-15', DATE '2026-04-14', DATE '2026-03-16',      10000.00, 'paid',     NULL,
        TIMESTAMPTZ '2026-03-20 12:00:00+00', NULL, NULL),
    ('NF 07001',   'SMK-PO-DUPPAY', DATE '2026-03-15', DATE '2026-04-14', DATE '2026-03-18',      10000.00, 'paid',     NULL,
        TIMESTAMPTZ '2026-03-22 12:00:00+00', NULL, NULL),
    -- case 4: payment timeliness (due on a Friday and on a Saturday)
    ('NF-FRI',     'SMK-PO-FRI',    DATE '2026-03-11', DATE '2026-04-10', DATE '2026-03-12',      10000.00, 'paid',     NULL,
        TIMESTAMPTZ '2026-03-16 12:00:00+00', NULL, NULL),
    ('NF-SAT',     'SMK-PO-SAT',    DATE '2026-03-12', DATE '2026-04-11', DATE '2026-03-12',      10000.00, 'paid',     NULL,
        TIMESTAMPTZ '2026-03-16 12:00:00+00', NULL, NULL),
    -- case 5: stale blocked invoices (60 days with a price divergence, exactly 45 days)
    ('NF-STALE',   'SMK-PO-STALE',  DATE '2026-07-15', DATE '2026-08-14', demo_as_of_date() - 60, 10300.00, 'blocked',  'Price difference',
        NULL, 1000.000, 10.3000),
    ('NF-S45',     'SMK-PO-STALE',  DATE '2026-07-15', DATE '2026-08-14', demo_as_of_date() - 45, 10000.00, 'blocked',  'Awaiting review',
        NULL, NULL, NULL),
    -- case 7: scorecard of SMKSUP2
    ('NF-I1',      'SMK-PO-S2-O1',  DATE '2026-03-20', DATE '2026-04-19', DATE '2026-03-20',       1000.00, 'matched',  NULL,
        NULL, 100.000, 10.0000),
    ('NF-I2',      'SMK-PO-S2-O2',  DATE '2026-03-20', DATE '2026-04-19', DATE '2026-03-20',       1050.00, 'approved', NULL,
        TIMESTAMPTZ '2026-03-24 12:00:00+00', 100.000, 10.5000),      -- price +5%, released after 4 days
    ('NF-I3',      'SMK-PO-S2-O3',  DATE '2026-03-20', DATE '2026-04-19', demo_as_of_date() - 10,  1050.00, 'blocked',  'Price difference',
        NULL, 100.000, 10.5000),                                       -- price +5%, still open
    ('NF-0100',    'SMK-PO-S2-O4',  DATE '2026-03-20', DATE '2026-04-19', DATE '2026-03-20',       1000.00, 'matched',  NULL,
        NULL, 100.000, 10.0000),
    ('NF 100',     'SMK-PO-S2-O4',  DATE '2026-03-20', DATE '2026-04-19', DATE '2026-03-23',       1000.00, 'blocked',  'Possível fatura duplicada',
        NULL, NULL, NULL)                                              -- duplicate of NF-0100
),
new_invoices AS (
    INSERT INTO invoice_receipts (supplier_id, purchase_order_id, supplier_invoice_number, invoice_date, due_date,
                                  posting_date, gross_amount, status, block_reason, approved_by, approved_at)
    SELECT po.supplier_id, po.id, v.num, v.inv_date, v.due_date, v.post_date, v.gross, v.status, v.block_reason,
           CASE WHEN v.approved_at IS NOT NULL THEN (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com') END,
           v.approved_at
      FROM v
      JOIN purchase_orders po ON po.notes = v.po_note
     -- Ids follow the posting date, so the original of each duplicate pair has the lower id.
     ORDER BY v.post_date, v.num
    RETURNING id, supplier_id, supplier_invoice_number, purchase_order_id
)
INSERT INTO invoice_receipt_items (invoice_receipt_id, line_number, purchase_order_item_id, quantity_invoiced, unit_price)
SELECT ni.id, 1, poi.id, v.qty, v.price
  FROM new_invoices ni
  JOIN v ON v.num = ni.supplier_invoice_number
  JOIN purchase_order_items poi ON poi.purchase_order_id = ni.purchase_order_id
 WHERE v.qty IS NOT NULL;

-- Payments ---------------------------------------------------------------------
-- Case 3: two separate statements so the duplicate invoice's payment has the higher id.
INSERT INTO payments (invoice_receipt_id, amount, scheduled_for, payment_method, status, paid_at, created_by)
SELECT ir.id, 10000.00, DATE '2026-04-14', 'pix', 'paid', TIMESTAMPTZ '2026-04-14 10:00:00+00',
       (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com')
  FROM invoice_receipts ir
 WHERE ir.supplier_invoice_number = 'NF-7001'
   AND ir.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

INSERT INTO payments (invoice_receipt_id, amount, scheduled_for, payment_method, status, paid_at, created_by)
SELECT ir.id, 10000.00, DATE '2026-04-14', 'pix', 'paid', TIMESTAMPTZ '2026-04-14 10:00:00+00',
       (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com')
  FROM invoice_receipts ir
 WHERE ir.supplier_invoice_number = 'NF 07001'
   AND ir.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

-- Case 4: both paid on Monday 2026-04-13.
INSERT INTO payments (invoice_receipt_id, amount, scheduled_for, payment_method, status, paid_at, created_by)
SELECT ir.id, 10000.00, DATE '2026-04-13', 'pix', 'paid', TIMESTAMPTZ '2026-04-13 10:00:00+00',
       (SELECT id FROM app_users WHERE email = 'smoke.finance@example.com')
  FROM invoice_receipts ir
 WHERE ir.supplier_invoice_number IN ('NF-FRI', 'NF-SAT')
   AND ir.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

-- Show the results -----------------------------------------------------------
SELECT po_number, total, requisition_number, requisition_status, approved_requisition, required_role, no_approval
  FROM vw_order_approval_check
 WHERE po_number IN (SELECT document_number FROM purchase_orders WHERE notes LIKE 'SMK-PO-%')
 ORDER BY po_number;

SELECT original_invoice, duplicate_invoice, original_supplier_number, duplicate_supplier_number,
       invoice_date, duplicate_status, resolution, amount
  FROM vw_duplicate_invoice_candidates
 WHERE po_number IN (SELECT document_number FROM purchase_orders WHERE notes LIKE 'SMK-PO-%')
 ORDER BY duplicate_invoice;

SELECT po_number, payments, amount_paid, amount_invoiced_valid, excess_amount, excess_payment
  FROM vw_duplicate_payment_check
 WHERE po_number IN (SELECT document_number FROM purchase_orders WHERE notes LIKE 'SMK-PO-%');

SELECT payment_number, invoice, due_date, due_business_day, scheduled_for, days_late, is_late, status
  FROM vw_payment_timeliness
 WHERE supplier_name LIKE 'Fornecedor Smoke%'
 ORDER BY payment_number;

SELECT invoice_number, posting_date, age_days, age_band, block_reason, amount, is_stale,
       is_duplicate_candidate, has_match_exception
  FROM vw_stale_blocked_invoices
 WHERE supplier_name LIKE 'Fornecedor Smoke%'
 ORDER BY invoice_number;

SELECT exception_type, po_number, po_status, goods_receipt_number, receipt_date, posted_receipts,
       reversal_reason, reversed_by
  FROM vw_receipt_exceptions
 WHERE supplier_name LIKE 'Fornecedor Smoke%'
 ORDER BY exception_type, po_number;

SELECT supplier_code, orders, total_spend, on_time_delivery_pct, avg_days_late, invoices,
       exception_rate_pct, avg_days_to_resolve, open_exceptions
  FROM vw_supplier_scorecard
 WHERE supplier_code LIKE 'SMKSUP%';

SELECT order_month, category_code, orders, items, total_value, suppliers
  FROM vw_spend_by_month_category
 WHERE category_code = 'SMKCAT';

-- Checks ---------------------------------------------------------------------
-- 1. vw_order_approval_check
DO $$
DECLARE
    v_total         numeric;
    v_req_number    text;
    v_req_status    text;
    v_approved      boolean;
    v_role          text;
    v_no_approval   boolean;
BEGIN
    -- Order without requisition above R$ 10,000.
    SELECT total, requisition_number, requisition_status, approved_requisition, required_role, no_approval
      INTO v_total, v_req_number, v_req_status, v_approved, v_role, v_no_approval
      FROM vw_order_approval_check
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-NOREQ');
    IF v_total = 12000.00 AND v_req_number IS NULL AND v_req_status IS NULL
       AND v_approved IS FALSE AND v_role = 'approver' AND v_no_approval IS TRUE THEN
        RAISE NOTICE 'OK: order without requisition (12,000) has no_approval and needs the approver role';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NOREQ: total %, requisition %, approved %, role %, no_approval %',
            v_total, v_req_number, v_approved, v_role, v_no_approval;
    END IF;

    -- Order with an approved requisition that has an approvals row.
    SELECT total, requisition_status, approved_requisition, required_role, no_approval
      INTO v_total, v_req_status, v_approved, v_role, v_no_approval
      FROM vw_order_approval_check
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-REQ');
    IF v_total = 5000.00 AND v_req_status = 'approved' AND v_approved IS TRUE
       AND v_role = 'buyer' AND v_no_approval IS FALSE THEN
        RAISE NOTICE 'OK: order with an approved requisition (5,000) is not flagged';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: REQ: total %, status %, approved %, role %, no_approval %',
            v_total, v_req_status, v_approved, v_role, v_no_approval;
    END IF;

    -- Requisition approved on the header but without a row in approvals; total on the manager boundary.
    SELECT total, requisition_status, approved_requisition, required_role, no_approval
      INTO v_total, v_req_status, v_approved, v_role, v_no_approval
      FROM vw_order_approval_check
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-REQ2');
    IF v_total = 100000.00 AND v_req_status = 'approved' AND v_approved IS FALSE
       AND v_role = 'manager' AND v_no_approval IS TRUE THEN
        RAISE NOTICE 'OK: requisition approved without an approvals row is flagged; 100,000 needs the manager role';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: REQ2: total %, status %, approved %, role %, no_approval %',
            v_total, v_req_status, v_approved, v_role, v_no_approval;
    END IF;
END $$;

-- 2. vw_duplicate_invoice_candidates
DO $$
DECLARE
    v_rows      int;
    v_original  text;
    v_duplicate text;
    v_orig_num  text;
    v_dup_num   text;
    v_resolution text;
    v_amount    numeric;
    v_expected_original  text;
    v_expected_duplicate text;
BEGIN
    SELECT document_number INTO v_expected_original
      FROM invoice_receipts WHERE supplier_invoice_number = 'NF-004512'
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');
    SELECT document_number INTO v_expected_duplicate
      FROM invoice_receipts WHERE supplier_invoice_number = 'NF 4512'
       AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

    SELECT count(*) INTO v_rows
      FROM vw_duplicate_invoice_candidates
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-DUP');
    SELECT original_invoice, duplicate_invoice, original_supplier_number, duplicate_supplier_number, resolution, amount
      INTO v_original, v_duplicate, v_orig_num, v_dup_num, v_resolution, v_amount
      FROM vw_duplicate_invoice_candidates
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-DUP')
     LIMIT 1;

    IF v_rows = 1 AND v_original = v_expected_original AND v_duplicate = v_expected_duplicate
       AND v_orig_num = 'NF-004512' AND v_dup_num = 'NF 4512'
       AND v_resolution = 'open' AND v_amount = 10000.00 THEN
        RAISE NOTICE 'OK: NF-004512 x NF 4512 is one open duplicate; the control invoice NF-9999 is not flagged';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: duplicate invoice: rows %, original %, duplicate %, numbers % / %, resolution %, amount %',
            v_rows, v_original, v_duplicate, v_orig_num, v_dup_num, v_resolution, v_amount;
    END IF;

    -- The duplicate of the DUPPAY order was paid.
    SELECT resolution INTO v_resolution
      FROM vw_duplicate_invoice_candidates
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-DUPPAY');
    IF v_resolution = 'paid' THEN
        RAISE NOTICE 'OK: a duplicate that was paid has resolution paid';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: DUPPAY duplicate resolution is %', v_resolution;
    END IF;
END $$;

-- 3. vw_duplicate_payment_check
DO $$
DECLARE
    v_rows      int;
    v_payments  text[];
    v_paid      numeric;
    v_valid     numeric;
    v_excess    numeric;
    v_excess_py text;
    v_expected  text;
BEGIN
    SELECT p.document_number INTO v_expected
      FROM payments p
      JOIN invoice_receipts ir ON ir.id = p.invoice_receipt_id
     WHERE ir.supplier_invoice_number = 'NF 07001'
       AND ir.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');

    SELECT count(*) INTO v_rows
      FROM vw_duplicate_payment_check
     WHERE po_number IN (SELECT document_number FROM purchase_orders WHERE notes LIKE 'SMK-PO-%');
    SELECT payments, amount_paid, amount_invoiced_valid, excess_amount, excess_payment
      INTO v_payments, v_paid, v_valid, v_excess, v_excess_py
      FROM vw_duplicate_payment_check
     WHERE po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-DUPPAY');

    -- Only DUPPAY has two active payments: FRI and SAT have one payment each, on separate orders.
    IF v_rows = 1 AND cardinality(v_payments) = 2 AND v_paid = 20000.00 AND v_valid = 10000.00
       AND v_excess = 10000.00 AND v_excess_py = v_expected AND v_payments[2] = v_expected THEN
        RAISE NOTICE 'OK: two active payments on one order: excess 10,000 is the payment of the duplicate invoice';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: duplicate payment: rows %, payments %, paid %, valid %, excess %, excess payment % (expected %)',
            v_rows, v_payments, v_paid, v_valid, v_excess, v_excess_py, v_expected;
    END IF;
END $$;

-- 4. vw_payment_timeliness
DO $$
DECLARE
    v_due_bd     date;
    v_days_late  int;
    v_is_late    boolean;
BEGIN
    -- Due on Friday 2026-04-10, scheduled on Monday 2026-04-13: late by 3 calendar days.
    SELECT t.due_business_day, t.days_late, t.is_late
      INTO v_due_bd, v_days_late, v_is_late
      FROM vw_payment_timeliness t
      JOIN invoice_receipts ir ON ir.document_number = t.invoice
     WHERE ir.supplier_invoice_number = 'NF-FRI'
       AND ir.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');
    IF v_due_bd = DATE '2026-04-10' AND v_days_late = 3 AND v_is_late IS TRUE THEN
        RAISE NOTICE 'OK: due on Friday, scheduled on Monday is late (3 days)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-FRI: due business day %, days late %, is late %',
            v_due_bd, v_days_late, v_is_late;
    END IF;

    -- Due on Saturday 2026-04-11 moves to Monday 2026-04-13: scheduled on Monday is not late.
    SELECT t.due_business_day, t.days_late, t.is_late
      INTO v_due_bd, v_days_late, v_is_late
      FROM vw_payment_timeliness t
      JOIN invoice_receipts ir ON ir.document_number = t.invoice
     WHERE ir.supplier_invoice_number = 'NF-SAT'
       AND ir.supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1');
    IF v_due_bd = DATE '2026-04-13' AND v_days_late = 0 AND v_is_late IS FALSE THEN
        RAISE NOTICE 'OK: due on Saturday, scheduled on Monday is not late';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-SAT: due business day %, days late %, is late %',
            v_due_bd, v_days_late, v_is_late;
    END IF;
END $$;

-- 5. vw_stale_blocked_invoices
DO $$
DECLARE
    v_age       int;
    v_band      text;
    v_stale     boolean;
    v_dup       boolean;
    v_exception boolean;
BEGIN
    SELECT age_days, age_band, is_stale, is_duplicate_candidate, has_match_exception
      INTO v_age, v_band, v_stale, v_dup, v_exception
      FROM vw_stale_blocked_invoices
     WHERE invoice_number = (SELECT document_number FROM invoice_receipts
                              WHERE supplier_invoice_number = 'NF-STALE'
                                AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1'));
    IF v_age = 60 AND v_band = '46-90' AND v_stale IS TRUE AND v_dup IS FALSE AND v_exception IS TRUE THEN
        RAISE NOTICE 'OK: blocked price divergence posted 60 days ago is stale, band 46-90';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-STALE: age %, band %, stale %, duplicate %, exception %',
            v_age, v_band, v_stale, v_dup, v_exception;
    END IF;

    SELECT age_days, age_band, is_stale
      INTO v_age, v_band, v_stale
      FROM vw_stale_blocked_invoices
     WHERE invoice_number = (SELECT document_number FROM invoice_receipts
                              WHERE supplier_invoice_number = 'NF-S45'
                                AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1'));
    IF v_age = 45 AND v_band = '31-45' AND v_stale IS FALSE THEN
        RAISE NOTICE 'OK: exactly 45 days is not stale (band 31-45)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF-S45: age %, band %, stale %', v_age, v_band, v_stale;
    END IF;

    -- The blocked duplicate is older than 45 days: stale by age, but not a divergence.
    SELECT age_days, is_stale, is_duplicate_candidate, has_match_exception
      INTO v_age, v_stale, v_dup, v_exception
      FROM vw_stale_blocked_invoices
     WHERE invoice_number = (SELECT document_number FROM invoice_receipts
                              WHERE supplier_invoice_number = 'NF 4512'
                                AND supplier_id = (SELECT id FROM suppliers WHERE code = 'SMKSUP1'));
    IF v_age = 70 AND v_stale IS TRUE AND v_dup IS TRUE AND v_exception IS FALSE THEN
        RAISE NOTICE 'OK: blocked duplicate is stale by age but marked is_duplicate_candidate';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: NF 4512: age %, stale %, duplicate %, exception %',
            v_age, v_stale, v_dup, v_exception;
    END IF;
END $$;

-- 6. vw_receipt_exceptions
DO $$
DECLARE
    v_gr         text;
    v_posted     bigint;
    v_reason     text;
    v_by         text;
    v_count      int;
    v_expected   text;
BEGIN
    -- Two posted receipts: one partial_delivery row pointing to the first one.
    SELECT document_number INTO v_expected FROM goods_receipts WHERE delivery_note_number = 'SMK-R-PART1';
    SELECT count(*) INTO v_count
      FROM vw_receipt_exceptions
     WHERE exception_type = 'partial_delivery'
       AND po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-PART');
    SELECT goods_receipt_number, posted_receipts INTO v_gr, v_posted
      FROM vw_receipt_exceptions
     WHERE exception_type = 'partial_delivery'
       AND po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-PART');
    IF v_count = 1 AND v_gr = v_expected AND v_posted = 2 THEN
        RAISE NOTICE 'OK: order with two posted receipts is one partial_delivery pointing to the first receipt';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: partial (two receipts): rows %, receipt % (expected %), posted %',
            v_count, v_gr, v_expected, v_posted;
    END IF;

    -- partially_received status with a single posted receipt.
    SELECT document_number INTO v_expected FROM goods_receipts WHERE delivery_note_number = 'SMK-R-PARTIAL';
    SELECT goods_receipt_number, posted_receipts INTO v_gr, v_posted
      FROM vw_receipt_exceptions
     WHERE exception_type = 'partial_delivery'
       AND po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-PARTIAL');
    IF v_gr = v_expected AND v_posted = 1 THEN
        RAISE NOTICE 'OK: order with status partially_received is a partial_delivery';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: partial (status): receipt % (expected %), posted %', v_gr, v_expected, v_posted;
    END IF;

    -- Reversed receipt: reason and user; its order has one posted (corrected) receipt, so it is not partial.
    SELECT document_number INTO v_expected FROM goods_receipts WHERE delivery_note_number = 'SMK-R-REV1';
    SELECT goods_receipt_number, reversal_reason, reversed_by INTO v_gr, v_reason, v_by
      FROM vw_receipt_exceptions
     WHERE exception_type = 'reversed_receipt'
       AND po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-REV');
    SELECT count(*) INTO v_count
      FROM vw_receipt_exceptions
     WHERE exception_type = 'partial_delivery'
       AND po_number = (SELECT document_number FROM purchase_orders WHERE notes = 'SMK-PO-REV');
    IF v_gr = v_expected AND v_reason = 'Quantidade lançada errada' AND v_by = 'Smoke Finance' AND v_count = 0 THEN
        RAISE NOTICE 'OK: reversed receipt lists reason and user; a reversal plus correction is not a partial delivery';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: reversed: receipt % (expected %), reason %, by %, partial rows %',
            v_gr, v_expected, v_reason, v_by, v_count;
    END IF;
END $$;

-- 7. vw_supplier_scorecard (SMKSUP2)
-- Orders counted: O1..O5 (O6 is cancelled). Spend: 4 x 1,000 + 2,000 = 6,000.
-- Delivered O1..O4; on time: O1 (Friday due, Monday in) and O4 = 2 of 4 = 50.0%.
-- Late: O2 (+2 days) and O3 (+6 days) = average 4.0.
-- Invoices: 5; exceptions: NF-I2 (released), NF-I3 (open) and the duplicate NF 100 (open) = 60.0%.
-- Days to resolve: NF-I2 = 4. Open exceptions: NF-I3 and NF 100 = 2.
DO $$
DECLARE
    v_orders    bigint;
    v_spend     numeric;
    v_on_time   numeric;
    v_late      numeric;
    v_invoices  bigint;
    v_rate      numeric;
    v_resolve   numeric;
    v_open      bigint;
BEGIN
    SELECT orders, total_spend, on_time_delivery_pct, avg_days_late, invoices, exception_rate_pct,
           avg_days_to_resolve, open_exceptions
      INTO v_orders, v_spend, v_on_time, v_late, v_invoices, v_rate, v_resolve, v_open
      FROM vw_supplier_scorecard
     WHERE supplier_code = 'SMKSUP2';
    IF v_orders = 5 AND v_spend = 6000.00 AND v_on_time = 50.0 AND v_late = 4.0
       AND v_invoices = 5 AND v_rate = 60.0 AND v_resolve = 4.0 AND v_open = 2 THEN
        RAISE NOTICE 'OK: scorecard of SMKSUP2 (5 orders, 6,000, 50.0%% on time, 4.0 days late, 60.0%% exceptions, 2 open)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: scorecard: orders %, spend %, on time %, days late %, invoices %, rate %, resolve %, open %',
            v_orders, v_spend, v_on_time, v_late, v_invoices, v_rate, v_resolve, v_open;
    END IF;

    -- Ordered by spend: the supplier with the larger spend comes first.
    IF (SELECT supplier_code FROM vw_supplier_scorecard WHERE supplier_code LIKE 'SMKSUP%' LIMIT 1) = 'SMKSUP1' THEN
        RAISE NOTICE 'OK: scorecard is ordered by total spend';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: scorecard is not ordered by total spend';
    END IF;
END $$;

-- 8. vw_spend_by_month_category (SMKCAT)
-- March 2026: SMKSUP1 orders except SMK-PO-STALE = 187,000 in 10 orders, plus SMKSUP2 = 6,000 in 5 orders
-- (the cancelled O6 is left out). April 2026: SMK-PO-STALE, 10,000.
DO $$
DECLARE
    v_orders    bigint;
    v_items     bigint;
    v_value     numeric;
    v_suppliers bigint;
BEGIN
    SELECT orders, items, total_value, suppliers INTO v_orders, v_items, v_value, v_suppliers
      FROM vw_spend_by_month_category
     WHERE order_month = DATE '2026-03-01' AND category_code = 'SMKCAT';
    IF v_orders = 15 AND v_items = 15 AND v_value = 193000.00 AND v_suppliers = 2 THEN
        RAISE NOTICE 'OK: spend for 2026-03 (15 orders, 193,000, 2 suppliers; cancelled order left out)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: spend 2026-03: orders %, items %, value %, suppliers %',
            v_orders, v_items, v_value, v_suppliers;
    END IF;

    SELECT orders, items, total_value, suppliers INTO v_orders, v_items, v_value, v_suppliers
      FROM vw_spend_by_month_category
     WHERE order_month = DATE '2026-04-01' AND category_code = 'SMKCAT';
    IF v_orders = 1 AND v_items = 1 AND v_value = 10000.00 AND v_suppliers = 1 THEN
        RAISE NOTICE 'OK: spend for 2026-04 (1 order, 10,000, 1 supplier)';
    ELSE
        RAISE EXCEPTION 'SMOKE TEST FAILED: spend 2026-04: orders %, items %, value %, suppliers %',
            v_orders, v_items, v_value, v_suppliers;
    END IF;
END $$;

ROLLBACK;