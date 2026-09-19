-- V2__approval_and_tolerance_rules.sql
-- Metalcor Procurement v0.1, block 2: rules.
-- Requires V1__master_data.sql (set_updated_at() and material_categories).
-- Fictional data only. Not SAP.

-- Needed by the exclusion constraint on approval_rules (equality on a scalar column inside GiST).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------------
-- approval_rules
-- ---------------------------------------------------------------------------
CREATE TABLE approval_rules (
    id             bigint GENERATED ALWAYS AS IDENTITY,
    document_type  varchar(30)   NOT NULL,
    min_amount     numeric(15,2) NOT NULL,
    max_amount     numeric(15,2),
    required_role  varchar(20)   NOT NULL,
    active         boolean       NOT NULL DEFAULT true,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_approval_rules PRIMARY KEY (id),
    CONSTRAINT uq_approval_rules_document_type_min_amount UNIQUE (document_type, min_amount),
    CONSTRAINT ck_approval_rules_document_type CHECK (document_type IN ('purchase_requisition', 'purchase_order')),
    CONSTRAINT ck_approval_rules_min_amount CHECK (min_amount >= 0),
    CONSTRAINT ck_approval_rules_max_amount CHECK (max_amount IS NULL OR max_amount > min_amount),
    CONSTRAINT ck_approval_rules_required_role CHECK (required_role IN ('buyer', 'approver', 'manager')),
    -- '[)' = min_amount inclusive, max_amount exclusive; a null max_amount is an unbounded range.
    CONSTRAINT ex_approval_rules_no_overlap EXCLUDE USING gist (
        document_type WITH =,
        numrange(min_amount, max_amount, '[)') WITH &&
    )
);

CREATE TRIGGER trg_approval_rules_set_updated_at
    BEFORE UPDATE ON approval_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  approval_rules               IS 'Approval rules by document value: which role must approve a document in a given amount range.';
COMMENT ON COLUMN approval_rules.document_type IS 'Document the rule applies to: purchase_requisition or purchase_order.';
COMMENT ON COLUMN approval_rules.min_amount    IS 'Lower bound of the range, inclusive.';
COMMENT ON COLUMN approval_rules.max_amount    IS 'Upper bound of the range, exclusive. Null means no limit.';
COMMENT ON COLUMN approval_rules.required_role IS 'Role that must approve documents in the range: buyer, approver or manager.';
COMMENT ON COLUMN approval_rules.active        IS 'False when the rule is disabled.';

-- ---------------------------------------------------------------------------
-- match_tolerances
-- ---------------------------------------------------------------------------
CREATE TABLE match_tolerances (
    id                      bigint GENERATED ALWAYS AS IDENTITY,
    material_category_id    bigint,
    price_tolerance_pct     numeric(5,2) NOT NULL,
    quantity_tolerance_pct  numeric(5,2) NOT NULL,
    active                  boolean      NOT NULL DEFAULT true,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_match_tolerances PRIMARY KEY (id),
    CONSTRAINT uq_match_tolerances_material_category UNIQUE (material_category_id),
    CONSTRAINT fk_match_tolerances_material_category FOREIGN KEY (material_category_id) REFERENCES material_categories (id),
    CONSTRAINT ck_match_tolerances_price_tolerance_pct CHECK (price_tolerance_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_match_tolerances_quantity_tolerance_pct CHECK (quantity_tolerance_pct BETWEEN 0 AND 100)
);

-- No separate index on material_category_id: uq_match_tolerances_material_category already indexes it.

-- A plain UNIQUE treats NULLs as distinct, so this partial index allows only one default row.
CREATE UNIQUE INDEX uq_match_tolerances_default
    ON match_tolerances ((true))
    WHERE material_category_id IS NULL;

CREATE TRIGGER trg_match_tolerances_set_updated_at
    BEFORE UPDATE ON match_tolerances
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  match_tolerances                        IS 'Three-way match tolerances (price and quantity) by material category, with one default row.';
COMMENT ON COLUMN match_tolerances.material_category_id    IS 'Category the tolerance applies to. Null marks the default rule, used when the category has none.';
COMMENT ON COLUMN match_tolerances.price_tolerance_pct    IS 'Accepted price deviation, in percent (0 to 100).';
COMMENT ON COLUMN match_tolerances.quantity_tolerance_pct IS 'Accepted quantity deviation, in percent (0 to 100).';
COMMENT ON COLUMN match_tolerances.active                 IS 'False when the tolerance is disabled.';

-- ---------------------------------------------------------------------------
-- reference data
-- ---------------------------------------------------------------------------
INSERT INTO approval_rules (document_type, min_amount, max_amount, required_role) VALUES
    ('purchase_requisition',      0.00,  10000.00, 'buyer'),
    ('purchase_requisition',  10000.00, 100000.00, 'approver'),
    ('purchase_requisition', 100000.00,      NULL, 'manager');

INSERT INTO match_tolerances (material_category_id, price_tolerance_pct, quantity_tolerance_pct) VALUES
    (NULL, 2.00, 5.00);