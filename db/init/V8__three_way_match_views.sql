-- V8__three_way_match_views.sql
-- Metalcor Procurement v0.2a: three-way match views (purchase order x goods receipt x invoice).
-- Requires V1 (master data), V2 (match_tolerances), V4 (orders) and V5 (receipts, invoices).
-- Fictional data only. Not SAP.
--
-- Views only: no table or trigger is touched.
--
-- Indexes: none added. The lookups these views need are already covered by existing indexes:
--   * idx_goods_receipt_items_purchase_order_item_id  -> received quantity per order line
--   * idx_goods_receipts_purchase_order_id            -> latest posted receipt date per order
--   * uq_invoice_receipt_items_line                   -> invoice lines by invoice
--   * uq_match_tolerances_material_category           -> tolerance by category
--   * idx_invoice_receipts_posting_date               -> monthly summary by posting date
-- Adding more would only cost writes without a measured gain at this data volume.
--
-- Scope: the received quantity of an order line is compared with each invoice line on its own;
-- other invoices for the same order line are NOT summed. Duplicate/overbilling detection across
-- invoices is left for v0.2b.

-- ---------------------------------------------------------------------------
-- demo_as_of_date()
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION demo_as_of_date()
RETURNS date
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT DATE '2026-09-18';
$$;

COMMENT ON FUNCTION demo_as_of_date() IS 'Cutoff date of the fictional data set. In production this would be current_date.';

-- ---------------------------------------------------------------------------
-- vw_invoice_line_match: one row per invoice line
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_invoice_line_match AS
SELECT
    ir.id                                   AS invoice_receipt_id,
    ir.document_number                      AS invoice_number,
    ir.supplier_invoice_number              AS supplier_invoice_number,
    ir.supplier_id                          AS supplier_id,
    po.id                                   AS purchase_order_id,
    po.document_number                      AS po_number,
    iri.purchase_order_item_id              AS purchase_order_item_id,
    poi.material_id                         AS material_id,
    m.code                                  AS material_code,
    poi.quantity                            AS quantity_ordered,
    calc.quantity_received                  AS quantity_received,
    iri.quantity_invoiced                   AS quantity_invoiced,
    poi.unit_price                          AS po_unit_price,
    iri.unit_price                          AS invoice_unit_price,
    calc.price_variance_pct                 AS price_variance_pct,
    calc.quantity_variance_pct              AS quantity_variance_pct,
    tol.price_tolerance_pct                 AS price_tolerance_pct,
    tol.quantity_tolerance_pct              AS quantity_tolerance_pct,
    COALESCE(abs(calc.price_variance_pct) > tol.price_tolerance_pct, false)
                                            AS price_exception,
    COALESCE(iri.quantity_invoiced > calc.quantity_received * (1 + tol.quantity_tolerance_pct / 100), false)
                                            AS quantity_exception,
    (calc.quantity_received = 0)            AS not_received
FROM invoice_receipt_items iri
JOIN invoice_receipts     ir  ON ir.id  = iri.invoice_receipt_id
JOIN purchase_orders      po  ON po.id  = ir.purchase_order_id
JOIN purchase_order_items poi ON poi.id = iri.purchase_order_item_id
JOIN materials            m   ON m.id   = poi.material_id
LEFT JOIN LATERAL (
    SELECT COALESCE(sum(gri.quantity_received), 0) AS received
      FROM goods_receipt_items gri
      JOIN goods_receipts gr ON gr.id = gri.goods_receipt_id
     WHERE gri.purchase_order_item_id = iri.purchase_order_item_id
       AND gr.status = 'posted'
) rec ON true
LEFT JOIN LATERAL (
    -- Category rule first; the default rule (null category) only when the category has none.
    SELECT mt.price_tolerance_pct, mt.quantity_tolerance_pct
      FROM match_tolerances mt
     WHERE mt.active
       AND (mt.material_category_id = m.material_category_id OR mt.material_category_id IS NULL)
     ORDER BY (mt.material_category_id IS NULL)
     LIMIT 1
) tol ON true
CROSS JOIN LATERAL (
    SELECT
        COALESCE(rec.received, 0)::numeric(15,3) AS quantity_received,
        round((iri.unit_price - poi.unit_price) / NULLIF(poi.unit_price, 0) * 100, 2)::numeric(7,2)
            AS price_variance_pct,
        round((iri.quantity_invoiced - COALESCE(rec.received, 0)) / NULLIF(COALESCE(rec.received, 0), 0) * 100, 2)::numeric(7,2)
            AS quantity_variance_pct
) calc;

COMMENT ON VIEW vw_invoice_line_match IS 'Three-way match per invoice line: order line x posted goods receipts x invoice line. Independent of invoice status. Received quantity is compared with the order line only; other invoices are not summed (duplicates are handled in v0.2b).';
COMMENT ON COLUMN vw_invoice_line_match.invoice_number           IS 'Internal invoice document number (invoice_receipts.document_number).';
COMMENT ON COLUMN vw_invoice_line_match.quantity_received        IS 'Sum of goods receipt lines with status posted for the order line. Zero when nothing was received.';
COMMENT ON COLUMN vw_invoice_line_match.price_variance_pct       IS '(invoice price - order price) / order price * 100, two decimals.';
COMMENT ON COLUMN vw_invoice_line_match.quantity_variance_pct    IS '(quantity invoiced - quantity received) / quantity received * 100, two decimals. Null when nothing was received.';
COMMENT ON COLUMN vw_invoice_line_match.price_tolerance_pct      IS 'Active tolerance of the material category, else the default rule.';
COMMENT ON COLUMN vw_invoice_line_match.quantity_tolerance_pct   IS 'Active tolerance of the material category, else the default rule.';
COMMENT ON COLUMN vw_invoice_line_match.price_exception          IS 'True when the absolute price variance exceeds the price tolerance.';
COMMENT ON COLUMN vw_invoice_line_match.quantity_exception       IS 'True when quantity invoiced exceeds quantity received plus the quantity tolerance.';
COMMENT ON COLUMN vw_invoice_line_match.not_received             IS 'True when no posted receipt exists for the order line.';

-- ---------------------------------------------------------------------------
-- vw_invoice_match: one row per invoice
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_invoice_match AS
WITH line_agg AS (
    SELECT
        l.invoice_receipt_id,
        max(abs(l.price_variance_pct))  AS max_abs_price_variance_pct,
        max(l.quantity_variance_pct)    AS max_quantity_variance_pct,
        bool_or(l.price_exception)      AS price_exception,
        bool_or(l.quantity_exception)   AS quantity_exception
      FROM vw_invoice_line_match l
     GROUP BY l.invoice_receipt_id
),
base AS (
    SELECT
        ir.id                           AS invoice_receipt_id,
        ir.document_number              AS invoice_number,
        ir.supplier_id,
        s.name                          AS supplier_name,
        po.document_number              AS po_number,
        ir.invoice_date,
        ir.posting_date,
        ir.due_date,
        ir.gross_amount,
        ir.status                       AS invoice_status,
        la.max_abs_price_variance_pct,
        la.max_quantity_variance_pct,
        COALESCE(la.price_exception, false)    AS price_exception,
        COALESCE(la.quantity_exception, false) AS quantity_exception,
        -- Invoice dated before the last posted receipt, or the order has no posted receipt at all.
        (lr.last_receipt_date IS NULL OR ir.invoice_date < lr.last_receipt_date) AS invoice_before_receipt,
        u.name                          AS approved_by,
        ir.approved_at,
        ir.block_reason
      FROM invoice_receipts ir
      JOIN suppliers        s  ON s.id  = ir.supplier_id
      JOIN purchase_orders  po ON po.id = ir.purchase_order_id
      LEFT JOIN app_users   u  ON u.id  = ir.approved_by
      LEFT JOIN line_agg    la ON la.invoice_receipt_id = ir.id
      LEFT JOIN LATERAL (
          SELECT max(gr.receipt_date) AS last_receipt_date
            FROM goods_receipts gr
           WHERE gr.purchase_order_id = ir.purchase_order_id
             AND gr.status = 'posted'
      ) lr ON true
),
typed AS (
    SELECT
        b.*,
        array_remove(ARRAY[
            CASE WHEN b.price_exception          THEN 'price_variance'        END,
            CASE WHEN b.quantity_exception       THEN 'quantity_variance'     END,
            CASE WHEN b.invoice_before_receipt   THEN 'invoice_before_receipt' END
        ]::text[], NULL) AS exception_types
      FROM base b
),
resolved AS (
    SELECT
        t.*,
        (cardinality(t.exception_types) > 0) AS has_exception,
        CASE
            WHEN cardinality(t.exception_types) = 0            THEN 'none'
            WHEN t.invoice_status = 'cancelled'                THEN 'cancelled'
            WHEN t.invoice_status IN ('approved', 'paid')      THEN 'released'
            ELSE 'open'  -- received, matched or blocked
        END AS resolution
      FROM typed t
)
SELECT
    r.invoice_number,
    r.supplier_id,
    r.supplier_name,
    r.po_number,
    r.invoice_date,
    r.posting_date,
    r.due_date,
    r.gross_amount,
    r.invoice_status,
    r.max_abs_price_variance_pct,
    r.max_quantity_variance_pct,
    r.price_exception,
    r.quantity_exception,
    r.invoice_before_receipt,
    r.exception_types,
    r.has_exception,
    r.resolution,
    r.approved_by,
    r.approved_at,
    CASE WHEN r.resolution = 'released' THEN r.approved_at::date - r.posting_date END AS days_to_resolve,
    r.block_reason,
    CASE WHEN r.resolution = 'open' THEN demo_as_of_date() - r.posting_date END      AS age_days,
    (r.resolution = 'open' AND demo_as_of_date() - r.posting_date > 45)              AS is_stale
FROM resolved r;

COMMENT ON VIEW vw_invoice_match IS 'Three-way match per invoice, aggregating vw_invoice_line_match, plus the timing check (invoice before receipt) and the resolution of each exception.';
COMMENT ON COLUMN vw_invoice_match.invoice_number           IS 'Internal invoice document number (invoice_receipts.document_number).';
COMMENT ON COLUMN vw_invoice_match.invoice_status           IS 'Workflow status of the invoice: received, matched, blocked, approved, paid or cancelled.';
COMMENT ON COLUMN vw_invoice_match.max_abs_price_variance_pct IS 'Largest absolute price variance among the invoice lines.';
COMMENT ON COLUMN vw_invoice_match.max_quantity_variance_pct  IS 'Largest quantity variance among the invoice lines. Null when no line has received quantity.';
COMMENT ON COLUMN vw_invoice_match.price_exception          IS 'True when any line exceeds the price tolerance.';
COMMENT ON COLUMN vw_invoice_match.quantity_exception       IS 'True when any line exceeds the quantity tolerance (including lines with nothing received).';
COMMENT ON COLUMN vw_invoice_match.invoice_before_receipt   IS 'True when invoice_date is before the latest posted receipt of the order, or the order has no posted receipt.';
COMMENT ON COLUMN vw_invoice_match.exception_types          IS 'Array of price_variance, quantity_variance and invoice_before_receipt. Empty when there is no exception.';
COMMENT ON COLUMN vw_invoice_match.has_exception            IS 'True when exception_types is not empty.';
COMMENT ON COLUMN vw_invoice_match.resolution               IS 'none (no exception), cancelled, open (blocked/received/matched with exception) or released (approved/paid with exception).';
COMMENT ON COLUMN vw_invoice_match.approved_by              IS 'Name of the user who approved the invoice.';
COMMENT ON COLUMN vw_invoice_match.days_to_resolve          IS 'Days between posting_date and approval. Only when resolution is released.';
COMMENT ON COLUMN vw_invoice_match.age_days                 IS 'demo_as_of_date() minus posting_date. Only when resolution is open.';
COMMENT ON COLUMN vw_invoice_match.is_stale                 IS 'True when the exception is still open after more than 45 days.';

-- ---------------------------------------------------------------------------
-- vw_match_exception_summary: exceptions by month, type and resolution
-- ---------------------------------------------------------------------------
CREATE OR REPLACE VIEW vw_match_exception_summary AS
SELECT
    date_trunc('month', m.posting_date)::date AS posting_month,
    t.exception_type,
    m.resolution,
    count(*)            AS invoice_count,
    sum(m.gross_amount) AS gross_amount
FROM vw_invoice_match m
CROSS JOIN LATERAL unnest(m.exception_types) AS t(exception_type)
GROUP BY date_trunc('month', m.posting_date)::date, t.exception_type, m.resolution
ORDER BY posting_month, t.exception_type, m.resolution;

COMMENT ON VIEW vw_match_exception_summary IS 'Exceptions by posting month, exception type and resolution: invoice count and gross amount. An invoice with two exception types appears once per type, so rows must not be summed across types.';
COMMENT ON COLUMN vw_match_exception_summary.posting_month  IS 'First day of the invoice posting month.';
COMMENT ON COLUMN vw_match_exception_summary.exception_type IS 'price_variance, quantity_variance or invoice_before_receipt.';
COMMENT ON COLUMN vw_match_exception_summary.resolution     IS 'open, released or cancelled (invoices without exception are not listed).';
COMMENT ON COLUMN vw_match_exception_summary.invoice_count  IS 'Number of invoices.';
COMMENT ON COLUMN vw_match_exception_summary.gross_amount   IS 'Sum of gross_amount of those invoices.';