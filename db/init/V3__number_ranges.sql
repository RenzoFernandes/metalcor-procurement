-- V3__number_ranges.sql
-- Metalcor Procurement v0.1: document numbering.
-- Requires V1__master_data.sql (set_updated_at()).
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- number_ranges
-- ---------------------------------------------------------------------------
CREATE TABLE number_ranges (
    id             bigint GENERATED ALWAYS AS IDENTITY,
    document_type  varchar(30) NOT NULL,
    fiscal_year    integer     NOT NULL,
    prefix         varchar(5)  NOT NULL,
    last_value     bigint      NOT NULL DEFAULT 0,
    padding        integer     NOT NULL DEFAULT 6,
    active         boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_number_ranges PRIMARY KEY (id),
    CONSTRAINT uq_number_ranges_document_type_fiscal_year UNIQUE (document_type, fiscal_year),
    CONSTRAINT ck_number_ranges_document_type CHECK (document_type IN (
        'purchase_requisition', 'purchase_order', 'goods_receipt', 'invoice_receipt', 'payment'
    )),
    CONSTRAINT ck_number_ranges_last_value CHECK (last_value >= 0),
    CONSTRAINT ck_number_ranges_padding CHECK (padding BETWEEN 1 AND 12)
);

CREATE TRIGGER trg_number_ranges_set_updated_at
    BEFORE UPDATE ON number_ranges
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  number_ranges               IS 'Document number counters, one per document type and fiscal year.';
COMMENT ON COLUMN number_ranges.document_type IS 'Document the counter numbers: purchase_requisition, purchase_order, goods_receipt, invoice_receipt or payment.';
COMMENT ON COLUMN number_ranges.fiscal_year   IS 'Calendar year the counter belongs to.';
COMMENT ON COLUMN number_ranges.prefix       IS 'Prefix of the formatted number (e.g. PO).';
COMMENT ON COLUMN number_ranges.last_value    IS 'Last number issued. 0 means none issued yet.';
COMMENT ON COLUMN number_ranges.padding       IS 'Number of digits of the sequential part, left-padded with zeros (1 to 12).';
COMMENT ON COLUMN number_ranges.active        IS 'False when the range is closed; no numbers are issued from it.';

-- ---------------------------------------------------------------------------
-- next_document_number
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION next_document_number(p_document_type text, p_date date DEFAULT current_date)
RETURNS text
LANGUAGE plpgsql
AS $$
DECLARE
    v_year    integer := extract(year FROM p_date)::integer;
    v_prefix  varchar(5);
    v_value   bigint;
    v_padding integer;
BEGIN
    UPDATE number_ranges
       SET last_value = last_value + 1
     WHERE document_type = p_document_type
       AND fiscal_year = v_year
       AND active
    RETURNING prefix, last_value, padding
      INTO v_prefix, v_value, v_padding;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Number range for document type "%" and fiscal year % does not exist or is inactive',
            p_document_type, v_year;
    END IF;

    RETURN v_prefix || '-' || v_year || '-' || lpad(v_value::text, v_padding, '0');
END;
$$;

COMMENT ON FUNCTION next_document_number(text, date) IS
    'Issues the next formatted document number (PREFIX-YEAR-NNNNNN) for a document type and the fiscal year of the given date. '
    'Deliberately not a sequence: the increment happens inside the caller''s transaction, so a rollback also undoes the number '
    'and no gaps are left. The UPDATE takes a row lock on the range, so concurrent callers are serialized and never get the '
    'same number; the lock is held until the caller''s transaction ends. Raises an exception if the range does not exist or is inactive.';

-- ---------------------------------------------------------------------------
-- reference data
-- ---------------------------------------------------------------------------
INSERT INTO number_ranges (document_type, fiscal_year, prefix)
SELECT t.document_type, y.fiscal_year, t.prefix
  FROM (VALUES
        ('purchase_requisition', 'PR'),
        ('purchase_order',       'PO'),
        ('goods_receipt',        'GR'),
        ('invoice_receipt',      'IR'),
        ('payment',              'PY')
       ) AS t (document_type, prefix)
 CROSS JOIN (VALUES (2025), (2026), (2027)) AS y (fiscal_year);