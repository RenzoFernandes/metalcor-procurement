#!/usr/bin/env python3
"""Generates db/seed/01_master_data.sql: fictional master data for Metalcor Procurement.

Part A of the seed (master data only). Standard library only, fixed seed (42), so the
output is reproducible. Reads data/seed/suppliers.csv and data/seed/materials.csv.

build_master() returns the master data as Python structures (same ids as the SQL file),
so the later seed parts (generate_purchasing.py, ...) can reuse it.

Usage (from anywhere):  python tools/seed/generate_seed.py
"""
import csv
import random
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUPPLIERS_CSV = ROOT / "data" / "seed" / "suppliers.csv"
MATERIALS_CSV = ROOT / "data" / "seed" / "materials.csv"
OUTPUT_SQL = ROOT / "db" / "seed" / "01_master_data.sql"

SEED = 42
TS = "'2025-09-15 00:00:00+00'"  # created_at / updated_at of everything

UNITS = [
    ("KG", "Quilograma"),
    ("M", "Metro"),
    ("UN", "Unidade"),
    ("L", "Litro"),
    ("PR", "Par"),
]

CATEGORIES = [
    ("ACO", "Aço e metais"),
    ("ROL", "Rolamentos e mancais"),
    ("FER", "Ferramentas de corte"),
    ("TIN", "Tintas e químicos"),
    ("EMB", "Embalagens"),
    ("MAN", "Manutenção e EPI"),
]

PLANTS = [
    ("SOR", "Metalcor Sorocaba", "Sorocaba", "SP"),
    ("GRA", "Metalcor Gravataí", "Gravataí", "RS"),
]

COST_CENTERS = [
    ("USI", "Usinagem"),
    ("EST", "Estamparia"),
    ("PIN", "Pintura"),
    ("MAN", "Manutenção"),
    ("QUA", "Qualidade"),
    ("LOG", "Logística"),
]

# (name, email, role, plant code or None)
USERS = [
    ("Ana Paula Ribeiro",      "ana.ribeiro@metalcor.example",       "requester", "SOR"),
    ("Carlos Eduardo Mendes",  "carlos.mendes@metalcor.example",     "requester", "SOR"),
    ("Fernanda Lima Castro",   "fernanda.castro@metalcor.example",   "requester", "SOR"),
    ("Rafael Augusto Nogueira", "rafael.nogueira@metalcor.example",  "requester", "SOR"),
    ("Juliana Martins Prado",  "juliana.prado@metalcor.example",     "requester", "GRA"),
    ("Thiago Henrique Souza",  "thiago.souza@metalcor.example",      "requester", "GRA"),
    ("Camila Ferreira Duarte", "camila.duarte@metalcor.example",     "requester", "GRA"),
    ("Marcos Vinícius Tavares", "marcos.tavares@metalcor.example",   "buyer",     "SOR"),
    ("Patrícia Almeida Rocha", "patricia.rocha@metalcor.example",    "buyer",     "GRA"),
    ("Roberto Carlos Pinheiro", "roberto.pinheiro@metalcor.example", "approver",  "SOR"),
    ("Luciana Barros Teixeira", "luciana.teixeira@metalcor.example", "approver",  "GRA"),
    ("Eduardo Campos Moreira", "eduardo.moreira@metalcor.example",   "finance",   None),
    ("Beatriz Cardoso Lopes",  "beatriz.lopes@metalcor.example",     "finance",   None),
    ("Henrique Batista Vasconcelos", "henrique.vasconcelos@metalcor.example", "manager", None),
]

# Tables in load order, for the sequence reset at the end.
TABLE_ORDER = [
    "units_of_measure", "material_categories", "plants", "cost_centers",
    "suppliers", "materials", "supplier_materials", "app_users",
]

PRICE_V1_FROM, PRICE_V1_TO = "2025-10-01", "2026-03-31"
PRICE_V2_FROM = "2026-04-01"

CENT4 = Decimal("0.0001")
THOUSANDTH = Decimal("0.001")

# Audit triggers are disabled only while a seed script loads its data.
DISABLE_AUDIT_SQL = (
    "-- Audit triggers are disabled only for this initial load and re-enabled at the end.\n"
    "-- Only triggers that call audit_row_change() are touched; no other trigger or constraint is changed.\n"
    "DO $$\n"
    "DECLARE\n"
    "    t record;\n"
    "BEGIN\n"
    "    FOR t IN\n"
    "        SELECT tgrelid::regclass AS table_name, tgname\n"
    "          FROM pg_trigger\n"
    "         WHERE tgfoid = 'audit_row_change'::regproc\n"
    "           AND NOT tgisinternal\n"
    "    LOOP\n"
    "        EXECUTE format('ALTER TABLE %s DISABLE TRIGGER %I', t.table_name, t.tgname);\n"
    "    END LOOP;\n"
    "END;\n"
    "$$;\n\n")

ENABLE_AUDIT_SQL = (
    "DO $$\n"
    "DECLARE\n"
    "    t record;\n"
    "BEGIN\n"
    "    FOR t IN\n"
    "        SELECT tgrelid::regclass AS table_name, tgname\n"
    "          FROM pg_trigger\n"
    "         WHERE tgfoid = 'audit_row_change'::regproc\n"
    "           AND NOT tgisinternal\n"
    "    LOOP\n"
    "        EXECUTE format('ALTER TABLE %s ENABLE TRIGGER %I', t.table_name, t.tgname);\n"
    "    END LOOP;\n"
    "END;\n"
    "$$;\n\n")


def q(text):
    """SQL string literal."""
    return "'" + text.replace("'", "''") + "'"


def num(value):
    return format(value, "f")


def read_csv(path):
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def nice_moq(base, unit):
    """Rounds a minimum order quantity to a round number that suits its magnitude."""
    if base >= 1000:
        step = 100
    elif base >= 100:
        step = 10
    elif base >= 10:
        step = 5
    else:
        step = 1
    value = (Decimal(str(base)) / step).quantize(Decimal(1), ROUND_HALF_UP) * step
    return max(value, Decimal(step)).quantize(THOUSANDTH)


def insert_sql(table, columns, rows):
    """INSERT with explicit ids (1..n) and fixed timestamps. Rows hold ready-to-use SQL literals."""
    lines = []
    for i, row in enumerate(rows, start=1):
        values = ", ".join([str(i)] + list(row) + [TS, TS])
        lines.append(f"    ({values})")
    cols = ", ".join(["id"] + columns + ["created_at", "updated_at"])
    return (f"INSERT INTO {table} ({cols}) OVERRIDING SYSTEM VALUE VALUES\n"
            + ",\n".join(lines) + ";\n")


def build_master():
    """Builds the master data (random.Random(42)) with the same ids as 01_master_data.sql.

    Returns a dict with:
      suppliers, materials, users: lists of dicts (key "id" is the database id);
      supplier_materials: list of dicts, price-list version 1 then 2 per supplier/material;
      plant_ids, category_ids, unit_ids: code -> id;
      cost_center_ids: (plant code, cost center code) -> id.
    """
    rng = random.Random(SEED)
    suppliers_csv = read_csv(SUPPLIERS_CSV)
    materials_csv = read_csv(MATERIALS_CSV)

    unit_ids = {code: i for i, (code, _) in enumerate(UNITS, start=1)}
    category_ids = {code: i for i, (code, _) in enumerate(CATEGORIES, start=1)}
    plant_ids = {code: i for i, (code, *_) in enumerate(PLANTS, start=1)}

    for m in materials_csv:
        assert m["unit"] in unit_ids, f"unknown unit {m['unit']}"
        assert m["category_code"] in category_ids, f"unknown category {m['category_code']}"
    for s in suppliers_csv:
        assert s["category_code"] in category_ids, f"unknown category {s['category_code']}"
        assert s["price_tier"] in ("economy", "premium"), f"unknown tier {s['price_tier']}"

    # cost_centers: every cost center in every plant, ids in load order
    cost_center_ids = {}
    for plant_code, *_ in PLANTS:
        for cc_code, _ in COST_CENTERS:
            cost_center_ids[(plant_code, cc_code)] = len(cost_center_ids) + 1

    suppliers = []
    for si, s in enumerate(suppliers_csv, start=1):
        suppliers.append({
            "id": si, "code": s["code"], "name": s["name"], "city": s["city"], "state": s["state"],
            "category_code": s["category_code"], "price_tier": s["price_tier"],
            "payment_terms_days": int(s["payment_terms_days"]),
            "lead_time_days": int(s["lead_time_days"]),
            "on_time_rate": Decimal(s["on_time_rate"]),
        })

    # materials: standard_price = midpoint of the CSV price range
    materials = []
    for mi, m in enumerate(materials_csv, start=1):
        price = ((Decimal(m["price_min"]) + Decimal(m["price_max"])) / 2).quantize(CENT4, ROUND_HALF_UP)
        materials.append({
            "id": mi, "code": m["code"], "description": m["description"],
            "category_code": m["category_code"], "category_id": category_ids[m["category_code"]],
            "unit": m["unit"], "unit_id": unit_ids[m["unit"]], "standard_price": price,
            "monthly_qty_min": int(m["monthly_qty_min"]), "monthly_qty_max": int(m["monthly_qty_max"]),
        })

    # supplier_materials: every supplier x every material of its category, two price-list versions
    supplier_materials = []
    for s, s_csv in zip(suppliers, suppliers_csv):
        if s["price_tier"] == "economy":
            low, high = 0.10, 0.25
        else:
            low, high = 0.75, 0.90
        for m, m_csv in zip(materials, materials_csv):
            if m["category_code"] != s["category_code"]:
                continue
            pmin, pmax = Decimal(m_csv["price_min"]), Decimal(m_csv["price_max"])
            fraction = Decimal(str(round(rng.uniform(low, high), 4)))
            price_v1 = (pmin + fraction * (pmax - pmin)).quantize(CENT4, ROUND_HALF_UP)
            lead_time = max(1, s["lead_time_days"] + rng.randint(-2, 2))
            moq_base = int(m_csv["monthly_qty_min"]) * rng.choice([0.05, 0.10, 0.15])
            moq = nice_moq(moq_base, m["unit"])
            increase = Decimal(str(round(rng.uniform(0.03, 0.08), 4)))
            price_v2 = (price_v1 * (1 + increase)).quantize(CENT4, ROUND_HALF_UP)

            common = {"supplier_id": s["id"], "material_id": m["id"],
                      "lead_time_days": lead_time, "min_order_qty": moq}
            supplier_materials.append({**common, "version": 1, "unit_price": price_v1,
                                       "valid_from": PRICE_V1_FROM, "valid_to": PRICE_V1_TO})
            supplier_materials.append({**common, "version": 2, "unit_price": price_v2,
                                       "valid_from": PRICE_V2_FROM, "valid_to": None})

    users = [{"id": i, "name": name, "email": email, "role": role, "plant_code": plant,
              "plant_id": plant_ids[plant] if plant else None}
             for i, (name, email, role, plant) in enumerate(USERS, start=1)]

    return {
        "suppliers": suppliers, "materials": materials, "supplier_materials": supplier_materials,
        "users": users, "plant_ids": plant_ids, "category_ids": category_ids, "unit_ids": unit_ids,
        "cost_center_ids": cost_center_ids,
    }


def main():
    master = build_master()
    plant_ids = master["plant_ids"]

    counts = {}
    body = []

    # units_of_measure
    rows = [[q(c), q(d)] for c, d in UNITS]
    body.append(insert_sql("units_of_measure", ["code", "description"], rows))
    counts["units_of_measure"] = len(rows)

    # material_categories
    rows = [[q(c), q(n)] for c, n in CATEGORIES]
    body.append(insert_sql("material_categories", ["code", "name"], rows))
    counts["material_categories"] = len(rows)

    # plants
    rows = [[q(c), q(n), q(city), q(st), "true"] for c, n, city, st in PLANTS]
    body.append(insert_sql("plants", ["code", "name", "city", "state", "active"], rows))
    counts["plants"] = len(rows)

    # cost_centers: every cost center in every plant
    rows = []
    for plant_code, *_ in PLANTS:
        for cc_code, cc_name in COST_CENTERS:
            rows.append([q(f"{plant_code}-{cc_code}"), q(cc_name), str(plant_ids[plant_code]), "true"])
    body.append(insert_sql("cost_centers", ["code", "name", "plant_id", "active"], rows))
    counts["cost_centers"] = len(rows)

    # suppliers
    rows = [[q(s["code"]), q(s["name"]), q(s["city"]), q(s["state"]), q("BR"),
             str(s["payment_terms_days"]), "true"] for s in master["suppliers"]]
    body.append(insert_sql(
        "suppliers", ["code", "name", "city", "state", "country", "payment_terms_days", "active"], rows))
    counts["suppliers"] = len(rows)

    # materials
    rows = [[q(m["code"]), q(m["description"]), str(m["category_id"]),
             str(m["unit_id"]), num(m["standard_price"]), "true"] for m in master["materials"]]
    body.append(insert_sql(
        "materials",
        ["code", "description", "material_category_id", "unit_of_measure_id", "standard_price", "active"],
        rows))
    counts["materials"] = len(rows)

    # supplier_materials
    rows = [[str(sm["supplier_id"]), str(sm["material_id"]), num(sm["unit_price"]),
             str(sm["lead_time_days"]), num(sm["min_order_qty"]),
             q(sm["valid_from"]), q(sm["valid_to"]) if sm["valid_to"] else "NULL"]
            for sm in master["supplier_materials"]]
    body.append(insert_sql(
        "supplier_materials",
        ["supplier_id", "material_id", "unit_price", "lead_time_days", "min_order_qty", "valid_from", "valid_to"],
        rows))
    counts["supplier_materials"] = len(rows)

    # app_users
    rows = [[q(u["name"]), q(u["email"]), q(u["role"]),
             str(u["plant_id"]) if u["plant_id"] else "NULL", "true"] for u in master["users"]]
    body.append(insert_sql("app_users", ["name", "email", "role", "plant_id", "active"], rows))
    counts["app_users"] = len(rows)

    # ---- assemble the script ----
    out = []
    out.append("-- 01_master_data.sql\n"
               "-- GENERATED by tools/seed/generate_seed.py (random.Random(42)). Do not edit by hand.\n"
               "-- Fictional data for a portfolio project. Any resemblance to real companies is coincidental.\n\n")
    out.append("SET client_encoding = 'UTF8';\n\n")
    out.append("BEGIN;\n\n")
    out.append(DISABLE_AUDIT_SQL)
    out.append("\n".join(body))
    out.append("\n-- Sequences continue after the explicit ids.\n")
    for table in TABLE_ORDER:
        out.append(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), (SELECT max(id) FROM {table}));\n")
    out.append("\n" + ENABLE_AUDIT_SQL + "COMMIT;\n")

    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(out))

    print(f"Wrote {OUTPUT_SQL}")
    for table in TABLE_ORDER:
        print(f"  {table}: {counts[table]} rows")


if __name__ == "__main__":
    main()