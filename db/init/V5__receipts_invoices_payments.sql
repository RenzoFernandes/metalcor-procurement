-- V5__receipts_invoices_payments.sql
-- Metalcor Procurement v0.1, block 3b: goods receipts, invoice receipts and payments.
-- Requires V1 (master data, set_updated_at()), V3 (next_document_number()) and V4 (purchase orders).
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- goods_receipts
-- ---------------------------------------------------------------------------
CREATE TABLE goods_receipts (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    document_number        varchar(20) NOT NULL DEFAULT next_document_number('goods_receipt'),
    purchase_order_id      bigint      NOT NULL,
    plant_id               bigint      NOT NULL,
    received_by            bigint      NOT NULL,
    receipt_date           date        NOT NULL DEFAULT current_date,
    delivery_note_number   varchar(30),
    status                 varchar(20) NOT NULL DEFAULT 'posted',
    reversed_by            bigint,
    reversed_at            timestamptz,
    reversal_reason        text,
    notes                  text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_goods_receipts PRIMARY KEY (id),
    CONSTRAINT uq_goods_receipts_document_number UNIQUE (document_number),
    CONSTRAINT fk_goods_receipts_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_goods_receipts_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_goods_receipts_received_by FOREIGN KEY (received_by) REFERENCES app_users (id),
    CONSTRAINT fk_goods_receipts_reversed_by FOREIGN KEY (reversed_by) REFERENCES app_users (id),
    CONSTRAINT ck_goods_receipts_status CHECK (status IN ('posted', 'reversed')),
    CONSTRAINT ck_goods_receipts_reversed CHECK (
        status <> 'reversed'
        OR (reversed_by IS NOT NULL AND reversed_at IS NOT NULL AND reversal_reason IS NOT NULL)
    )
);

CREATE INDEX idx_goods_receipts_purchase_order_id ON goods_receipts (purchase_order_id);
CREATE INDEX idx_goods_receipts_plant_id          ON goods_receipts (plant_id);
CREATE INDEX idx_goods_receipts_received_by       ON goods_receipts (received_by);
CREATE INDEX idx_goods_receipts_reversed_by       ON goods_receipts (reversed_by);
CREATE INDEX idx_goods_receipts_status            ON goods_receipts (status);
CREATE INDEX idx_goods_receipts_receipt_date      ON goods_receipts (receipt_date);

CREATE TRIGGER trg_goods_receipts_set_updated_at
    BEFORE UPDATE ON goods_receipts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  goods_receipts                      IS 'Goods receipt header: physical receipt of materials against a purchase order. A purchase order can have several receipts (partial deliveries).';
COMMENT ON COLUMN goods_receipts.document_number      IS 'Unique document number, issued by next_document_number() (e.g. GR-2026-000001).';
COMMENT ON COLUMN goods_receipts.purchase_order_id    IS 'Purchase order being received.';
COMMENT ON COLUMN goods_receipts.plant_id             IS 'Plant where the goods were received.';
COMMENT ON COLUMN goods_receipts.received_by          IS 'User who posted the receipt.';
COMMENT ON COLUMN goods_receipts.receipt_date         IS 'Date the goods arrived.';
COMMENT ON COLUMN goods_receipts.delivery_note_number IS 'Supplier delivery note number, if any.';
COMMENT ON COLUMN goods_receipts.status               IS 'posted or reversed.';
COMMENT ON COLUMN goods_receipts.reversed_by          IS 'User who reversed the receipt. Required when status is reversed.';
COMMENT ON COLUMN goods_receipts.reversed_at          IS 'Reversal timestamp. Required when status is reversed.';
COMMENT ON COLUMN goods_receipts.reversal_reason      IS 'Reason for the reversal. Required when status is reversed.';
COMMENT ON COLUMN goods_receipts.notes                IS 'Free-text notes.';

-- ---------------------------------------------------------------------------
-- goods_receipt_items
-- ---------------------------------------------------------------------------
CREATE TABLE goods_receipt_items (
    id                       bigint GENERATED ALWAYS AS IDENTITY,
    goods_receipt_id         bigint        NOT NULL,
    line_number              integer       NOT NULL,
    purchase_order_item_id   bigint        NOT NULL,
    quantity_received        numeric(15,3) NOT NULL,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_goods_receipt_items PRIMARY KEY (id),
    CONSTRAINT uq_goods_receipt_items_line UNIQUE (goods_receipt_id, line_number),
    CONSTRAINT uq_goods_receipt_items_purchase_order_item UNIQUE (goods_receipt_id, purchase_order_item_id),
    CONSTRAINT fk_goods_receipt_items_goods_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts (id),
    CONSTRAINT fk_goods_receipt_items_purchase_order_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT ck_goods_receipt_items_line_number CHECK (line_number > 0),
    CONSTRAINT ck_goods_receipt_items_quantity_received CHECK (quantity_received > 0)
);

-- No index on goods_receipt_id: the unique constraints already lead with it.
CREATE INDEX idx_goods_receipt_items_purchase_order_item_id ON goods_receipt_items (purchase_order_item_id);

CREATE TRIGGER trg_goods_receipt_items_set_updated_at
    BEFORE UPDATE ON goods_receipt_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  goods_receipt_items                        IS 'Goods receipt line items, each pointing to a purchase order line. Cumulative received quantity against the order and the three-way match are checked by views and the API, not by constraints.';
COMMENT ON COLUMN goods_receipt_items.goods_receipt_id       IS 'Receipt the line belongs to.';
COMMENT ON COLUMN goods_receipt_items.line_number            IS 'Line position within the receipt, starting at 1.';
COMMENT ON COLUMN goods_receipt_items.purchase_order_item_id IS 'Purchase order line being received. One receipt line per order line.';
COMMENT ON COLUMN goods_receipt_items.quantity_received      IS 'Quantity received, in the order line unit of measure.';

-- ---------------------------------------------------------------------------
-- invoice_receipts
-- ---------------------------------------------------------------------------
CREATE TABLE invoice_receipts (
    id                       bigint GENERATED ALWAYS AS IDENTITY,
    document_number          varchar(20)   NOT NULL DEFAULT next_document_number('invoice_receipt'),
    supplier_id              bigint        NOT NULL,
    purchase_order_id        bigint        NOT NULL,
    supplier_invoice_number  varchar(30)   NOT NULL,
    invoice_date             date          NOT NULL,
    due_date                 date          NOT NULL,
    posting_date             date          NOT NULL DEFAULT current_date,
    currency                 char(3)       NOT NULL DEFAULT 'BRL',
    gross_amount             numeric(15,2) NOT NULL,
    status                   varchar(20)   NOT NULL DEFAULT 'received',
    block_reason             text,
    approved_by              bigint,
    approved_at              timestamptz,
    notes                    text,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_invoice_receipts PRIMARY KEY (id),
    CONSTRAINT uq_invoice_receipts_document_number UNIQUE (document_number),
    CONSTRAINT uq_invoice_receipts_supplier_invoice_number UNIQUE (supplier_id, supplier_invoice_number),
    CONSTRAINT fk_invoice_receipts_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_invoice_receipts_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_invoice_receipts_approved_by FOREIGN KEY (approved_by) REFERENCES app_users (id),
    CONSTRAINT ck_invoice_receipts_gross_amount CHECK (gross_amount >= 0),
    CONSTRAINT ck_invoice_receipts_status CHECK (status IN ('received', 'matched', 'blocked', 'approved', 'paid', 'cancelled')),
    CONSTRAINT ck_invoice_receipts_due_date CHECK (due_date >= invoice_date),
    CONSTRAINT ck_invoice_receipts_blocked CHECK (status <> 'blocked' OR block_reason IS NOT NULL),
    CONSTRAINT ck_invoice_receipts_approved CHECK (
        status NOT IN ('approved', 'paid') OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    )
);

-- No index on supplier_id: uq_invoice_receipts_supplier_invoice_number already leads with it.
CREATE INDEX idx_invoice_receipts_purchase_order_id ON invoice_receipts (purchase_order_id);
CREATE INDEX idx_invoice_receipts_approved_by       ON invoice_receipts (approved_by);
CREATE INDEX idx_invoice_receipts_status            ON invoice_receipts (status);
CREATE INDEX idx_invoice_receipts_invoice_date      ON invoice_receipts (invoice_date);
CREATE INDEX idx_invoice_receipts_due_date          ON invoice_receipts (due_date);
CREATE INDEX idx_invoice_receipts_posting_date      ON invoice_receipts (posting_date);

CREATE TRIGGER trg_invoice_receipts_set_updated_at
    BEFORE UPDATE ON invoice_receipts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  invoice_receipts                         IS 'Supplier invoice header. Model rule: one invoice per purchase order (enforced by the application, not by a constraint). Cumulative invoiced quantity against the order and the three-way match are checked by views and the API, not by constraints.';
COMMENT ON COLUMN invoice_receipts.document_number         IS 'Unique internal document number, issued by next_document_number() (e.g. IR-2026-000001).';
COMMENT ON COLUMN invoice_receipts.supplier_id             IS 'Supplier that issued the invoice.';
COMMENT ON COLUMN invoice_receipts.purchase_order_id       IS 'Purchase order the invoice refers to. One invoice per purchase order.';
COMMENT ON COLUMN invoice_receipts.supplier_invoice_number IS 'Invoice number as printed by the supplier. Unique per supplier, which blocks duplicate entry.';
COMMENT ON COLUMN invoice_receipts.invoice_date            IS 'Date on the supplier invoice.';
COMMENT ON COLUMN invoice_receipts.due_date                IS 'Payment due date. Not before invoice_date.';
COMMENT ON COLUMN invoice_receipts.posting_date            IS 'Date the invoice was entered in the system.';
COMMENT ON COLUMN invoice_receipts.currency                IS 'ISO 4217 currency code. Default BRL.';
COMMENT ON COLUMN invoice_receipts.gross_amount            IS 'Total invoice amount.';
COMMENT ON COLUMN invoice_receipts.status                  IS 'received, matched, blocked, approved, paid or cancelled.';
COMMENT ON COLUMN invoice_receipts.block_reason            IS 'Why the invoice is blocked (e.g. match difference). Required when status is blocked.';
COMMENT ON COLUMN invoice_receipts.approved_by             IS 'User who approved the invoice. Required when status is approved or paid.';
COMMENT ON COLUMN invoice_receipts.approved_at             IS 'Approval timestamp. Required when status is approved or paid.';
COMMENT ON COLUMN invoice_receipts.notes                   IS 'Free-text notes.';

-- ---------------------------------------------------------------------------
-- invoice_receipt_items
-- ---------------------------------------------------------------------------
CREATE TABLE invoice_receipt_items (
    id                       bigint GENERATED ALWAYS AS IDENTITY,
    invoice_receipt_id       bigint        NOT NULL,
    line_number              integer       NOT NULL,
    purchase_order_item_id   bigint        NOT NULL,
    quantity_invoiced        numeric(15,3) NOT NULL,
    unit_price               numeric(15,4) NOT NULL,
    line_total               numeric(15,2) GENERATED ALWAYS AS (round(quantity_invoiced * unit_price, 2)) STORED,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_invoice_receipt_items PRIMARY KEY (id),
    CONSTRAINT uq_invoice_receipt_items_line UNIQUE (invoice_receipt_id, line_number),
    CONSTRAINT uq_invoice_receipt_items_purchase_order_item UNIQUE (invoice_receipt_id, purchase_order_item_id),
    CONSTRAINT fk_invoice_receipt_items_invoice_receipt FOREIGN KEY (invoice_receipt_id) REFERENCES invoice_receipts (id),
    CONSTRAINT fk_invoice_receipt_items_purchase_order_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT ck_invoice_receipt_items_line_number CHECK (line_number > 0),
    CONSTRAINT ck_invoice_receipt_items_quantity_invoiced CHECK (quantity_invoiced > 0),
    CONSTRAINT ck_invoice_receipt_items_unit_price CHECK (unit_price >= 0)
);

-- No index on invoice_receipt_id: the unique constraints already lead with it.
CREATE INDEX idx_invoice_receipt_items_purchase_order_item_id ON invoice_receipt_items (purchase_order_item_id);

CREATE TRIGGER trg_invoice_receipt_items_set_updated_at
    BEFORE UPDATE ON invoice_receipt_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  invoice_receipt_items                        IS 'Supplier invoice line items, each pointing to a purchase order line. Cumulative invoiced quantity against the order and the three-way match are checked by views and the API, not by constraints.';
COMMENT ON COLUMN invoice_receipt_items.invoice_receipt_id     IS 'Invoice the line belongs to.';
COMMENT ON COLUMN invoice_receipt_items.line_number            IS 'Line position within the invoice, starting at 1.';
COMMENT ON COLUMN invoice_receipt_items.purchase_order_item_id IS 'Purchase order line being invoiced. One invoice line per order line.';
COMMENT ON COLUMN invoice_receipt_items.quantity_invoiced      IS 'Quantity billed by the supplier.';
COMMENT ON COLUMN invoice_receipt_items.unit_price             IS 'Unit price billed by the supplier.';
COMMENT ON COLUMN invoice_receipt_items.line_total             IS 'Generated: round(quantity_invoiced * unit_price, 2).';

-- ---------------------------------------------------------------------------
-- payments
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id                   bigint GENERATED ALWAYS AS IDENTITY,
    document_number      varchar(20)   NOT NULL DEFAULT next_document_number('payment'),
    invoice_receipt_id   bigint        NOT NULL,
    amount               numeric(15,2) NOT NULL,
    scheduled_for        date          NOT NULL,
    payment_method       varchar(20)   NOT NULL,
    status               varchar(20)   NOT NULL DEFAULT 'scheduled',
    paid_at              timestamptz,
    created_by           bigint        NOT NULL,
    reference            varchar(60),
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uq_payments_document_number UNIQUE (document_number),
    CONSTRAINT fk_payments_invoice_receipt FOREIGN KEY (invoice_receipt_id) REFERENCES invoice_receipts (id),
    CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by) REFERENCES app_users (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_payment_method CHECK (payment_method IN ('bank_transfer', 'boleto', 'pix')),
    CONSTRAINT ck_payments_status CHECK (status IN ('scheduled', 'paid', 'cancelled')),
    CONSTRAINT ck_payments_paid CHECK (status <> 'paid' OR paid_at IS NOT NULL)
);

CREATE INDEX idx_payments_invoice_receipt_id ON payments (invoice_receipt_id);

-- Only one non-cancelled payment per invoice; cancelled ones stay as history.
CREATE UNIQUE INDEX uq_payments_active_invoice
    ON payments (invoice_receipt_id)
    WHERE status <> 'cancelled';
CREATE INDEX idx_payments_created_by         ON payments (created_by);
CREATE INDEX idx_payments_status             ON payments (status);
CREATE INDEX idx_payments_scheduled_for      ON payments (scheduled_for);
CREATE INDEX idx_payments_paid_at            ON payments (paid_at);

CREATE TRIGGER trg_payments_set_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  payments                    IS 'Payments of supplier invoices (accounts payable settlement).';
COMMENT ON COLUMN payments.document_number    IS 'Unique document number, issued by next_document_number() (e.g. PY-2026-000001).';
COMMENT ON COLUMN payments.invoice_receipt_id IS 'Invoice being paid.';
COMMENT ON COLUMN payments.amount             IS 'Amount paid or scheduled.';
COMMENT ON COLUMN payments.scheduled_for      IS 'Planned payment date.';
COMMENT ON COLUMN payments.payment_method     IS 'bank_transfer, boleto or pix.';
COMMENT ON COLUMN payments.status             IS 'scheduled, paid or cancelled.';
COMMENT ON COLUMN payments.paid_at            IS 'Actual payment timestamp. Required when status is paid.';
COMMENT ON COLUMN payments.created_by         IS 'User who created the payment.';
COMMENT ON COLUMN payments.reference          IS 'External reference (bank authentication code, boleto line, Pix id).';