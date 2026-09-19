#!/usr/bin/env python3
"""Generates db/seed/03_receipts.sql: fictional goods receipts and the resulting purchase order status.

Part B2a of the seed. Standard library only, fixed seed (44), Decimal for quantities, so the output
is reproducible. Reuses build_purchasing() of generate_purchasing.py (same orders as 02_purchasing.sql,
which is not touched), so 01_master_data.sql and 02_purchasing.sql must be loaded first.
Also adds the partial_delivery and reversed_receipt rows to db/seed/anomalies_manifest.csv.

Cut-off date: 2026-09-18. Every receipt date is a business day (Monday to Friday; holidays are not
modelled). Orders whose simulated delivery falls after the cut-off have no receipt (status issued).

Usage (from anywhere):  python tools/seed/generate_receipts.py
"""
import random
import sys
from collections import Counter, defaultdict
from datetime import timedelta
from decimal import Decimal

from generate_purchasing import (
    END, END_DT, Numbers, after, build_purchasing, dl, insert_rows, opt, qty3, random_time, ts,
    update_manifest,
)
from generate_seed import DISABLE_AUDIT_SQL, ENABLE_AUDIT_SQL, ROOT, q

OUTPUT_SQL = ROOT / "db" / "seed" / "03_receipts.sql"

SEED = 44

# ---- delivery simulation ----
ON_TIME_JITTER_BD = 1          # on time: expected date +-1 business day
LATE_MIN_BD, LATE_MAX_BD = 2, 10
PARTIAL_RATE = 0.04            # share of delivered orders with a partial first delivery
PARTIAL_MAX_ITEMS = 2
PARTIAL_MIN_PCT, PARTIAL_MAX_PCT = 40, 80
PARTIAL_SECOND_MIN_BD, PARTIAL_SECOND_MAX_BD = 3, 12
REVERSAL_COUNT = 6
REVERSAL_LAST_DAYS = 6         # only receipts before the last 6 business days are reversed

REVERSAL_REASONS = [
    "Recebimento lançado por engano antes da conferência física",
    "Lançado no pedido errado; refeito no pedido correto",
    "Conferência incompleta no lançamento; refeito após nova contagem",
    "Lançamento feito pelo usuário errado; refeito pelo responsável do recebimento",
    "Divergência de conferência com a nota de entrega; lançamento refeito",
]


# ---------------------------------------------------------------------------
# calendar helpers (not capped at the cut-off: deliveries may fall after it)
# ---------------------------------------------------------------------------
def snap(d):
    """d itself when it is a business day, otherwise the next one."""
    while d.weekday() >= 5:
        d += timedelta(days=1)
    return d


def shift(d, n):
    """The business day n days after (or before, when negative) d."""
    d = snap(d)
    step = 1 if n >= 0 else -1
    for _ in range(abs(n)):
        d += timedelta(days=step)
        while d.weekday() >= 5:
            d += timedelta(days=step)
    return d


# ---------------------------------------------------------------------------
# 1-3. receipts
# ---------------------------------------------------------------------------
def build_receipts(rng, master, orders):
    suppliers = {s["id"]: s for s in master["suppliers"]}
    plant_code = {pid: code for code, pid in master["plant_ids"].items()}
    requesters = defaultdict(list)
    for u in master["users"]:
        if u["role"] == "requester":
            requesters[u["plant_id"]].append(u["id"])
    assert all(requesters[pid] for pid in plant_code), "a plant has no requester"

    used_notes = set()

    def delivery_note():
        while True:
            number = f"NE{rng.randint(100000, 999999)}"
            if number not in used_notes:
                used_notes.add(number)
                return number

    receipts = []

    def add(order, receipt_date, items, note, tag, notes=None):
        receipts.append({
            "seq": len(receipts), "order": order, "plant_id": order["plant_id"], "date": receipt_date,
            "received_by": rng.choice(requesters[order["plant_id"]]), "delivery_note": note,
            "status": "posted", "reversed_by": None, "reversed_at": None, "reversal_reason": None,
            "notes": notes, "created_at": random_time(rng, receipt_date), "items": items, "tag": tag,
        })
        return receipts[-1]

    deliveries = []  # (order, first receipt date, on_time flag) of every delivered order
    partial_seqs = []
    for o in orders:
        supplier = suppliers[o["supplier_id"]]
        late = rng.random() < float(1 - supplier["on_time_rate"])
        expected = snap(o["expected_delivery_date"])
        if late:
            d = shift(expected, rng.randint(LATE_MIN_BD, LATE_MAX_BD))
        else:
            d = shift(expected, rng.randint(-ON_TIME_JITTER_BD, ON_TIME_JITTER_BD))
        d = max(d, shift(o["date"], 1))
        partial = rng.random() < PARTIAL_RATE
        if d > END:
            continue
        deliveries.append((o, d, not late))

        full = [(i, i["quantity"]) for i in o["items"]]
        candidates = [i for i in o["items"] if i["quantity"] >= 2]
        if not (partial and candidates):
            add(o, d, full, delivery_note(), "single")
            continue

        chosen = {i["id"] for i in rng.sample(candidates, min(rng.randint(1, PARTIAL_MAX_ITEMS), len(candidates)))}
        first, rest = [], []
        for i in o["items"]:
            if i["id"] in chosen:
                pct = rng.randint(PARTIAL_MIN_PCT, PARTIAL_MAX_PCT)
                part = min(max(1, i["quantity"] * pct // 100), i["quantity"] - 1)
                first.append((i, part))
                rest.append((i, i["quantity"] - part))
            else:
                first.append((i, i["quantity"]))
        second_date = shift(d, rng.randint(PARTIAL_SECOND_MIN_BD, PARTIAL_SECOND_MAX_BD))
        r = add(o, d, first, delivery_note(), "partial_first",
                "Entrega parcial: saldo a ser entregue posteriormente.")
        partial_seqs.append(r["seq"])
        if second_date <= END:
            add(o, second_date, rest, delivery_note(), "partial_second", "Entrega complementar do pedido.")

    # reversals: a wrong receipt is reversed and posted again for the same items
    eligible = [r for r in receipts if r["tag"] == "single" and r["date"] <= shift(END, -REVERSAL_LAST_DAYS)]
    reversed_seqs = []
    for r in rng.sample(eligible, REVERSAL_COUNT):
        reversal_day = shift(r["date"], rng.randint(0, 1))
        reversed_at = min(after(rng, random_time(rng, reversal_day), r["created_at"]), END_DT)
        r.update(status="reversed", reversed_by=rng.choice(requesters[r["plant_id"]]), reversed_at=reversed_at,
                 reversal_reason=rng.choice(REVERSAL_REASONS), tag="reversed")
        reversed_seqs.append(r["seq"])
        fix = add(r["order"], r["date"], list(r["items"]), r["delivery_note"], "correction")
        fix["created_at"] = min(after(rng, random_time(rng, reversal_day), reversed_at), END_DT)
        fix["reverses_seq"] = r["seq"]

    return receipts, deliveries, partial_seqs, reversed_seqs


# ---------------------------------------------------------------------------
# 4. ids and numbers
# ---------------------------------------------------------------------------
def number_receipts(receipts):
    numbers = Numbers()
    receipts.sort(key=lambda r: (r["date"], r["created_at"], r["seq"]))
    item_id = 0
    for gid, r in enumerate(receipts, start=1):
        r["id"] = gid
        r["number"] = numbers.next("GR", r["date"])
        r["updated_at"] = r["reversed_at"] or r["created_at"]
        for line_number, (item, quantity) in enumerate(r["items"], start=1):
            item_id += 1
            r.setdefault("lines", []).append({
                "id": item_id, "line_number": line_number, "purchase_order_item_id": item["id"],
                "quantity": quantity,
            })
    by_seq = {r["seq"]: r for r in receipts}
    for r in receipts:
        if r["tag"] == "correction":
            r["notes"] = f"Lançamento refeito após o estorno do {by_seq[r['reverses_seq']]['number']}."
    return numbers


# ---------------------------------------------------------------------------
# 5. purchase order status
# ---------------------------------------------------------------------------
def order_statuses(orders, receipts):
    """Returns {order id: (status, updated_at)} for the orders that have at least one receipt, and
    the status of every order (issued when nothing was received)."""
    posted = defaultdict(lambda: defaultdict(Decimal))  # order id -> item id -> quantity
    last_at = {}
    for r in receipts:
        oid = r["order"]["id"]
        last_at[oid] = max(last_at.get(oid, r["created_at"]), r["created_at"], r["updated_at"])
        if r["status"] == "posted":
            for item, quantity in r["items"]:
                posted[oid][item["id"]] += quantity

    status, updates = {}, {}
    for o in orders:
        got = posted[o["id"]]
        for i in o["items"]:
            assert got[i["id"]] <= i["quantity"], f"over-received item {i['id']}"
        if all(got[i["id"]] >= i["quantity"] for i in o["items"]):
            status[o["id"]] = "received"
        elif any(got[i["id"]] > 0 for i in o["items"]):
            status[o["id"]] = "partially_received"
        else:
            status[o["id"]] = "issued"
        if o["id"] in last_at:
            updates[o["id"]] = (status[o["id"]], last_at[o["id"]])
    return status, updates


# ---------------------------------------------------------------------------
# SQL output
# ---------------------------------------------------------------------------
def render_sql(receipts, updates, numbers):
    body = []
    body.append(insert_rows(
        "goods_receipts",
        ["id", "document_number", "purchase_order_id", "plant_id", "received_by", "receipt_date",
         "delivery_note_number", "status", "reversed_by", "reversed_at", "reversal_reason", "notes",
         "created_at", "updated_at"],
        [[str(r["id"]), q(r["number"]), str(r["order"]["id"]), str(r["plant_id"]), str(r["received_by"]),
          dl(r["date"]), q(r["delivery_note"]), q(r["status"]), opt(r["reversed_by"]),
          opt(r["reversed_at"], ts), opt(r["reversal_reason"], q), opt(r["notes"], q),
          ts(r["created_at"]), ts(r["updated_at"])] for r in receipts]))
    body.append(insert_rows(
        "goods_receipt_items",
        ["id", "goods_receipt_id", "line_number", "purchase_order_item_id", "quantity_received",
         "created_at", "updated_at"],
        [[str(l["id"]), str(r["id"]), str(l["line_number"]), str(l["purchase_order_item_id"]),
          qty3(l["quantity"]), ts(r["created_at"]), ts(r["created_at"])]
         for r in receipts for l in r["lines"]]))

    rows = [f"    ({oid}, {q(status)}, {ts(at)})" for oid, (status, at) in sorted(updates.items())]
    updates_sql = []
    for start in range(0, len(rows), 500):
        updates_sql.append(
            "UPDATE purchase_orders AS po\n"
            "   SET status = v.status, updated_at = v.updated_at::timestamptz\n"
            "  FROM (VALUES\n" + ",\n".join(rows[start:start + 500]) + "\n"
            ") AS v (id, status, updated_at)\n"
            " WHERE po.id = v.id;\n")

    out = [
        "-- 03_receipts.sql\n"
        "-- GENERATED by tools/seed/generate_receipts.py (random.Random(44)). Do not edit by hand.\n"
        "-- Goods receipts and the status of the purchase orders. Requires V1 to V7, 01_master_data.sql\n"
        "-- and 02_purchasing.sql. Cut-off date: 2026-09-18.\n"
        "-- Fictional data for a portfolio project. Any resemblance to real companies is coincidental.\n\n",
        "SET client_encoding = 'UTF8';\n\n",
        "BEGIN;\n\n",
        DISABLE_AUDIT_SQL,
        "\n".join(body),
        "\n-- Order status follows the posted receipts. The updated_at trigger is disabled only for these\n"
        "-- UPDATEs, so updated_at can carry the date of the last receipt.\n",
        "ALTER TABLE purchase_orders DISABLE TRIGGER trg_purchase_orders_set_updated_at;\n\n",
        "\n".join(updates_sql),
        "\nALTER TABLE purchase_orders ENABLE TRIGGER trg_purchase_orders_set_updated_at;\n",
        "\n-- Document numbers were issued explicitly: move the counters to the highest number of each year.\n",
    ]
    for (prefix, year), last in sorted(numbers.last.items()):
        out.append(f"UPDATE number_ranges SET last_value = {last}\n"
                   f" WHERE document_type = 'goods_receipt' AND fiscal_year = {year};\n")
    out.append("\n-- Sequences continue after the explicit ids.\n")
    for table in ["goods_receipts", "goods_receipt_items"]:
        out.append(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), (SELECT max(id) FROM {table}));\n")
    out.append("\n" + ENABLE_AUDIT_SQL + "COMMIT;\n")
    return "".join(out)


# ---------------------------------------------------------------------------
# checks and report
# ---------------------------------------------------------------------------
def check(receipts, reversed_seqs):
    for r in receipts:
        assert r["date"].weekday() < 5 and r["date"] <= END and r["date"] >= r["order"]["date"]
        assert r["created_at"].date() <= END
        assert r["lines"] and all(l["quantity"] > 0 for l in r["lines"])
        assert (r["status"] == "reversed") == (r["reversed_at"] is not None)
    assert len({r["number"] for r in receipts}) == len(receipts)
    assert len(reversed_seqs) == REVERSAL_COUNT
    for r in receipts:
        if r["tag"] == "correction":
            original = next(x for x in receipts if x["seq"] == r["reverses_seq"])
            assert original["status"] == "reversed" and original["order"] is r["order"]
            assert [(i["id"], qn) for i, qn in original["items"]] == [(i["id"], qn) for i, qn in r["items"]]
            assert r["created_at"] > original["reversed_at"]


def print_report(master, orders, receipts, statuses, deliveries, partial_seqs, reversed_seqs):
    print(f"\nRecebimentos: {len(receipts)} ({sum(len(r['lines']) for r in receipts)} itens)")
    by_status = Counter(r["status"] for r in receipts)
    for status in ("posted", "reversed"):
        print(f"  {status}: {by_status[status]}")

    print(f"\nPedidos: {len(orders)}")
    by_order = Counter(statuses.values())
    for status in ("received", "partially_received", "issued"):
        print(f"  {status}: {by_order[status]}")

    print("\nEntregas no prazo por fornecedor (pedidos entregues até 2026-09-18; alvo = on_time_rate do CSV):")
    per_supplier = defaultdict(lambda: [0, 0])
    for o, _, on_time in deliveries:
        per_supplier[o["supplier_id"]][0] += 1
        per_supplier[o["supplier_id"]][1] += on_time
    for s in master["suppliers"]:
        total, on_time = per_supplier[s["id"]]
        share = 100 * on_time / total if total else 0
        print(f"  {s['code']} {s['name']} ({s['price_tier']}): {share:5.1f}% de {total} "
              f"(alvo {100 * s['on_time_rate']:.0f}%)")

    print(f"\nEntregas parciais: {len(partial_seqs)}")
    print(f"Estornos: {len(reversed_seqs)}")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    master, _, _, orders, _, _ = build_purchasing()
    rng = random.Random(SEED)
    receipts, deliveries, partial_seqs, reversed_seqs = build_receipts(rng, master, orders)
    numbers = number_receipts(receipts)
    statuses, updates = order_statuses(orders, receipts)
    check(receipts, reversed_seqs)

    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_sql(receipts, updates, numbers))

    by_seq = {r["seq"]: r["number"] for r in receipts}
    update_manifest(
        {"partial_delivery", "reversed_receipt"},
        [("partial_delivery", "goods_receipts", by_seq[s]) for s in sorted(partial_seqs)]
        + [("reversed_receipt", "goods_receipts", by_seq[s]) for s in sorted(reversed_seqs)])

    print(f"Wrote {OUTPUT_SQL}")
    print_report(master, orders, receipts, statuses, deliveries, partial_seqs, reversed_seqs)


if __name__ == "__main__":
    main()