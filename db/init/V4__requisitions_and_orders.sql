-- V4__requisitions_and_orders.sql
-- Metalcor Procurement v0.1, block 3a: purchase requisitions and purchase orders.
-- Requires V1 (master data, set_updated_at()) and V3 (next_document_number()).
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- purchase_requisitions
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_requisitions (
    id                bigint GENERATED ALWAYS AS IDENTITY,
    document_number   varchar(20) NOT NULL DEFAULT next_document_number('purchase_requisition'),
    plant_id          bigint      NOT NULL,
    cost_center_id    bigint      NOT NULL,
    requested_by      bigint      NOT NULL,
    needed_by         date        NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'draft',
    approved_by       bigint,
    approved_at       timestamptz,
    rejection_reason  text,
    notes             text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_requisitions PRIMARY KEY (id),
    CONSTRAINT uq_purchase_requisitions_document_number UNIQUE (document_number),
    CONSTRAINT fk_purchase_requisitions_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_purchase_requisitions_cost_center FOREIGN KEY (cost_center_id) REFERENCES cost_centers (id),
    CONSTRAINT fk_purchase_requisitions_requested_by FOREIGN KEY (requested_by) REFERENCES app_users (id),
    CONSTRAINT fk_purchase_requisitions_approved_by FOREIGN KEY (approved_by) REFERENCES app_users (id),
    CONSTRAINT ck_purchase_requisitions_status CHECK (status IN ('draft', 'pending_approval', 'approved', 'rejected', 'cancelled')),
    CONSTRAINT ck_purchase_requisitions_approved CHECK (
        status <> 'approved' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    )
);

CREATE INDEX idx_purchase_requisitions_plant_id       ON purchase_requisitions (plant_id);
CREATE INDEX idx_purchase_requisitions_cost_center_id ON purchase_requisitions (cost_center_id);
CREATE INDEX idx_purchase_requisitions_requested_by   ON purchase_requisitions (requested_by);
CREATE INDEX idx_purchase_requisitions_approved_by    ON purchase_requisitions (approved_by);
CREATE INDEX idx_purchase_requisitions_status         ON purchase_requisitions (status);
CREATE INDEX idx_purchase_requisitions_needed_by      ON purchase_requisitions (needed_by);

CREATE TRIGGER trg_purchase_requisitions_set_updated_at
    BEFORE UPDATE ON purchase_requisitions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  purchase_requisitions                  IS 'Purchase requisition header: an internal request to buy materials, subject to approval.';
COMMENT ON COLUMN purchase_requisitions.document_number  IS 'Unique document number, issued by next_document_number() (e.g. PR-2026-000001).';
COMMENT ON COLUMN purchase_requisitions.plant_id         IS 'Plant that needs the materials.';
COMMENT ON COLUMN purchase_requisitions.cost_center_id   IS 'Cost center charged.';
COMMENT ON COLUMN purchase_requisitions.requested_by     IS 'User who created the requisition.';
COMMENT ON COLUMN purchase_requisitions.needed_by        IS 'Date the materials are needed.';
COMMENT ON COLUMN purchase_requisitions.status           IS 'draft, pending_approval, approved, rejected or cancelled.';
COMMENT ON COLUMN purchase_requisitions.approved_by      IS 'User who approved. Required when status is approved.';
COMMENT ON COLUMN purchase_requisitions.approved_at      IS 'Approval timestamp. Required when status is approved.';
COMMENT ON COLUMN purchase_requisitions.rejection_reason IS 'Reason given when the requisition is rejected.';
COMMENT ON COLUMN purchase_requisitions.notes            IS 'Free-text notes.';

-- ---------------------------------------------------------------------------
-- purchase_requisition_items
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_requisition_items (
    id                        bigint GENERATED ALWAYS AS IDENTITY,
    purchase_requisition_id   bigint        NOT NULL,
    line_number               integer       NOT NULL,
    material_id               bigint        NOT NULL,
    quantity                  numeric(15,3) NOT NULL,
    unit_of_measure_id        bigint        NOT NULL,
    estimated_unit_price      numeric(15,4) NOT NULL,
    estimated_total           numeric(15,2) GENERATED ALWAYS AS (round(quantity * estimated_unit_price, 2)) STORED,
    suggested_supplier_id     bigint,
    needed_by                 date,
    created_at                timestamptz   NOT NULL DEFAULT now(),
    updated_at                timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_requisition_items PRIMARY KEY (id),
    CONSTRAINT uq_purchase_requisition_items_line UNIQUE (purchase_requisition_id, line_number),
    CONSTRAINT fk_purchase_requisition_items_purchase_requisition FOREIGN KEY (purchase_requisition_id) REFERENCES purchase_requisitions (id),
    CONSTRAINT fk_purchase_requisition_items_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_purchase_requisition_items_unit_of_measure FOREIGN KEY (unit_of_measure_id) REFERENCES units_of_measure (id),
    CONSTRAINT fk_purchase_requisition_items_suggested_supplier FOREIGN KEY (suggested_supplier_id) REFERENCES suppliers (id),
    CONSTRAINT ck_purchase_requisition_items_line_number CHECK (line_number > 0),
    CONSTRAINT ck_purchase_requisition_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_requisition_items_estimated_unit_price CHECK (estimated_unit_price >= 0)
);

-- No index on purchase_requisition_id: uq_purchase_requisition_items_line already leads with it.
CREATE INDEX idx_purchase_requisition_items_material_id           ON purchase_requisition_items (material_id);
CREATE INDEX idx_purchase_requisition_items_unit_of_measure_id    ON purchase_requisition_items (unit_of_measure_id);
CREATE INDEX idx_purchase_requisition_items_suggested_supplier_id ON purchase_requisition_items (suggested_supplier_id);
CREATE INDEX idx_purchase_requisition_items_needed_by             ON purchase_requisition_items (needed_by);

CREATE TRIGGER trg_purchase_requisition_items_set_updated_at
    BEFORE UPDATE ON purchase_requisition_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  purchase_requisition_items                         IS 'Purchase requisition line items.';
COMMENT ON COLUMN purchase_requisition_items.purchase_requisition_id IS 'Requisition the line belongs to.';
COMMENT ON COLUMN purchase_requisition_items.line_number             IS 'Line position within the requisition, starting at 1.';
COMMENT ON COLUMN purchase_requisition_items.material_id             IS 'Material requested.';
COMMENT ON COLUMN purchase_requisition_items.quantity                IS 'Quantity requested, in unit_of_measure_id.';
COMMENT ON COLUMN purchase_requisition_items.unit_of_measure_id      IS 'Unit of measure of the quantity.';
COMMENT ON COLUMN purchase_requisition_items.estimated_unit_price    IS 'Estimated price per unit.';
COMMENT ON COLUMN purchase_requisition_items.estimated_total         IS 'Generated: round(quantity * estimated_unit_price, 2).';
COMMENT ON COLUMN purchase_requisition_items.suggested_supplier_id   IS 'Supplier suggested by the requester, if any.';
COMMENT ON COLUMN purchase_requisition_items.needed_by               IS 'Line-level need date, if different from the header.';

-- ---------------------------------------------------------------------------
-- purchase_orders
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_orders (
    id                        bigint GENERATED ALWAYS AS IDENTITY,
    document_number           varchar(20) NOT NULL DEFAULT next_document_number('purchase_order'),
    supplier_id               bigint      NOT NULL,
    purchase_requisition_id   bigint,
    plant_id                  bigint      NOT NULL,
    buyer_id                  bigint      NOT NULL,
    order_date                date        NOT NULL DEFAULT current_date,
    expected_delivery_date    date        NOT NULL,
    payment_terms_days        integer     NOT NULL,
    currency                  char(3)     NOT NULL DEFAULT 'BRL',
    status                    varchar(20) NOT NULL DEFAULT 'draft',
    notes                     text,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),
    CONSTRAINT uq_purchase_orders_document_number UNIQUE (document_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_orders_purchase_requisition FOREIGN KEY (purchase_requisition_id) REFERENCES purchase_requisitions (id),
    CONSTRAINT fk_purchase_orders_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT fk_purchase_orders_buyer FOREIGN KEY (buyer_id) REFERENCES app_users (id),
    CONSTRAINT ck_purchase_orders_payment_terms_days CHECK (payment_terms_days >= 0),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('draft', 'issued', 'partially_received', 'received', 'closed', 'cancelled')),
    CONSTRAINT ck_purchase_orders_expected_delivery_date CHECK (expected_delivery_date >= order_date)
);

CREATE INDEX idx_purchase_orders_supplier_id             ON purchase_orders (supplier_id);
CREATE INDEX idx_purchase_orders_purchase_requisition_id ON purchase_orders (purchase_requisition_id);
CREATE INDEX idx_purchase_orders_plant_id                ON purchase_orders (plant_id);
CREATE INDEX idx_purchase_orders_buyer_id                ON purchase_orders (buyer_id);
CREATE INDEX idx_purchase_orders_status                  ON purchase_orders (status);
CREATE INDEX idx_purchase_orders_order_date              ON purchase_orders (order_date);
CREATE INDEX idx_purchase_orders_expected_delivery_date  ON purchase_orders (expected_delivery_date);

CREATE TRIGGER trg_purchase_orders_set_updated_at
    BEFORE UPDATE ON purchase_orders
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  purchase_orders                         IS 'Purchase order header: a commitment to buy from a supplier.';
COMMENT ON COLUMN purchase_orders.document_number         IS 'Unique document number, issued by next_document_number() (e.g. PO-2026-000001).';
COMMENT ON COLUMN purchase_orders.supplier_id             IS 'Supplier the order is placed with.';
COMMENT ON COLUMN purchase_orders.purchase_requisition_id IS 'Source requisition. Null for orders created without one.';
COMMENT ON COLUMN purchase_orders.plant_id                IS 'Plant that receives the goods.';
COMMENT ON COLUMN purchase_orders.buyer_id                IS 'User (buyer) who placed the order.';
COMMENT ON COLUMN purchase_orders.order_date              IS 'Date the order was placed.';
COMMENT ON COLUMN purchase_orders.expected_delivery_date  IS 'Date the supplier is expected to deliver. Not before order_date.';
COMMENT ON COLUMN purchase_orders.payment_terms_days      IS 'Payment term in days, agreed for this order.';
COMMENT ON COLUMN purchase_orders.currency                IS 'ISO 4217 currency code. Default BRL.';
COMMENT ON COLUMN purchase_orders.status                  IS 'draft, issued, partially_received, received, closed or cancelled.';
COMMENT ON COLUMN purchase_orders.notes                   IS 'Free-text notes.';

-- ---------------------------------------------------------------------------
-- purchase_order_items
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_order_items (
    id                      bigint GENERATED ALWAYS AS IDENTITY,
    purchase_order_id       bigint        NOT NULL,
    line_number             integer       NOT NULL,
    material_id             bigint        NOT NULL,
    requisition_item_id     bigint,
    quantity                numeric(15,3) NOT NULL,
    unit_of_measure_id      bigint        NOT NULL,
    unit_price              numeric(15,4) NOT NULL,
    line_total              numeric(15,2) GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED,
    delivery_date           date,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (id),
    CONSTRAINT uq_purchase_order_items_line UNIQUE (purchase_order_id, line_number),
    CONSTRAINT fk_purchase_order_items_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_items_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT fk_purchase_order_items_requisition_item FOREIGN KEY (requisition_item_id) REFERENCES purchase_requisition_items (id),
    CONSTRAINT fk_purchase_order_items_unit_of_measure FOREIGN KEY (unit_of_measure_id) REFERENCES units_of_measure (id),
    CONSTRAINT ck_purchase_order_items_line_number CHECK (line_number > 0),
    CONSTRAINT ck_purchase_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_order_items_unit_price CHECK (unit_price >= 0)
);

-- No index on purchase_order_id: uq_purchase_order_items_line already leads with it.
CREATE INDEX idx_purchase_order_items_material_id         ON purchase_order_items (material_id);
CREATE INDEX idx_purchase_order_items_requisition_item_id ON purchase_order_items (requisition_item_id);
CREATE INDEX idx_purchase_order_items_unit_of_measure_id  ON purchase_order_items (unit_of_measure_id);
CREATE INDEX idx_purchase_order_items_delivery_date       ON purchase_order_items (delivery_date);

CREATE TRIGGER trg_purchase_order_items_set_updated_at
    BEFORE UPDATE ON purchase_order_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  purchase_order_items                     IS 'Purchase order line items.';
COMMENT ON COLUMN purchase_order_items.purchase_order_id   IS 'Order the line belongs to.';
COMMENT ON COLUMN purchase_order_items.line_number         IS 'Line position within the order, starting at 1.';
COMMENT ON COLUMN purchase_order_items.material_id         IS 'Material ordered.';
COMMENT ON COLUMN purchase_order_items.requisition_item_id IS 'Requisition line this line originates from (traceability). Null if none.';
COMMENT ON COLUMN purchase_order_items.quantity            IS 'Quantity ordered, in unit_of_measure_id.';
COMMENT ON COLUMN purchase_order_items.unit_of_measure_id  IS 'Unit of measure of the quantity.';
COMMENT ON COLUMN purchase_order_items.unit_price          IS 'Agreed price per unit.';
COMMENT ON COLUMN purchase_order_items.line_total          IS 'Generated: round(quantity * unit_price, 2).';
COMMENT ON COLUMN purchase_order_items.delivery_date       IS 'Line-level delivery date, if different from the header.';