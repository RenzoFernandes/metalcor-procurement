-- V9__operational_exception_views.sql
-- Metalcor Procurement v0.2b: operational exception views (approval, duplicates, payments, receipts),
-- supplier scorecard and spend summary.
-- Requires V1..V8 (uses vw_invoice_match and demo_as_of_date() from V8). Fictional data only. Not SAP.
--
-- Views only: no table or trigger is touched.
--
-- Which document each anomaly type of db/seed/anomalies_manifest.csv points to:
--   order_without_approval -> purchase_orders (PO)   -> vw_order_approval_check.po_number
--   duplicate_invoice      -> invoice_receipts (IR)  -> the DUPLICATE, not the original:
--                                                       vw_duplicate_invoice_candidates.duplicate_invoice
--   duplicate_payment      -> payments (PY)          -> the payment of the duplicate invoice (excess):
--                                                       vw_duplicate_payment_check.excess_payment
--   late_payment           -> payments (PY)          -> vw_payment_timeliness.payment_number
--   stale_blocked_invoice  -> invoice_receipts (IR)  -> vw_stale_blocked_invoices.invoice_number
--   partial_delivery       -> goods_receipts (GR)    -> the FIRST receipt of the order:
--                                                       vw_receipt_exceptions.goods_receipt_number
--   reversed_receipt       -> goods_receipts (GR)    -> the reversed receipt:
--                                                       vw_receipt_exceptions.goods_receipt_number
--
-- Business days: only Saturdays and Sundays are skipped. There is no holiday calendar in the
-- database, same as the seed generator.
--
-- Indexes: none added. The lookups these views need are covered by existing indexes
-- (idx_approvals_purchase_requisition_id, idx_invoice_receipts_purchase_order_id,
-- idx_payments_invoice_receipt_id, idx_goods_receipts_purchase_order_id, ...). At this data
-- volume an extra index would only cost writes.

-- ---------------------------------------------------------------------------
-- vw_order_approval_check: one row per purchase order
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_order_approval_check AS
SELECT
    po.document_number                      AS po_number,
    s.name                                  AS supplier_name,
    po.order_date,
    po.status                               AS po_status,
    tot.total                               AS total,
    pr.document_number                      AS requisition_number,
    pr.status                               AS requisition_status,
    apr.is_approved                         AS approved_requisition,
    rule.required_role                      AS required_role,
    NOT apr.is_approved                     AS no_approval
FROM purchase_orders po
JOIN suppliers s ON s.id = po.supplier_id
LEFT JOIN purchase_requisitions pr ON pr.id = po.purchase_requisition_id
CROSS JOIN LATERAL (
    SELECT COALESCE(sum(poi.line_total), 0)::numeric(15,2) AS total
      FROM purchase_order_items poi
     WHERE poi.purchase_order_id = po.id
) tot
CROSS JOIN LATERAL (
    -- Approved only when the requisition exists, is approved AND has an approved decision in the history.
    SELECT (pr.id IS NOT NULL
            AND pr.status = 'approved'
            AND EXISTS (SELECT 1
                          FROM approvals a
                         WHERE a.purchase_requisition_id = pr.id
                           AND a.decision = 'approved')) AS is_approved
) apr
LEFT JOIN LATERAL (
    SELECT ar.required_role
      FROM approval_rules ar
     WHERE ar.document_type = 'purchase_requisition'
       AND ar.active
       AND tot.total >= ar.min_amount
       AND (ar.max_amount IS NULL OR tot.total < ar.max_amount)
     LIMIT 1
) rule ON true;

COMMENT ON VIEW vw_order_approval_check IS 'One row per purchase order with the approval evidence of its requisition. no_approval marks orders without an approved requisition (detected type: order_without_approval).';
COMMENT ON COLUMN vw_order_approval_check.po_number             IS 'Purchase order document number.';
COMMENT ON COLUMN vw_order_approval_check.total                 IS 'Sum of the order line totals.';
COMMENT ON COLUMN vw_order_approval_check.requisition_number    IS 'Requisition document number. Null when the order has no requisition.';
COMMENT ON COLUMN vw_order_approval_check.requisition_status    IS 'Status of the requisition. Null when the order has no requisition.';
COMMENT ON COLUMN vw_order_approval_check.approved_requisition  IS 'True only when the requisition exists, is approved and has an approved row in approvals.';
COMMENT ON COLUMN vw_order_approval_check.required_role         IS 'Role required by approval_rules (purchase_requisition) for the order total.';
COMMENT ON COLUMN vw_order_approval_check.no_approval           IS 'True when the order has no approved requisition (detected type: order_without_approval).';

-- ---------------------------------------------------------------------------
-- vw_duplicate_invoice_candidates: one row per duplicate invoice
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_duplicate_invoice_candidates AS
WITH normalized AS (
    SELECT
        ir.id,
        ir.document_number,
        ir.supplier_id,
        ir.purchase_order_id,
        ir.supplier_invoice_number,
        ir.invoice_date,
        ir.gross_amount,
        ir.status,
        -- Digits only, without leading zeros: 'NF-004512', 'NF 4512' and 'NF4512' all become '4512'.
        ltrim(regexp_replace(ir.supplier_invoice_number, '\D', '', 'g'), '0') AS number_key
      FROM invoice_receipts ir
),
grouped AS (
    SELECT
        n.*,
        min(n.id)  OVER w AS original_id,
        count(*)   OVER w AS group_size
      FROM normalized n
     WHERE n.number_key <> ''
    WINDOW w AS (PARTITION BY n.supplier_id, n.purchase_order_id, n.gross_amount, n.invoice_date, n.number_key)
)
SELECT
    o.document_number               AS original_invoice,
    d.document_number               AS duplicate_invoice,
    s.name                          AS supplier_name,
    po.document_number              AS po_number,
    o.supplier_invoice_number       AS original_supplier_number,
    d.supplier_invoice_number       AS duplicate_supplier_number,
    d.invoice_date,
    o.status                        AS original_status,
    d.status                        AS duplicate_status,
    CASE d.status
        WHEN 'blocked'   THEN 'open'
        WHEN 'cancelled' THEN 'cancelled'
        WHEN 'paid'      THEN 'paid'
        ELSE 'other'
    END                             AS resolution,
    d.gross_amount                  AS amount
FROM grouped d
JOIN grouped o ON o.id = d.original_id
JOIN suppliers s ON s.id = d.supplier_id
JOIN purchase_orders po ON po.id = d.purchase_order_id
WHERE d.group_size > 1
  AND d.id <> d.original_id;

COMMENT ON VIEW vw_duplicate_invoice_candidates IS 'Near-duplicate invoices: same supplier, purchase order, gross amount and invoice date, and the same supplier invoice number once normalized to digits without leading zeros. The lowest id is the original; each other invoice is one duplicate row (detected type: duplicate_invoice).';
COMMENT ON COLUMN vw_duplicate_invoice_candidates.original_invoice          IS 'Document number of the original invoice (lowest id of the group).';
COMMENT ON COLUMN vw_duplicate_invoice_candidates.duplicate_invoice         IS 'Document number of the duplicate invoice. This is what the manifest lists as duplicate_invoice.';
COMMENT ON COLUMN vw_duplicate_invoice_candidates.original_supplier_number  IS 'Supplier invoice number of the original, as written.';
COMMENT ON COLUMN vw_duplicate_invoice_candidates.duplicate_supplier_number IS 'Supplier invoice number of the duplicate, as written.';
COMMENT ON COLUMN vw_duplicate_invoice_candidates.resolution                IS 'open (duplicate is blocked), cancelled, paid (duplicate was paid) or other.';
COMMENT ON COLUMN vw_duplicate_invoice_candidates.amount                    IS 'Gross amount of the duplicate invoice.';

-- ---------------------------------------------------------------------------
-- vw_duplicate_payment_check: one row per order with more than one active payment
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_duplicate_payment_check AS
WITH active_payments AS (
    SELECT p.id, p.document_number, p.amount, ir.purchase_order_id
      FROM payments p
      JOIN invoice_receipts ir ON ir.id = p.invoice_receipt_id
     WHERE p.status <> 'cancelled'
),
multi AS (
    SELECT
        ap.purchase_order_id,
        array_agg(ap.document_number ORDER BY ap.id) AS payment_numbers,
        sum(ap.amount)::numeric(15,2)                AS amount_paid,
        max(ap.id)                                   AS excess_payment_id
      FROM active_payments ap
     GROUP BY ap.purchase_order_id
    HAVING count(*) > 1
),
valid_invoiced AS (
    -- Invoices that are not cancelled and are not the duplicate of another invoice.
    SELECT ir.purchase_order_id, sum(ir.gross_amount)::numeric(15,2) AS amount_invoiced_valid
      FROM invoice_receipts ir
      LEFT JOIN vw_duplicate_invoice_candidates dup ON dup.duplicate_invoice = ir.document_number
     WHERE ir.status <> 'cancelled'
       AND dup.duplicate_invoice IS NULL
     GROUP BY ir.purchase_order_id
)
SELECT
    po.document_number                                          AS po_number,
    s.name                                                      AS supplier_name,
    m.payment_numbers                                           AS payments,
    m.amount_paid,
    COALESCE(v.amount_invoiced_valid, 0)::numeric(15,2)         AS amount_invoiced_valid,
    (m.amount_paid - COALESCE(v.amount_invoiced_valid, 0))::numeric(15,2) AS excess_amount,
    ex.document_number                                          AS excess_payment
FROM multi m
JOIN purchase_orders po ON po.id = m.purchase_order_id
JOIN suppliers s ON s.id = po.supplier_id
JOIN payments ex ON ex.id = m.excess_payment_id
LEFT JOIN valid_invoiced v ON v.purchase_order_id = m.purchase_order_id;

COMMENT ON VIEW vw_duplicate_payment_check IS 'Orders with more than one non-cancelled payment across their invoices: possible duplicate payment (detected type: duplicate_payment). Compares what was paid or scheduled with what was validly invoiced.';
COMMENT ON COLUMN vw_duplicate_payment_check.payments               IS 'Document numbers of the non-cancelled payments, in id order.';
COMMENT ON COLUMN vw_duplicate_payment_check.amount_paid            IS 'Sum of the non-cancelled payments (paid or still scheduled).';
COMMENT ON COLUMN vw_duplicate_payment_check.amount_invoiced_valid  IS 'Sum of the non-cancelled invoices of the order, not counting duplicates.';
COMMENT ON COLUMN vw_duplicate_payment_check.excess_amount          IS 'amount_paid minus amount_invoiced_valid.';
COMMENT ON COLUMN vw_duplicate_payment_check.excess_payment         IS 'Document number of the excess payment: the one with the highest id. This is what the manifest lists as duplicate_payment.';

-- ---------------------------------------------------------------------------
-- vw_payment_timeliness: one row per payment
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_payment_timeliness AS
SELECT
    p.document_number               AS payment_number,
    ir.document_number              AS invoice,
    s.name                          AS supplier_name,
    ir.due_date,
    bd.due_business_day,
    p.scheduled_for,
    p.paid_at,
    GREATEST(p.scheduled_for - bd.due_business_day, 0) AS days_late,
    (p.status <> 'cancelled' AND p.scheduled_for > bd.due_business_day) AS is_late,
    p.status
FROM payments p
JOIN invoice_receipts ir ON ir.id = p.invoice_receipt_id
JOIN suppliers s ON s.id = ir.supplier_id
CROSS JOIN LATERAL (
    -- A due date on a weekend moves to the next Monday: paying then is not late.
    SELECT (ir.due_date + CASE extract(isodow FROM ir.due_date)::int
                              WHEN 6 THEN 2
                              WHEN 7 THEN 1
                              ELSE 0
                          END) AS due_business_day
) bd;

COMMENT ON VIEW vw_payment_timeliness IS 'One row per payment, comparing the scheduled date with the due date moved to the next business day (weekends only). Detected type: late_payment.';
COMMENT ON COLUMN vw_payment_timeliness.payment_number    IS 'Payment document number.';
COMMENT ON COLUMN vw_payment_timeliness.invoice           IS 'Internal invoice document number.';
COMMENT ON COLUMN vw_payment_timeliness.due_business_day  IS 'due_date, or the next Monday when it falls on a Saturday or Sunday.';
COMMENT ON COLUMN vw_payment_timeliness.days_late         IS 'Calendar days between due_business_day and scheduled_for, 0 when on time.';
COMMENT ON COLUMN vw_payment_timeliness.is_late           IS 'True when scheduled_for is after due_business_day. Cancelled payments are never late.';

-- ---------------------------------------------------------------------------
-- vw_stale_blocked_invoices: one row per blocked invoice
-- ---------------------------------------------------------------------------
-- The manifest type stale_blocked_invoice has 6 rows and refers ONLY to invoices blocked by a
-- match divergence (price or quantity), the same set as vw_invoice_match.is_stale. Blocked
-- duplicates (block_reason 'Possível fatura duplicada') are also blocked and can be older than
-- 45 days, so they show up here with is_stale = true too. Use is_duplicate_candidate = false
-- (or has_match_exception = true) to get the manifest set.
CREATE OR REPLACE VIEW vw_stale_blocked_invoices AS
SELECT
    ir.document_number              AS invoice_number,
    s.name                          AS supplier_name,
    po.document_number              AS po_number,
    ir.posting_date,
    age.age_days,
    CASE
        WHEN age.age_days <= 15 THEN '0-15'
        WHEN age.age_days <= 30 THEN '16-30'
        WHEN age.age_days <= 45 THEN '31-45'
        WHEN age.age_days <= 90 THEN '46-90'
        ELSE '90+'
    END                             AS age_band,
    ir.block_reason,
    ir.gross_amount                 AS amount,
    (age.age_days > 45)             AS is_stale,
    (dup.duplicate_invoice IS NOT NULL) AS is_duplicate_candidate,
    COALESCE(m.has_exception, false)    AS has_match_exception
FROM invoice_receipts ir
JOIN suppliers s ON s.id = ir.supplier_id
JOIN purchase_orders po ON po.id = ir.purchase_order_id
LEFT JOIN vw_invoice_match m ON m.invoice_number = ir.document_number
LEFT JOIN vw_duplicate_invoice_candidates dup ON dup.duplicate_invoice = ir.document_number
CROSS JOIN LATERAL (
    SELECT (demo_as_of_date() - ir.posting_date) AS age_days
) age
WHERE ir.status = 'blocked';

COMMENT ON VIEW vw_stale_blocked_invoices IS 'Blocked invoices with age since posting and age band. is_stale = older than 45 days. Includes blocked duplicates; the stale_blocked_invoice manifest set is is_stale AND NOT is_duplicate_candidate (blocked by a match divergence, same as vw_invoice_match.is_stale).';
COMMENT ON COLUMN vw_stale_blocked_invoices.age_days               IS 'demo_as_of_date() minus posting_date.';
COMMENT ON COLUMN vw_stale_blocked_invoices.age_band               IS 'Age band: 0-15, 16-30, 31-45, 46-90 or 90+ days.';
COMMENT ON COLUMN vw_stale_blocked_invoices.is_stale               IS 'True when the invoice has been blocked for more than 45 days.';
COMMENT ON COLUMN vw_stale_blocked_invoices.is_duplicate_candidate IS 'True when the invoice is a duplicate in vw_duplicate_invoice_candidates.';
COMMENT ON COLUMN vw_stale_blocked_invoices.has_match_exception    IS 'True when vw_invoice_match reports at least one exception for the invoice.';

-- ---------------------------------------------------------------------------
-- vw_receipt_exceptions: partial deliveries and reversed receipts
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_receipt_exceptions AS
-- (a) partial_delivery: one row per order, pointing to its first posted receipt.
SELECT
    'partial_delivery'::text        AS exception_type,
    po.document_number              AS po_number,
    s.name                          AS supplier_name,
    po.status                       AS po_status,
    first_gr.document_number        AS goods_receipt_number,
    first_gr.receipt_date,
    agg.posted_receipts,
    NULL::text                      AS reversal_reason,
    NULL::text                      AS reversed_by,
    NULL::timestamptz               AS reversed_at
FROM purchase_orders po
JOIN suppliers s ON s.id = po.supplier_id
CROSS JOIN LATERAL (
    SELECT count(*) AS posted_receipts
      FROM goods_receipts gr
     WHERE gr.purchase_order_id = po.id AND gr.status = 'posted'
) agg
JOIN LATERAL (
    SELECT gr.document_number, gr.receipt_date
      FROM goods_receipts gr
     WHERE gr.purchase_order_id = po.id AND gr.status = 'posted'
     ORDER BY gr.receipt_date, gr.id
     LIMIT 1
) first_gr ON true
WHERE agg.posted_receipts > 1 OR po.status = 'partially_received'
UNION ALL
-- (b) reversed_receipt: one row per reversed receipt.
SELECT
    'reversed_receipt'::text,
    po.document_number,
    s.name,
    po.status,
    gr.document_number,
    gr.receipt_date,
    (SELECT count(*) FROM goods_receipts g2 WHERE g2.purchase_order_id = po.id AND g2.status = 'posted'),
    gr.reversal_reason,
    u.name,
    gr.reversed_at
FROM goods_receipts gr
JOIN purchase_orders po ON po.id = gr.purchase_order_id
JOIN suppliers s ON s.id = po.supplier_id
LEFT JOIN app_users u ON u.id = gr.reversed_by
WHERE gr.status = 'reversed';

COMMENT ON VIEW vw_receipt_exceptions IS 'Receipt exceptions in one list. partial_delivery: orders with more than one posted receipt or status partially_received, one row per order pointing to the first posted receipt. reversed_receipt: one row per reversed receipt.';
COMMENT ON COLUMN vw_receipt_exceptions.exception_type        IS 'partial_delivery or reversed_receipt.';
COMMENT ON COLUMN vw_receipt_exceptions.goods_receipt_number  IS 'partial_delivery: first posted receipt of the order (by receipt date, then id). reversed_receipt: the reversed receipt.';
COMMENT ON COLUMN vw_receipt_exceptions.posted_receipts       IS 'Number of posted receipts of the order.';
COMMENT ON COLUMN vw_receipt_exceptions.reversal_reason       IS 'Reason of the reversal. Only for reversed_receipt.';
COMMENT ON COLUMN vw_receipt_exceptions.reversed_by           IS 'Name of the user who reversed the receipt. Only for reversed_receipt.';

-- ---------------------------------------------------------------------------
-- vw_supplier_scorecard: one row per supplier
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_supplier_scorecard AS
WITH orders AS (
    SELECT
        po.id,
        po.supplier_id,
        po.expected_delivery_date,
        -- Expected date moved to a business day, plus one business day of grace.
        (po.expected_delivery_date + CASE extract(isodow FROM po.expected_delivery_date)::int
                                         WHEN 5 THEN 3
                                         WHEN 6 THEN 3
                                         WHEN 7 THEN 2
                                         ELSE 1
                                     END) AS on_time_limit,
        (SELECT sum(poi.line_total) FROM purchase_order_items poi WHERE poi.purchase_order_id = po.id) AS total,
        (SELECT min(gr.receipt_date) FROM goods_receipts gr
          WHERE gr.purchase_order_id = po.id AND gr.status = 'posted') AS first_receipt_date
      FROM purchase_orders po
     WHERE po.status NOT IN ('draft', 'cancelled')
),
order_agg AS (
    SELECT
        o.supplier_id,
        count(*)                                                        AS orders,
        COALESCE(sum(o.total), 0)::numeric(15,2)                        AS total_spend,
        count(o.first_receipt_date)                                     AS delivered_orders,
        count(*) FILTER (WHERE o.first_receipt_date <= o.on_time_limit) AS on_time_orders,
        avg(o.first_receipt_date - o.expected_delivery_date)
            FILTER (WHERE o.first_receipt_date > o.on_time_limit)       AS avg_days_late
      FROM orders o
     GROUP BY o.supplier_id
),
invoice_agg AS (
    SELECT
        m.supplier_id,
        count(*)                                                        AS invoices,
        count(*) FILTER (WHERE m.has_exception OR dup.duplicate_invoice IS NOT NULL) AS exception_invoices,
        avg(m.days_to_resolve)                                          AS avg_days_to_resolve,
        count(*) FILTER (WHERE (m.has_exception AND m.resolution = 'open')
                            OR dup.resolution = 'open')                 AS open_exceptions
      FROM vw_invoice_match m
      LEFT JOIN vw_duplicate_invoice_candidates dup ON dup.duplicate_invoice = m.invoice_number
     GROUP BY m.supplier_id
)
SELECT
    s.code                                                              AS supplier_code,
    s.name                                                              AS supplier_name,
    COALESCE(oa.orders, 0)                                              AS orders,
    COALESCE(oa.total_spend, 0)::numeric(15,2)                          AS total_spend,
    round(100.0 * oa.on_time_orders / NULLIF(oa.delivered_orders, 0), 1) AS on_time_delivery_pct,
    round(oa.avg_days_late, 1)                                          AS avg_days_late,
    COALESCE(ia.invoices, 0)                                            AS invoices,
    round(100.0 * ia.exception_invoices / NULLIF(ia.invoices, 0), 1)    AS exception_rate_pct,
    round(ia.avg_days_to_resolve, 1)                                    AS avg_days_to_resolve,
    COALESCE(ia.open_exceptions, 0)                                     AS open_exceptions
FROM suppliers s
LEFT JOIN order_agg   oa ON oa.supplier_id = s.id
LEFT JOIN invoice_agg ia ON ia.supplier_id = s.id
ORDER BY total_spend DESC, s.code;

COMMENT ON VIEW vw_supplier_scorecard IS 'Supplier scorecard: spend, delivery punctuality and invoice exceptions. Orders in draft or cancelled status are not counted. Ordered by total spend.';
COMMENT ON COLUMN vw_supplier_scorecard.orders               IS 'Purchase orders not in draft or cancelled status.';
COMMENT ON COLUMN vw_supplier_scorecard.total_spend          IS 'Sum of the order line totals of those orders.';
COMMENT ON COLUMN vw_supplier_scorecard.on_time_delivery_pct IS 'Delivered orders whose first posted receipt came by expected_delivery_date + 1 business day, over delivered orders. Null when nothing was delivered.';
COMMENT ON COLUMN vw_supplier_scorecard.avg_days_late        IS 'Average calendar days between expected_delivery_date and the first posted receipt, over the late deliveries only.';
COMMENT ON COLUMN vw_supplier_scorecard.invoices             IS 'All invoices of the supplier, including cancelled ones.';
COMMENT ON COLUMN vw_supplier_scorecard.exception_rate_pct   IS 'Invoices with a match exception (vw_invoice_match) or flagged as duplicate, over all invoices.';
COMMENT ON COLUMN vw_supplier_scorecard.avg_days_to_resolve  IS 'Average days from posting to approval of invoices whose exception was released.';
COMMENT ON COLUMN vw_supplier_scorecard.open_exceptions      IS 'Invoices with an exception still open (match exception or blocked duplicate).';

-- ---------------------------------------------------------------------------
-- vw_spend_by_month_category
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_spend_by_month_category AS
SELECT
    date_trunc('month', po.order_date)::date    AS order_month,
    mc.code                                     AS category_code,
    mc.name                                     AS category_name,
    count(DISTINCT po.id)                       AS orders,
    count(*)                                    AS items,
    sum(poi.line_total)::numeric(15,2)          AS total_value,
    count(DISTINCT po.supplier_id)              AS suppliers
FROM purchase_order_items poi
JOIN purchase_orders      po ON po.id = poi.purchase_order_id
JOIN materials            m  ON m.id  = poi.material_id
JOIN material_categories  mc ON mc.id = m.material_category_id
WHERE po.status NOT IN ('draft', 'cancelled')
GROUP BY date_trunc('month', po.order_date)::date, mc.code, mc.name
ORDER BY order_month, mc.code;

COMMENT ON VIEW vw_spend_by_month_category IS 'Purchase order spend by order month and material category. Orders in draft or cancelled status are not counted.';
COMMENT ON COLUMN vw_spend_by_month_category.order_month IS 'First day of the order month.';
COMMENT ON COLUMN vw_spend_by_month_category.orders      IS 'Distinct purchase orders with at least one item of the category.';
COMMENT ON COLUMN vw_spend_by_month_category.items       IS 'Order lines of the category.';
COMMENT ON COLUMN vw_spend_by_month_category.total_value IS 'Sum of the line totals.';
COMMENT ON COLUMN vw_spend_by_month_category.suppliers   IS 'Distinct suppliers with orders in the month and category.';