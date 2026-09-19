#!/usr/bin/env python3
"""Generates db/seed/02_purchasing.sql: fictional requisitions, approvals and purchase orders.

Part B1 of the seed. Standard library only, fixed seed (43), Decimal for money, so the output
is reproducible. Reuses the master data of generate_seed.py (build_master()), so 01_master_data.sql
must be loaded first. Also writes db/seed/anomalies_manifest.csv (planted anomalies).

Simulation window: 2025-10-01 to 2026-09-18, business days only (Monday to Friday; holidays are
not modelled).

Usage (from anywhere):  python tools/seed/generate_purchasing.py
"""
import calendar
import csv
import math
import random
import sys
from collections import Counter, defaultdict
from datetime import date, datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP

from generate_seed import (
    CATEGORIES, DISABLE_AUDIT_SQL, ENABLE_AUDIT_SQL, PLANTS, ROOT, THOUSANDTH, build_master, num, q,
)

OUTPUT_SQL = ROOT / "db" / "seed" / "02_purchasing.sql"
MANIFEST_CSV = ROOT / "db" / "seed" / "anomalies_manifest.csv"

SEED = 43
START = date(2025, 10, 1)
END = date(2026, 9, 18)
END_DT = datetime(2026, 9, 18, 18, 0, 0)  # no timestamp goes past the end of the simulation
PRICE_V2_FROM = date(2026, 4, 1)
TZ = "-03"  # America/Sao_Paulo; timestamps are written with this fixed offset

CENT = Decimal("0.01")

# ---- demand ----
SOR_SHARE = 0.55                      # share of each material's monthly demand bought by Sorocaba
JAN_FACTOR_NUM, JAN_FACTOR_DEN = 3, 4  # -25% in January
PEAK_MONTHS = (3, 6, 9, 12)           # +40% in the last two weeks of these months
PEAK_FACTOR = 1.4
MAX_LINES_PER_MATERIAL = 4
MAX_ITEMS_PER_REQUISITION = 8

# cost centers that buy each material category
CC_BY_CATEGORY = {
    "ACO": ["EST", "USI"],
    "ROL": ["MAN", "USI"],
    "FER": ["USI"],
    "TIN": ["PIN"],
    "EMB": ["LOG"],
    "MAN": ["MAN", "QUA"],
}

# ---- statuses and approval ----
CANCEL_RATE = 0.02
REJECT_RATE_LOW = 0.02    # amount <= R$ 10,000
REJECT_RATE_HIGH = 0.07   # amount above R$ 10,000 (about 4% overall)
REJECT_THRESHOLD = Decimal("10000.00")

# approval_rules of V2 (document_type purchase_requisition): (id, min inclusive, max exclusive, role)
APPROVAL_RULES = [
    (1, Decimal("0.00"), Decimal("10000.00"), "buyer"),
    (2, Decimal("10000.00"), Decimal("100000.00"), "approver"),
    (3, Decimal("100000.00"), None, "manager"),
]

REJECTION_REASONS = [
    "Orçamento do centro de custo excedido no período",
    "Quantidade acima do consumo histórico do material",
    "Item já contemplado em outra requisição em aberto",
    "Justificativa de necessidade insuficiente",
    "Solicitar cotação adicional antes de comprar",
    "Estoque de segurança suficiente para o período",
    "Prazo de necessidade incompatível com o volume solicitado",
]

# ---- purchase orders ----
ECONOMY_RATE = 0.55
ORDER_DELAY_MAX_BD = 2
ANOMALY_RATE = 0.01   # share of all purchase orders created without requisition or approval
ANOMALY_ECONOMY_RATE = 0.70
ANOMALY_MIN_TOTAL = 10500
ANOMALY_MAX_TOTAL = 40000

DOC_TYPES = {"PR": "purchase_requisition", "PO": "purchase_order"}


# ---------------------------------------------------------------------------
# calendar helpers
# ---------------------------------------------------------------------------
BDAYS = [d for d in (START + timedelta(n) for n in range((END - START).days + 1)) if d.weekday() < 5]
BD_INDEX = {d: i for i, d in enumerate(BDAYS)}


def add_bdays(d, n):
    """The business day n days after d (capped at the end of the simulation)."""
    return BDAYS[min(BD_INDEX[d] + n, len(BDAYS) - 1)]


def weekdays_in_month(year, month):
    days = calendar.monthrange(year, month)[1]
    return sum(1 for day in range(1, days + 1) if date(year, month, day).weekday() < 5)


def in_peak_window(d):
    """Last two weeks (14 calendar days) of March, June, September and December."""
    return d.month in PEAK_MONTHS and d.day > calendar.monthrange(d.year, d.month)[1] - 14


def random_time(rng, d):
    return datetime(d.year, d.month, d.day, rng.randint(8, 17), rng.randint(0, 59), rng.randint(0, 59))


def after(rng, moment, earliest):
    """moment, or a few minutes after `earliest` when moment would not come after it."""
    if moment > earliest:
        return moment
    return earliest + timedelta(minutes=rng.randint(5, 120))


def ts(dt):
    return f"'{dt:%Y-%m-%d %H:%M:%S}{TZ}'"


def dl(d):
    return f"'{d:%Y-%m-%d}'"


def brl(value):
    return "R$ " + f"{value:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")


def money(quantity, price):
    return (Decimal(quantity) * price).quantize(CENT, ROUND_HALF_UP)


def qty3(quantity):
    return num(Decimal(quantity).quantize(THOUSANDTH))


def rule_for(amount):
    for rule in APPROVAL_RULES:
        _, low, high, _ = rule
        if amount >= low and (high is None or amount < high):
            return rule
    raise ValueError(f"no approval rule for {amount}")


# ---------------------------------------------------------------------------
# 1. demand -> lines (material, plant, cost center, date, quantity)
# ---------------------------------------------------------------------------
def build_lines(rng, materials, min_qty):
    months = defaultdict(list)
    for d in BDAYS:
        months[(d.year, d.month)].append(d)

    lines = []
    for (year, month), days in months.items():
        prorate = len(days) / weekdays_in_month(year, month)  # only September 2026 is partial
        for mat in materials:
            total = rng.randint(mat["monthly_qty_min"], mat["monthly_qty_max"])
            if month == 1:
                total = total * JAN_FACTOR_NUM // JAN_FACTOR_DEN
            total = int(total * prorate + 0.5)
            sor = int(total * SOR_SHARE + 0.5)
            for plant_code, plant_qty in (("SOR", sor), ("GRA", total - sor)):
                if plant_qty <= 0:
                    continue
                moq = min_qty[mat["id"]]
                n = min(rng.randint(1, MAX_LINES_PER_MATERIAL), max(1, plant_qty // moq))
                dates = sorted(rng.choice(days) for _ in range(n))
                weights = [rng.uniform(0.5, 1.5) for _ in range(n)]
                for d, w in zip(dates, weights):
                    factor = PEAK_FACTOR if in_peak_window(d) else 1.0
                    quantity = max(moq, int(plant_qty * w / sum(weights) * factor + 0.5))
                    cc = rng.choice(CC_BY_CATEGORY[mat["category_code"]])
                    lines.append({"material_id": mat["id"], "plant": plant_code, "cc": cc,
                                  "date": d, "quantity": quantity})
    return lines


# ---------------------------------------------------------------------------
# 2-4. requisitions, items and approvals
# ---------------------------------------------------------------------------
def build_requisitions(rng, master, lines, numbers):
    materials = {m["id"]: m for m in master["materials"]}
    users = master["users"]
    requesters = defaultdict(list)
    for u in users:
        if u["role"] == "requester":
            requesters[u["plant_code"]].append(u)

    def pick_user(role, plant_code):
        same_plant = [u for u in users if u["role"] == role and u["plant_code"] == plant_code]
        pool = same_plant or [u for u in users if u["role"] == role]
        return rng.choice(pool)

    # group lines by date, plant and cost center; the same material on the same day is one line
    groups = defaultdict(dict)
    for line in lines:
        g = groups[(line["date"], line["plant"], line["cc"])]
        g[line["material_id"]] = g.get(line["material_id"], 0) + line["quantity"]

    last_days = set(BDAYS[-3:])
    requisitions, approvals = [], []
    item_id = 0
    for key in sorted(groups):
        d, plant_code, cc_code = key
        entries = sorted(groups[key].items())
        for start in range(0, len(entries), MAX_ITEMS_PER_REQUISITION):
            chunk = entries[start:start + MAX_ITEMS_PER_REQUISITION]
            r = {
                "id": len(requisitions) + 1,
                "number": numbers.next("PR", d),
                "date": d,
                "plant": plant_code,
                "plant_id": master["plant_ids"][plant_code],
                "cost_center_id": master["cost_center_ids"][(plant_code, cc_code)],
                "requested_by": rng.choice(requesters[plant_code])["id"],
                "needed_by": d + timedelta(days=rng.randint(5, 20)),
                "created_at": random_time(rng, d),
                "items": [],
                "approved_by": None, "approved_at": None, "rejection_reason": None, "notes": None,
            }
            for line_number, (material_id, quantity) in enumerate(chunk, start=1):
                item_id += 1
                price = materials[material_id]["standard_price"]
                r["items"].append({
                    "id": item_id, "line_number": line_number, "material_id": material_id,
                    "unit_id": materials[material_id]["unit_id"], "quantity": quantity,
                    "price": price, "total": money(quantity, price),
                })
            r["amount"] = sum((i["total"] for i in r["items"]), Decimal("0.00"))
            r["updated_at"] = r["created_at"]

            if rng.random() < CANCEL_RATE:
                r["status"] = "cancelled"
                r["notes"] = "Cancelada pelo solicitante."
                r["updated_at"] = min(r["created_at"] + timedelta(hours=rng.randint(2, 72)), END_DT)
            elif d in last_days:
                r["status"] = "pending_approval"
            else:
                rule_id, _, _, role = rule_for(r["amount"])
                reject_rate = REJECT_RATE_HIGH if r["amount"] > REJECT_THRESHOLD else REJECT_RATE_LOW
                decision = "rejected" if rng.random() < reject_rate else "approved"
                decider = pick_user(role, plant_code)
                decided_day = add_bdays(d, rng.randint(0, 3))
                decided_at = after(rng, random_time(rng, decided_day), r["created_at"])
                comment = rng.choice(REJECTION_REASONS) if decision == "rejected" else None

                r["status"] = decision
                r["updated_at"] = decided_at
                if decision == "approved":
                    r["approved_by"], r["approved_at"] = decider["id"], decided_at
                else:
                    r["rejection_reason"] = comment
                approvals.append({
                    "id": len(approvals) + 1, "purchase_requisition_id": r["id"], "step": 1,
                    "required_role": role, "approval_rule_id": rule_id, "decided_by": decider["id"],
                    "decision": decision, "comment": comment, "amount_evaluated": r["amount"],
                    "decided_at": decided_at,
                })
            requisitions.append(r)
    return requisitions, approvals


# ---------------------------------------------------------------------------
# 5-6. purchase orders (from approved requisitions, plus planted orders without approval)
# ---------------------------------------------------------------------------
def build_orders(rng, master, requisitions, numbers):
    materials = {m["id"]: m for m in master["materials"]}
    suppliers = {s["id"]: s for s in master["suppliers"]}
    supplier_by_category_tier = {(s["category_code"], s["price_tier"]): s for s in master["suppliers"]}
    buyers = [u for u in master["users"] if u["role"] == "buyer"]

    price = {}
    lead_time = {}
    for sm in master["supplier_materials"]:
        price[(sm["supplier_id"], sm["material_id"], sm["version"])] = sm["unit_price"]
        lead_time[(sm["supplier_id"], sm["material_id"])] = sm["lead_time_days"]

    def price_at(supplier_id, material_id, order_date):
        return price[(supplier_id, material_id, 2 if order_date >= PRICE_V2_FROM else 1)]

    orders = []

    def new_order(supplier, plant_id, requisition_id, order_date, created_at, buyer, items):
        orders.append({
            "seq": len(orders), "supplier_id": supplier["id"], "plant_id": plant_id,
            "requisition_id": requisition_id, "date": order_date, "created_at": created_at,
            "buyer_id": buyer["id"], "payment_terms_days": supplier["payment_terms_days"],
            "expected_delivery_date": order_date + timedelta(
                days=max(lead_time[(supplier["id"], i["material_id"])] for i in items)),
            "items": items,
        })

    # from approved requisitions: one order per supplier
    for r in requisitions:
        if r["status"] != "approved":
            continue
        by_supplier = {}
        for item in r["items"]:
            category = materials[item["material_id"]]["category_code"]
            tier = "economy" if rng.random() < ECONOMY_RATE else "premium"
            supplier = supplier_by_category_tier[(category, tier)]
            by_supplier.setdefault(supplier["id"], []).append(item)
        for supplier_id in sorted(by_supplier):
            order_date = add_bdays(r["approved_at"].date(), rng.randint(0, ORDER_DELAY_MAX_BD))
            created_at = after(rng, random_time(rng, order_date), r["approved_at"])
            items = [{
                "material_id": i["material_id"], "unit_id": i["unit_id"], "quantity": i["quantity"],
                "unit_price": price_at(supplier_id, i["material_id"], order_date),
                "requisition_item_id": i["id"],
            } for i in by_supplier[supplier_id]]
            new_order(suppliers[supplier_id], r["plant_id"], r["id"], order_date, created_at,
                      rng.choice(buyers), items)

    # anomaly: orders without requisition and without approval, total above R$ 10,000
    regular = len(orders)
    n_anomalies = int(regular * ANOMALY_RATE / (1 - ANOMALY_RATE) + 0.5)
    materials_by_category = defaultdict(list)
    for m in master["materials"]:
        materials_by_category[m["category_code"]].append(m)
    anomaly_seqs = []
    for _ in range(n_anomalies):
        category = rng.choice(CATEGORIES)[0]
        tier = "economy" if rng.random() < ANOMALY_ECONOMY_RATE else "premium"
        supplier = supplier_by_category_tier[(category, tier)]
        picked = sorted(rng.sample(materials_by_category[category], rng.randint(1, 3)),
                        key=lambda m: m["id"])
        order_date = rng.choice(BDAYS)
        target = Decimal(rng.randint(ANOMALY_MIN_TOTAL, ANOMALY_MAX_TOTAL))
        items = []
        for m in picked:
            unit_price = price_at(supplier["id"], m["id"], order_date)
            min_qty = next(sm["min_order_qty"] for sm in master["supplier_materials"]
                           if sm["supplier_id"] == supplier["id"] and sm["material_id"] == m["id"])
            quantity = max(int(min_qty), math.ceil(target / len(picked) / unit_price))
            items.append({"material_id": m["id"], "unit_id": m["unit_id"], "quantity": quantity,
                          "unit_price": unit_price, "requisition_item_id": None})
        plant_id = master["plant_ids"][rng.choice(PLANTS)[0]]
        anomaly_seqs.append(len(orders))
        new_order(supplier, plant_id, None, order_date, random_time(rng, order_date),
                  rng.choice(buyers), items)

    # chronological ids and numbers
    orders.sort(key=lambda o: (o["date"], o["created_at"], o["seq"]))
    anomalous = set(anomaly_seqs)
    item_id = 0
    anomaly_numbers = []
    for i, o in enumerate(orders, start=1):
        o["id"] = i
        o["number"] = numbers.next("PO", o["date"])
        o["status"] = "issued"
        for line_number, item in enumerate(o["items"], start=1):
            item_id += 1
            item["id"] = item_id
            item["line_number"] = line_number
            item["total"] = money(item["quantity"], item["unit_price"])
        o["total"] = sum((it["total"] for it in o["items"]), Decimal("0.00"))
        if o["seq"] in anomalous:
            anomaly_numbers.append(o["number"])
    return orders, anomaly_numbers


class Numbers:
    """Sequential document numbers per type and year, in the format of next_document_number()."""

    def __init__(self):
        self.last = defaultdict(int)

    def next(self, prefix, d):
        self.last[(prefix, d.year)] += 1
        return f"{prefix}-{d.year}-{self.last[(prefix, d.year)]:06d}"


# ---------------------------------------------------------------------------
# SQL output
# ---------------------------------------------------------------------------
def insert_rows(table, columns, rows, chunk=500):
    """INSERT statements with explicit ids. Rows hold ready-to-use SQL literals."""
    cols = ", ".join(columns)
    statements = []
    for start in range(0, len(rows), chunk):
        values = ",\n".join("    (" + ", ".join(row) + ")" for row in rows[start:start + chunk])
        statements.append(f"INSERT INTO {table} ({cols}) OVERRIDING SYSTEM VALUE VALUES\n{values};\n")
    return "\n".join(statements)


def opt(value, fmt=str):
    return "NULL" if value is None else fmt(value)


def render_sql(requisitions, approvals, orders, numbers):
    body = []

    body.append(insert_rows(
        "purchase_requisitions",
        ["id", "document_number", "plant_id", "cost_center_id", "requested_by", "needed_by", "status",
         "approved_by", "approved_at", "rejection_reason", "notes", "created_at", "updated_at"],
        [[str(r["id"]), q(r["number"]), str(r["plant_id"]), str(r["cost_center_id"]),
          str(r["requested_by"]), dl(r["needed_by"]), q(r["status"]), opt(r["approved_by"]),
          opt(r["approved_at"], ts), opt(r["rejection_reason"], q), opt(r["notes"], q),
          ts(r["created_at"]), ts(r["updated_at"])] for r in requisitions]))

    body.append(insert_rows(
        "purchase_requisition_items",
        ["id", "purchase_requisition_id", "line_number", "material_id", "quantity", "unit_of_measure_id",
         "estimated_unit_price", "created_at", "updated_at"],
        [[str(i["id"]), str(r["id"]), str(i["line_number"]), str(i["material_id"]), qty3(i["quantity"]),
          str(i["unit_id"]), num(i["price"]), ts(r["created_at"]), ts(r["created_at"])]
         for r in requisitions for i in r["items"]]))

    body.append(insert_rows(
        "approvals",
        ["id", "purchase_requisition_id", "step", "required_role", "approval_rule_id", "decided_by",
         "decision", "comment", "amount_evaluated", "decided_at", "created_at"],
        [[str(a["id"]), str(a["purchase_requisition_id"]), str(a["step"]), q(a["required_role"]),
          str(a["approval_rule_id"]), str(a["decided_by"]), q(a["decision"]), opt(a["comment"], q),
          num(a["amount_evaluated"]), ts(a["decided_at"]), ts(a["decided_at"])] for a in approvals]))

    body.append(insert_rows(
        "purchase_orders",
        ["id", "document_number", "supplier_id", "purchase_requisition_id", "plant_id", "buyer_id",
         "order_date", "expected_delivery_date", "payment_terms_days", "status", "created_at", "updated_at"],
        [[str(o["id"]), q(o["number"]), str(o["supplier_id"]), opt(o["requisition_id"]), str(o["plant_id"]),
          str(o["buyer_id"]), dl(o["date"]), dl(o["expected_delivery_date"]), str(o["payment_terms_days"]),
          q(o["status"]), ts(o["created_at"]), ts(o["created_at"])] for o in orders]))

    body.append(insert_rows(
        "purchase_order_items",
        ["id", "purchase_order_id", "line_number", "material_id", "requisition_item_id", "quantity",
         "unit_of_measure_id", "unit_price", "created_at", "updated_at"],
        [[str(i["id"]), str(o["id"]), str(i["line_number"]), str(i["material_id"]),
          opt(i["requisition_item_id"]), qty3(i["quantity"]), str(i["unit_id"]), num(i["unit_price"]),
          ts(o["created_at"]), ts(o["created_at"])] for o in orders for i in o["items"]]))

    out = [
        "-- 02_purchasing.sql\n"
        "-- GENERATED by tools/seed/generate_purchasing.py (random.Random(43)). Do not edit by hand.\n"
        "-- Requisitions, approvals and purchase orders. Requires V1 to V7 and 01_master_data.sql.\n"
        "-- Fictional data for a portfolio project. Any resemblance to real companies is coincidental.\n\n",
        "SET client_encoding = 'UTF8';\n\n",
        "BEGIN;\n\n",
        DISABLE_AUDIT_SQL,
        "\n".join(body),
        "\n-- Document numbers were issued explicitly: move the counters to the highest number of each year.\n",
    ]
    for (prefix, year), last in sorted(numbers.last.items()):
        out.append(f"UPDATE number_ranges SET last_value = {last}\n"
                   f" WHERE document_type = '{DOC_TYPES[prefix]}' AND fiscal_year = {year};\n")
    out.append("\n-- Sequences continue after the explicit ids.\n")
    for table in ["purchase_requisitions", "purchase_requisition_items", "approvals",
                  "purchase_orders", "purchase_order_items"]:
        out.append(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), (SELECT max(id) FROM {table}));\n")
    out.append("\n" + ENABLE_AUDIT_SQL + "COMMIT;\n")
    return "".join(out)


# ---------------------------------------------------------------------------
# checks and report
# ---------------------------------------------------------------------------
def check(requisitions, approvals, orders, anomaly_numbers, last_days):
    approval_by_req = {a["purchase_requisition_id"]: a for a in approvals}
    assert len(approval_by_req) == len(approvals), "more than one decision per requisition"
    for r in requisitions:
        assert 1 <= len(r["items"]) <= MAX_ITEMS_PER_REQUISITION
        decided = r["status"] in ("approved", "rejected")
        assert decided == (r["id"] in approval_by_req)
        if decided:
            a = approval_by_req[r["id"]]
            assert a["amount_evaluated"] == r["amount"] and a["decided_at"] > r["created_at"]
        if r["status"] == "pending_approval":
            assert r["date"] in last_days
    approved = {r["id"] for r in requisitions if r["status"] == "approved"}
    for o in orders:
        if o["requisition_id"] is not None:
            assert o["requisition_id"] in approved
        assert o["expected_delivery_date"] >= o["date"] and o["date"] <= END
        assert o["created_at"].date() <= END
    by_number = {o["number"]: o for o in orders}
    for number in anomaly_numbers:
        o = by_number[number]
        assert o["requisition_id"] is None and o["total"] > REJECT_THRESHOLD


def print_report(master, requisitions, approvals, orders, anomaly_numbers):
    category_of = {m["id"]: m["category_code"] for m in master["materials"]}
    category_name = dict(CATEGORIES)

    print(f"\nRequisições: {len(requisitions)} ({sum(len(r['items']) for r in requisitions)} itens), "
          f"aprovações: {len(approvals)}")
    by_status = Counter(r["status"] for r in requisitions)
    for status in ("approved", "rejected", "pending_approval", "cancelled"):
        print(f"  {status}: {by_status[status]}")

    print(f"\nPedidos: {len(orders)} ({sum(len(o['items']) for o in orders)} itens)")
    by_month = Counter(f"{o['date']:%Y-%m}" for o in orders)
    for month in sorted(by_month):
        print(f"  {month}: {by_month[month]}")

    spend = defaultdict(lambda: Decimal("0.00"))
    for o in orders:
        for i in o["items"]:
            spend[category_of[i["material_id"]]] += i["total"]
    print(f"\nGasto total: {brl(sum(spend.values(), Decimal('0.00')))}")
    print("Gasto por categoria:")
    for code, _ in CATEGORIES:
        print(f"  {code} ({category_name[code]}): {brl(spend[code])}")

    print(f"\nAnomalias plantadas: {len(anomaly_numbers)} (pedido sem aprovação)")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    rng = random.Random(SEED)
    master = build_master()

    # each line must respect the minimum order quantity of both suppliers of its category
    min_qty = defaultdict(int)
    for sm in master["supplier_materials"]:
        min_qty[sm["material_id"]] = max(min_qty[sm["material_id"]], int(sm["min_order_qty"]))

    numbers = Numbers()
    lines = build_lines(rng, master["materials"], min_qty)
    requisitions, approvals = build_requisitions(rng, master, lines, numbers)
    orders, anomaly_numbers = build_orders(rng, master, requisitions, numbers)
    check(requisitions, approvals, orders, anomaly_numbers, set(BDAYS[-3:]))

    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_sql(requisitions, approvals, orders, numbers))
    with open(MANIFEST_CSV, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(["type", "table", "document_number"])
        for number in sorted(anomaly_numbers):
            writer.writerow(["order_without_approval", "purchase_orders", number])

    print(f"Wrote {OUTPUT_SQL}")
    print(f"Wrote {MANIFEST_CSV}")
    print_report(master, requisitions, approvals, orders, anomaly_numbers)


if __name__ == "__main__":
    main()