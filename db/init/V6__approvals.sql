-- V6__approvals.sql
-- Metalcor Procurement v0.1, block 4a: approvals (immutable decision history).
-- Requires V2 (approval_rules), V4 (requisitions, orders) and V5 (invoice receipts).
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- prevent_modification: trigger function for append-only tables
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION prevent_modification()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$;

COMMENT ON FUNCTION prevent_modification() IS
    'Trigger function for immutable history tables: always raises an exception, so rows can only be inserted, never updated, deleted or truncated. '
    'Attach it as BEFORE UPDATE OR DELETE (row level) and BEFORE TRUNCATE (statement level).';

-- ---------------------------------------------------------------------------
-- approvals
-- ---------------------------------------------------------------------------
CREATE TABLE approvals (
    id                       bigint GENERATED ALWAYS AS IDENTITY,
    purchase_requisition_id  bigint,
    purchase_order_id        bigint,
    invoice_receipt_id       bigint,
    step                     integer       NOT NULL DEFAULT 1,
    required_role            varchar(20)   NOT NULL,
    approval_rule_id         bigint,
    decided_by               bigint        NOT NULL,
    decision                 varchar(20)   NOT NULL,
    comment                  text,
    amount_evaluated         numeric(15,2) NOT NULL,
    decided_at               timestamptz   NOT NULL DEFAULT now(),
    created_at               timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_approvals PRIMARY KEY (id),
    CONSTRAINT fk_approvals_purchase_requisition FOREIGN KEY (purchase_requisition_id) REFERENCES purchase_requisitions (id),
    CONSTRAINT fk_approvals_purchase_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_approvals_invoice_receipt FOREIGN KEY (invoice_receipt_id) REFERENCES invoice_receipts (id),
    CONSTRAINT fk_approvals_approval_rule FOREIGN KEY (approval_rule_id) REFERENCES approval_rules (id),
    CONSTRAINT fk_approvals_decided_by FOREIGN KEY (decided_by) REFERENCES app_users (id),
    CONSTRAINT ck_approvals_one_document CHECK (
        num_nonnulls(purchase_requisition_id, purchase_order_id, invoice_receipt_id) = 1
    ),
    CONSTRAINT ck_approvals_step CHECK (step > 0),
    CONSTRAINT ck_approvals_required_role CHECK (required_role IN ('buyer', 'approver', 'manager')),
    CONSTRAINT ck_approvals_decision CHECK (decision IN ('approved', 'rejected')),
    CONSTRAINT ck_approvals_amount_evaluated CHECK (amount_evaluated >= 0),
    CONSTRAINT ck_approvals_rejection_comment CHECK (
        decision <> 'rejected' OR (comment IS NOT NULL AND btrim(comment) <> '')
    )
);

CREATE INDEX idx_approvals_purchase_requisition_id ON approvals (purchase_requisition_id);
CREATE INDEX idx_approvals_purchase_order_id       ON approvals (purchase_order_id);
CREATE INDEX idx_approvals_invoice_receipt_id      ON approvals (invoice_receipt_id);
CREATE INDEX idx_approvals_approval_rule_id        ON approvals (approval_rule_id);
CREATE INDEX idx_approvals_decided_by              ON approvals (decided_by);
CREATE INDEX idx_approvals_decided_at              ON approvals (decided_at);

-- Append-only: no set_updated_at trigger, no updated_at column.
CREATE TRIGGER trg_approvals_prevent_update_delete
    BEFORE UPDATE OR DELETE ON approvals
    FOR EACH ROW EXECUTE FUNCTION prevent_modification();

CREATE TRIGGER trg_approvals_prevent_truncate
    BEFORE TRUNCATE ON approvals
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_modification();

COMMENT ON TABLE  approvals                         IS 'Immutable history of approval decisions: rows are only inserted, never updated, deleted or truncated. The current status of a requisition is kept on the requisition itself and is updated by the API.';
COMMENT ON COLUMN approvals.purchase_requisition_id IS 'Requisition decided on. Exactly one of the three document columns is set.';
COMMENT ON COLUMN approvals.purchase_order_id       IS 'Purchase order decided on. Exactly one of the three document columns is set.';
COMMENT ON COLUMN approvals.invoice_receipt_id      IS 'Invoice decided on. Exactly one of the three document columns is set.';
COMMENT ON COLUMN approvals.step                    IS 'Position of the decision in the approval sequence, starting at 1.';
COMMENT ON COLUMN approvals.required_role           IS 'Role the rule required for this step: buyer, approver or manager.';
COMMENT ON COLUMN approvals.approval_rule_id        IS 'Approval rule that applied when the decision was taken. Null if none.';
COMMENT ON COLUMN approvals.decided_by              IS 'User who took the decision.';
COMMENT ON COLUMN approvals.decision                IS 'approved or rejected.';
COMMENT ON COLUMN approvals.comment                 IS 'Free-text justification. Required, and not blank, when the decision is rejected.';
COMMENT ON COLUMN approvals.amount_evaluated        IS 'Document amount at the moment of the decision, used to pick the approval rule.';
COMMENT ON COLUMN approvals.decided_at              IS 'When the decision was taken.';