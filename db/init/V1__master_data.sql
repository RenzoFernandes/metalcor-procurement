-- V1__master_data.sql
-- Metalcor Procurement v0.1, block 1: master data.
-- Fictional data only. Not SAP.

-- ---------------------------------------------------------------------------
-- Shared trigger function: keeps updated_at current on every UPDATE
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION set_updated_at() IS 'Trigger function: sets updated_at to now() before each UPDATE.';

-- ---------------------------------------------------------------------------
-- plants
-- ---------------------------------------------------------------------------
CREATE TABLE plants (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    code        varchar(10)  NOT NULL,
    name        varchar(100) NOT NULL,
    city        varchar(100) NOT NULL,
    state       char(2)      NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_plants PRIMARY KEY (id),
    CONSTRAINT uq_plants_code UNIQUE (code)
);

CREATE TRIGGER trg_plants_set_updated_at
    BEFORE UPDATE ON plants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  plants        IS 'Manufacturing plants of the company. Organizational unit that owns cost centers and users.';
COMMENT ON COLUMN plants.code   IS 'Unique short business code of the plant.';
COMMENT ON COLUMN plants.name   IS 'Plant name.';
COMMENT ON COLUMN plants.city   IS 'City where the plant is located.';
COMMENT ON COLUMN plants.state  IS 'Brazilian state (UF), two letters.';
COMMENT ON COLUMN plants.active IS 'False when the plant is no longer in use.';

-- ---------------------------------------------------------------------------
-- cost_centers
-- ---------------------------------------------------------------------------
CREATE TABLE cost_centers (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    code        varchar(10)  NOT NULL,
    name        varchar(100) NOT NULL,
    plant_id    bigint       NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_cost_centers PRIMARY KEY (id),
    CONSTRAINT uq_cost_centers_code UNIQUE (code),
    CONSTRAINT fk_cost_centers_plant FOREIGN KEY (plant_id) REFERENCES plants (id)
);

CREATE INDEX idx_cost_centers_plant_id ON cost_centers (plant_id);

CREATE TRIGGER trg_cost_centers_set_updated_at
    BEFORE UPDATE ON cost_centers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  cost_centers          IS 'Cost centers that consume purchased goods and services.';
COMMENT ON COLUMN cost_centers.code     IS 'Unique business code of the cost center.';
COMMENT ON COLUMN cost_centers.name     IS 'Cost center name.';
COMMENT ON COLUMN cost_centers.plant_id IS 'Plant the cost center belongs to.';
COMMENT ON COLUMN cost_centers.active   IS 'False when the cost center is closed.';

-- ---------------------------------------------------------------------------
-- units_of_measure
-- ---------------------------------------------------------------------------
CREATE TABLE units_of_measure (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    code        varchar(10)  NOT NULL,
    description varchar(100) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_units_of_measure PRIMARY KEY (id),
    CONSTRAINT uq_units_of_measure_code UNIQUE (code)
);

CREATE TRIGGER trg_units_of_measure_set_updated_at
    BEFORE UPDATE ON units_of_measure
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  units_of_measure             IS 'Units of measure used for material quantities (e.g. KG, UN, M).';
COMMENT ON COLUMN units_of_measure.code        IS 'Unique short code of the unit.';
COMMENT ON COLUMN units_of_measure.description IS 'Full description of the unit.';

-- ---------------------------------------------------------------------------
-- material_categories
-- ---------------------------------------------------------------------------
CREATE TABLE material_categories (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    code        varchar(10)  NOT NULL,
    name        varchar(100) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_material_categories PRIMARY KEY (id),
    CONSTRAINT uq_material_categories_code UNIQUE (code)
);

CREATE TRIGGER trg_material_categories_set_updated_at
    BEFORE UPDATE ON material_categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  material_categories      IS 'Groups of materials (e.g. steel, bearings, paint, packaging).';
COMMENT ON COLUMN material_categories.code IS 'Unique business code of the category.';
COMMENT ON COLUMN material_categories.name IS 'Category name.';

-- ---------------------------------------------------------------------------
-- materials
-- ---------------------------------------------------------------------------
CREATE TABLE materials (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    code            varchar(20)   NOT NULL,
    description     varchar(200)  NOT NULL,
    material_category_id bigint   NOT NULL,
    unit_of_measure_id   bigint   NOT NULL,
    standard_price  numeric(15,4) NOT NULL DEFAULT 0,
    active          boolean       NOT NULL DEFAULT true,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_materials PRIMARY KEY (id),
    CONSTRAINT uq_materials_code UNIQUE (code),
    CONSTRAINT fk_materials_material_category FOREIGN KEY (material_category_id) REFERENCES material_categories (id),
    CONSTRAINT fk_materials_unit_of_measure FOREIGN KEY (unit_of_measure_id) REFERENCES units_of_measure (id),
    CONSTRAINT ck_materials_standard_price CHECK (standard_price >= 0)
);

CREATE INDEX idx_materials_material_category_id ON materials (material_category_id);
CREATE INDEX idx_materials_unit_of_measure_id ON materials (unit_of_measure_id);

CREATE TRIGGER trg_materials_set_updated_at
    BEFORE UPDATE ON materials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  materials                IS 'Material master: raw materials, components and supplies that can be purchased.';
COMMENT ON COLUMN materials.code           IS 'Unique business code of the material.';
COMMENT ON COLUMN materials.description    IS 'Material description.';
COMMENT ON COLUMN materials.material_category_id IS 'Material category.';
COMMENT ON COLUMN materials.unit_of_measure_id IS 'Base unit of measure.';
COMMENT ON COLUMN materials.standard_price IS 'Reference price per base unit, used as a benchmark against actual prices.';
COMMENT ON COLUMN materials.active        IS 'False when the material can no longer be purchased.';

-- ---------------------------------------------------------------------------
-- suppliers
-- ---------------------------------------------------------------------------
CREATE TABLE suppliers (
    id                  bigint GENERATED ALWAYS AS IDENTITY,
    code                varchar(20)  NOT NULL,
    name                varchar(150) NOT NULL,
    city                varchar(100),
    state               char(2),
    country             char(2)      NOT NULL DEFAULT 'BR',
    payment_terms_days  integer      NOT NULL DEFAULT 30,
    active              boolean      NOT NULL DEFAULT true,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT uq_suppliers_code UNIQUE (code),
    CONSTRAINT ck_suppliers_payment_terms_days CHECK (payment_terms_days >= 0)
);

CREATE TRIGGER trg_suppliers_set_updated_at
    BEFORE UPDATE ON suppliers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  suppliers                    IS 'Supplier master. All suppliers are fictional.';
COMMENT ON COLUMN suppliers.code               IS 'Unique business code of the supplier.';
COMMENT ON COLUMN suppliers.name               IS 'Supplier name.';
COMMENT ON COLUMN suppliers.city               IS 'Supplier city.';
COMMENT ON COLUMN suppliers.state              IS 'State or province code, two letters.';
COMMENT ON COLUMN suppliers.country            IS 'ISO 3166-1 alpha-2 country code. Default BR.';
COMMENT ON COLUMN suppliers.payment_terms_days IS 'Default payment term in days, counted from the invoice date.';
COMMENT ON COLUMN suppliers.active             IS 'False when the supplier is blocked or no longer used.';

-- ---------------------------------------------------------------------------
-- supplier_materials
-- ---------------------------------------------------------------------------
CREATE TABLE supplier_materials (
    id              bigint GENERATED ALWAYS AS IDENTITY,
    supplier_id     bigint        NOT NULL,
    material_id     bigint        NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    lead_time_days  integer       NOT NULL,
    min_order_qty   numeric(15,3) NOT NULL,
    valid_from      date          NOT NULL,
    valid_to        date,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_supplier_materials PRIMARY KEY (id),
    CONSTRAINT uq_supplier_materials_supplier_material_valid_from UNIQUE (supplier_id, material_id, valid_from),
    CONSTRAINT fk_supplier_materials_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_materials_material FOREIGN KEY (material_id) REFERENCES materials (id),
    CONSTRAINT ck_supplier_materials_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_supplier_materials_lead_time_days CHECK (lead_time_days >= 0),
    CONSTRAINT ck_supplier_materials_min_order_qty CHECK (min_order_qty > 0),
    CONSTRAINT ck_supplier_materials_valid_period CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_supplier_materials_supplier_id ON supplier_materials (supplier_id);
CREATE INDEX idx_supplier_materials_material_id ON supplier_materials (material_id);
CREATE INDEX idx_supplier_materials_valid_from  ON supplier_materials (valid_from);

CREATE TRIGGER trg_supplier_materials_set_updated_at
    BEFORE UPDATE ON supplier_materials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  supplier_materials                IS 'Price list: which supplier sells which material, at what price and lead time, and for what validity period.';
COMMENT ON COLUMN supplier_materials.supplier_id    IS 'Supplier offering the material.';
COMMENT ON COLUMN supplier_materials.material_id    IS 'Material offered.';
COMMENT ON COLUMN supplier_materials.unit_price     IS 'Price per material base unit.';
COMMENT ON COLUMN supplier_materials.lead_time_days IS 'Expected delivery time in days after the order.';
COMMENT ON COLUMN supplier_materials.min_order_qty  IS 'Minimum order quantity, in the material base unit.';
COMMENT ON COLUMN supplier_materials.valid_from     IS 'First day the price is valid.';
COMMENT ON COLUMN supplier_materials.valid_to       IS 'Last day the price is valid. Null means open-ended.';

-- ---------------------------------------------------------------------------
-- app_users
-- ---------------------------------------------------------------------------
CREATE TABLE app_users (
    id          bigint GENERATED ALWAYS AS IDENTITY,
    name        varchar(100) NOT NULL,
    email       varchar(150) NOT NULL,
    role        varchar(20)  NOT NULL,
    plant_id    bigint,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_users PRIMARY KEY (id),
    CONSTRAINT uq_app_users_email UNIQUE (email),
    CONSTRAINT fk_app_users_plant FOREIGN KEY (plant_id) REFERENCES plants (id),
    CONSTRAINT ck_app_users_role CHECK (role IN ('requester', 'approver', 'buyer', 'finance', 'manager'))
);

CREATE INDEX idx_app_users_plant_id ON app_users (plant_id);

CREATE TRIGGER trg_app_users_set_updated_at
    BEFORE UPDATE ON app_users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE  app_users          IS 'Application users. No sign-up: users are picked through "Enter as" by role.';
COMMENT ON COLUMN app_users.name     IS 'User full name.';
COMMENT ON COLUMN app_users.email    IS 'Unique user email.';
COMMENT ON COLUMN app_users.role     IS 'Profile: requester, approver, buyer, finance or manager.';
COMMENT ON COLUMN app_users.plant_id IS 'Plant the user is assigned to. Null for users not tied to one plant.';
COMMENT ON COLUMN app_users.active   IS 'False when the user is disabled.';
