#!/usr/bin/env python3
"""Generates db/seed/04_invoices_payments.sql: fictional supplier invoices, payments and their anomalies.

Part B2b of the seed. Standard library only, fixed seed (45), Decimal for money and quantities, so the
output is reproducible. Reuses build_purchasing() and the receipts of generate_receipts.py (same orders
and receipts as 02_purchasing.sql and 03_receipts.sql, which are not touched), so 01, 02 and 03 must be
loaded first. Also adds the invoice and payment anomaly types to db/seed/anomalies_manifest.csv.

Cut-off date: 2026-09-18. Every date the simulation chooses is a business day (Monday to Friday;
holidays are not modelled), except due_date, which is invoice_date + the supplier payment terms.

Late payment: a payment is late when its scheduled date is after the due date, moved to the next business
day when the due date falls on a weekend (a weekend due date is payable on the next business day).

Usage (from anywhere):  python tools/seed/generate_invoices.py
"""
import random
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from decimal import Decimal, ROUND_HALF_UP

from generate_purchasing import (
    BDAYS, END, END_DT, Numbers, after, brl, dl, insert_rows, money, opt, qty3, random_time, ts,
    update_manifest, build_purchasing, num,
)
from generate_receipts import (
    SEED as RECEIPTS_SEED, build_receipts, number_receipts, order_statuses, shift, snap,
)
from generate_seed import DISABLE_AUDIT_SQL, ENABLE_AUDIT_SQL, ROOT, q

OUTPUT_SQL = ROOT / "db" / "seed" / "04_invoices_payments.sql"

SEED = 45
CENT4 = Decimal("0.0001")
THOUSANDTH = Decimal("0.001")

# ---- invoice dates ----
INVOICE_MIN_BD, INVOICE_MAX_BD = 1, 8      # after the last posted receipt
POSTING_MAX_BD = 2                          # posting_date = invoice_date + 0 to 2 business days
NUMBER_START_MIN, NUMBER_START_MAX = 1000, 60000
NUMBER_JUMP_MIN, NUMBER_JUMP_MAX = 1, 40

# ---- kinds of invoice (one draw per invoice, exclusive) ----
BEFORE_RATE = 0.02    # only single-delivery orders
PRICE_RATE = 0.05
QTY_RATE = 0.015
NOISE_RATE = 0.06

# thresholds of the exclusive kinds (cumulative)
PRICE_RATE_LOW = BEFORE_RATE
PRICE_RATE_HIGH = BEFORE_RATE + PRICE_RATE
QTY_RATE_HIGH = PRICE_RATE_HIGH + QTY_RATE
NOISE_RATE_HIGH = QTY_RATE_HIGH + NOISE_RATE

NOISE_BPS = (30, 178)                       # +0.30% to +1.78% (must stay within 0.3% to 1.8%)
PRICE_LOW_BPS, PRICE_HIGH_BPS = (250, 490), (510, 1190)
PRICE_LOW_SHARE = 0.60
PRICE_MAX_ITEMS = 3
QTY_BPS = (600, 1900)                       # +6% to +19% (must stay within 6% to 20%)
BEFORE_MIN_BD, BEFORE_MAX_BD = 1, 3         # invoice date before the receipt
RELEASE_MIN_BD, RELEASE_MAX_BD = 1, 2       # release after the receipt
RESOLVE_MIN_BD, RESOLVE_MAX_BD = 2, 12      # resolution after the posting date
STALE_COUNT = 6
STALE_MIN_AGE_DAYS = 45
DUPLICATE_COUNT, DUPLICATE_PAID, DUPLICATE_BLOCKED, DUPLICATE_CANCELLED = 8, 2, 3, 3
DUPLICATE_MIN_BD, DUPLICATE_MAX_BD = 1, 5
APPROVAL_MIN_BD, APPROVAL_MAX_BD = 1, 3
NEW_INVOICE_DAYS = 3                        # postings in the last 3 business days are still received/matched
NEW_RECEIVED_SHARE = 0.60
LATE_DELAY_RATE = 0.03
LATE_DELAY_MIN_BD, LATE_DELAY_MAX_BD = 1, 4
PAYMENT_METHODS = ["pix", "boleto", "bank_transfer"]
PAYMENT_WEIGHTS = [50, 35, 15]
REFERENCE_PREFIX = {"pix": "PIX", "boleto": "BOL", "bank_transfer": "TED"}
REFERENCE_CHARS = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"

REASON_PRICE = "Preço da fatura acima do pedido (tolerância de 2%)"
REASON_QTY = "Quantidade faturada maior que a recebida (tolerância de 5%)"
REASON_BEFORE = "Fatura recebida antes do recebimento da mercadoria"
REASON_DUPLICATE = "Possível fatura duplicada"
NOTE_RESOLVED = "Diferença aceita após conferência com o fornecedor"
NOTE_CANCELLED = "Cancelada: duplicidade confirmada"


# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------
def at_ten(d):
    return datetime(d.year, d.month, d.day, 10, 0, 0)


def raise_price(rng, price, bps_range):
    """price raised by a percentage drawn in bps_range (basis points), never below the range start."""
    low, high = bps_range
    bps = rng.randint(low, high)
    new = (price * (10000 + bps) / 10000).quantize(CENT4, ROUND_HALF_UP)
    while (new - price) * 10000 < price * low:
        new += CENT4
    return new


def raise_quantity(rng, quantity):
    low, high = QTY_BPS
    bps = rng.randint(low, high)
    new = (quantity * (10000 + bps) / 10000).quantize(THOUSANDTH, ROUND_HALF_UP)
    while (new - quantity) * 10000 < quantity * low:
        new += THOUSANDTH
    return new


def pct_bps(new, old):
    return (new - old) / old * 10000


# ---------------------------------------------------------------------------
# 1. base invoices, one per fully received order
# ---------------------------------------------------------------------------
def received_orders(orders, receipts, statuses):
    """[(order, last posted receipt date, quantities received per item id, single delivery flag)]."""
    posted = defaultdict(list)
    for r in receipts:
        if r["status"] == "posted":
            posted[r["order"]["id"]].append(r)
    result = []
    for o in orders:
        if statuses[o["id"]] != "received":
            continue
        rs = posted[o["id"]]
        got = defaultdict(Decimal)
        for r in rs:
            for item, quantity in r["items"]:
                got[item["id"]] += quantity
        result.append((o, max(r["date"] for r in rs), got, len(rs) == 1))
    return result


def new_invoice(seq, order, kind, invoice_date, posting_date, got, price_of, qty_of):
    items = []
    for line_number, item in enumerate(order["items"], start=1):
        quantity = qty_of.get(item["id"], Decimal(got[item["id"]]))
        price = price_of.get(item["id"], item["unit_price"])
        items.append({"line_number": line_number, "purchase_order_item_id": item["id"],
                      "quantity": quantity, "price": price, "total": money(quantity, price)})
    return {
        "seq": seq, "order": order, "supplier_id": order["supplier_id"], "kind": kind,
        "invoice_date": invoice_date,
        "due_date": invoice_date + timedelta(days=order["payment_terms_days"]),
        "posting_date": posting_date, "items": items,
        "gross": sum((i["total"] for i in items), Decimal("0.00")),
        "status": "received", "block_reason": None, "approved_by": None, "approved_at": None,
        "notes": None, "payment": None, "tags": [], "stale": False, "original_seq": None,
    }


def build_invoices(rng, master, orders, receipts, statuses):
    users = master["users"]
    finance = [u["id"] for u in users if u["role"] == "finance"]
    approvers = [u["id"] for u in users if u["role"] == "approver"]
    managers = [u["id"] for u in users if u["role"] == "manager"]
    assert finance and approvers and managers, "a required role has no user"

    last_days = set(BDAYS[-NEW_INVOICE_DAYS:])
    invoices = []

    def add(order, kind, invoice_date, posting_date, got, price_of=None, qty_of=None):
        inv = new_invoice(len(invoices), order, kind, invoice_date, posting_date, got,
                          price_of or {}, qty_of or {})
        inv["created_at"] = random_time(rng, posting_date)
        invoices.append(inv)
        return inv

    # -- 1-6. what each invoice looks like when it arrives --
    for order, last_receipt, got, single in received_orders(orders, receipts, statuses):
        normal_date = shift(last_receipt, rng.randint(INVOICE_MIN_BD, INVOICE_MAX_BD))
        u = rng.random()
        if u < BEFORE_RATE and single and shift(last_receipt, -1) >= order["date"]:
            invoice_date = max(shift(last_receipt, -rng.randint(BEFORE_MIN_BD, BEFORE_MAX_BD)), order["date"])
            posting_date = min(shift(invoice_date, rng.randint(0, POSTING_MAX_BD)), shift(last_receipt, -1))
            inv = add(order, "before_receipt", invoice_date, posting_date, got)
            inv["receipt_date"] = last_receipt
            continue
        if normal_date > END:
            continue
        posting_date = min(shift(normal_date, rng.randint(0, POSTING_MAX_BD)), END)
        if PRICE_RATE_LOW <= u < PRICE_RATE_HIGH:
            kind = "price"
        elif PRICE_RATE_HIGH <= u < QTY_RATE_HIGH:
            kind = "quantity"
        elif QTY_RATE_HIGH <= u < NOISE_RATE_HIGH:
            kind = "noise"
        else:
            kind = "normal"

        price_of, qty_of = {}, {}
        if kind == "noise":
            item = rng.choice(order["items"])
            price_of[item["id"]] = raise_price(rng, item["unit_price"], NOISE_BPS)
        elif kind == "price":
            band = PRICE_LOW_BPS if rng.random() < PRICE_LOW_SHARE else PRICE_HIGH_BPS
            chosen = rng.sample(order["items"], min(rng.randint(1, PRICE_MAX_ITEMS), len(order["items"])))
            for item in chosen:
                price_of[item["id"]] = raise_price(rng, item["unit_price"], band)
        elif kind == "quantity":
            item = rng.choice(order["items"])
            qty_of[item["id"]] = raise_quantity(rng, Decimal(got[item["id"]]))
        inv = add(order, kind, normal_date, posting_date, got, price_of, qty_of)
        if kind == "price":
            inv["price_band_high"] = max(pct_bps(price_of[i["id"]], i["unit_price"])
                                         for i in order["items"] if i["id"] in price_of) > 500

    # supplier invoice numbers: increasing per supplier over time
    by_supplier = defaultdict(list)
    for inv in invoices:
        by_supplier[inv["supplier_id"]].append(inv)
    for supplier_id in sorted(by_supplier):
        number = rng.randint(NUMBER_START_MIN, NUMBER_START_MAX)
        for inv in sorted(by_supplier[supplier_id], key=lambda i: (i["invoice_date"], i["order"]["id"])):
            number += rng.randint(NUMBER_JUMP_MIN, NUMBER_JUMP_MAX)
            inv["number"] = f"NF-{number:06d}"
        assert number < 100000, "supplier invoice number ran past 6 digits of headroom"

    # -- 7. stale blocked divergences: old, never resolved --
    old_divergences = [i for i in invoices if i["kind"] in ("price", "quantity")
                       and (END - i["posting_date"]).days > STALE_MIN_AGE_DAYS]
    for inv in rng.sample(old_divergences, STALE_COUNT):
        inv["stale"] = True

    # -- 3-7, 9-10. status flow and payments of every invoice --
    def pay(inv, approval_date, approver_id, delay=0):
        """Approves the invoice on approval_date and schedules or pays it."""
        approved_at = after(rng, random_time(rng, approval_date), inv["created_at"])
        inv.update(status="approved", approved_by=approver_id, approved_at=approved_at)
        scheduled = snap(max(inv["due_date"], shift(approval_date, 1)))
        if delay:
            scheduled = shift(scheduled, delay)
        method = rng.choices(PAYMENT_METHODS, PAYMENT_WEIGHTS)[0]
        payment = {
            "invoice": inv, "amount": inv["gross"], "scheduled_for": scheduled, "method": method,
            "created_by": rng.choice(finance),
            "reference": REFERENCE_PREFIX[method] + "-" + "".join(rng.choice(REFERENCE_CHARS) for _ in range(8)),
            "created_at": min(after(rng, random_time(rng, approval_date), approved_at), END_DT),
            "late": scheduled > snap(inv["due_date"]),
        }
        if scheduled <= END:
            payment.update(status="paid", paid_at=at_ten(scheduled))
            inv["status"] = "paid"
            inv["updated_at"] = payment["paid_at"]
        else:
            payment.update(status="scheduled", paid_at=None)
            inv["updated_at"] = payment["created_at"]
        inv["payment"] = payment

    def resolve(inv, block_reason, date, approver_id, note=None):
        inv.update(status="blocked", block_reason=block_reason, updated_at=inv["created_at"])
        if date <= END:
            pay(inv, date, approver_id)
            inv["notes"] = note

    for inv in invoices:
        inv["updated_at"] = inv["created_at"]
        kind = inv["kind"]
        if kind == "before_receipt":
            release = shift(inv["receipt_date"], rng.randint(RELEASE_MIN_BD, RELEASE_MAX_BD))
            resolve(inv, REASON_BEFORE, release, rng.choice(finance))
        elif kind in ("price", "quantity"):
            resolution = shift(inv["posting_date"], rng.randint(RESOLVE_MIN_BD, RESOLVE_MAX_BD))
            high = kind == "quantity" or inv["price_band_high"]
            approver = rng.choice(managers if high else approvers)
            reason = REASON_PRICE if kind == "price" else REASON_QTY
            if inv["stale"]:
                inv.update(status="blocked", block_reason=reason)
            else:
                resolve(inv, reason, resolution, approver, NOTE_RESOLVED)
        elif inv["posting_date"] in last_days:
            inv["status"] = "received" if rng.random() < NEW_RECEIVED_SHARE else "matched"
        else:
            delay = rng.randint(LATE_DELAY_MIN_BD, LATE_DELAY_MAX_BD) if rng.random() < LATE_DELAY_RATE else 0
            pay(inv, shift(inv["posting_date"], rng.randint(APPROVAL_MIN_BD, APPROVAL_MAX_BD)),
                rng.choice(finance), delay)

    # -- 8. near-duplicates of normal invoices already issued --
    taken = {(i["supplier_id"], i["number"]) for i in invoices}
    last_ok = shift(END, -DUPLICATE_MAX_BD)
    pool = [i for i in invoices if i["kind"] == "normal" and i["posting_date"] <= last_ok]
    paid_pool = [i for i in pool if i["status"] == "paid"]
    destinations = (["paid"] * DUPLICATE_PAID + ["blocked"] * DUPLICATE_BLOCKED
                    + ["cancelled"] * DUPLICATE_CANCELLED)
    used = set()
    for destination in destinations:
        candidates = [i for i in (paid_pool if destination == "paid" else pool) if i["seq"] not in used]
        rng.shuffle(candidates)
        for original in candidates:
            posting = shift(original["posting_date"], rng.randint(DUPLICATE_MIN_BD, DUPLICATE_MAX_BD))
            digits = original["number"][3:]
            variants = [v for v in (f"NF {digits}", f"NF{digits}", f"NF-{int(digits)}")
                        if (original["supplier_id"], v) not in taken]
            number = rng.choice(variants)
            approval = shift(posting, rng.randint(APPROVAL_MIN_BD, APPROVAL_MAX_BD))
            if destination == "paid" and approval > END:
                continue
            dup = add(original["order"], "duplicate", original["invoice_date"], posting, defaultdict(Decimal))
            dup.update(number=number, original_seq=original["seq"], tags=["duplicate_invoice"],
                       items=[dict(i) for i in original["items"]], gross=original["gross"],
                       due_date=original["due_date"], updated_at=dup["created_at"])
            if destination == "paid":
                pay(dup, approval, rng.choice(finance))
                if dup["status"] != "paid":       # would be scheduled, not paid: try another original
                    invoices.pop()
                    continue
                dup["tags"].append("duplicate_payment")
            elif destination == "blocked":
                dup.update(status="blocked", block_reason=REASON_DUPLICATE)
            else:
                dup.update(status="cancelled", notes=NOTE_CANCELLED)
            taken.add((dup["supplier_id"], number))
            used.add(original["seq"])
            break
        else:
            raise RuntimeError(f"no original found for a {destination} duplicate")
    return invoices


# ---------------------------------------------------------------------------
# 12. ids and numbers
# ---------------------------------------------------------------------------
def number_documents(invoices):
    inv_numbers, pay_numbers = Numbers(), Numbers()
    invoices.sort(key=lambda i: (i["posting_date"], i["created_at"], i["seq"]))
    item_id = 0
    for gid, inv in enumerate(invoices, start=1):
        inv["id"] = gid
        inv["document_number"] = inv_numbers.next("IR", inv["posting_date"])
        for item in inv["items"]:
            item_id += 1
            item["id"] = item_id
    payments = sorted((i["payment"] for i in invoices if i["payment"]),
                      key=lambda p: (p["created_at"], p["invoice"]["seq"]))
    for pid, p in enumerate(payments, start=1):
        p["id"] = pid
        p["document_number"] = pay_numbers.next("PY", p["created_at"].date())
    return payments, inv_numbers, pay_numbers


# ---------------------------------------------------------------------------
# SQL output
# ---------------------------------------------------------------------------
def render_sql(invoices, payments, closed, inv_numbers, pay_numbers):
    body = []
    body.append(insert_rows(
        "invoice_receipts",
        ["id", "document_number", "supplier_id", "purchase_order_id", "supplier_invoice_number", "invoice_date",
         "due_date", "posting_date", "currency", "gross_amount", "status", "block_reason", "approved_by",
         "approved_at", "notes", "created_at", "updated_at"],
        [[str(i["id"]), q(i["document_number"]), str(i["supplier_id"]), str(i["order"]["id"]), q(i["number"]),
          dl(i["invoice_date"]), dl(i["due_date"]), dl(i["posting_date"]), "'BRL'", num(i["gross"]),
          q(i["status"]), opt(i["block_reason"], q), opt(i["approved_by"]), opt(i["approved_at"], ts),
          opt(i["notes"], q), ts(i["created_at"]), ts(i["updated_at"])] for i in invoices]))
    body.append(insert_rows(
        "invoice_receipt_items",
        ["id", "invoice_receipt_id", "line_number", "purchase_order_item_id", "quantity_invoiced", "unit_price",
         "created_at", "updated_at"],
        [[str(l["id"]), str(i["id"]), str(l["line_number"]), str(l["purchase_order_item_id"]),
          qty3(l["quantity"]), num(l["price"]), ts(i["created_at"]), ts(i["created_at"])]
         for i in invoices for l in i["items"]]))
    body.append(insert_rows(
        "payments",
        ["id", "document_number", "invoice_receipt_id", "amount", "scheduled_for", "payment_method", "status",
         "paid_at", "created_by", "reference", "created_at", "updated_at"],
        [[str(p["id"]), q(p["document_number"]), str(p["invoice"]["id"]), num(p["amount"]),
          dl(p["scheduled_for"]), q(p["method"]), q(p["status"]), opt(p["paid_at"], ts), str(p["created_by"]),
          q(p["reference"]), ts(p["created_at"]), ts(p["paid_at"] or p["created_at"])] for p in payments]))

    rows = [f"    ({oid}, {ts(at)})" for oid, at in sorted(closed.items())]
    updates_sql = []
    for start in range(0, len(rows), 500):
        updates_sql.append(
            "UPDATE purchase_orders AS po\n"
            "   SET status = 'closed', updated_at = v.updated_at::timestamptz\n"
            "  FROM (VALUES\n" + ",\n".join(rows[start:start + 500]) + "\n"
            ") AS v (id, updated_at)\n"
            " WHERE po.id = v.id;\n")

    out = [
        "-- 04_invoices_payments.sql\n"
        "-- GENERATED by tools/seed/generate_invoices.py (random.Random(45)). Do not edit by hand.\n"
        "-- Supplier invoices, payments and the closing of paid purchase orders. Requires V1 to V7,\n"
        "-- 01_master_data.sql, 02_purchasing.sql and 03_receipts.sql. Cut-off date: 2026-09-18.\n"
        "-- Fictional data for a portfolio project. Any resemblance to real companies is coincidental.\n\n",
        "SET client_encoding = 'UTF8';\n\n",
        "BEGIN;\n\n",
        DISABLE_AUDIT_SQL,
        "\n".join(body),
        "\n-- Orders with a paid invoice are closed. The updated_at trigger is disabled only for these\n"
        "-- UPDATEs, so updated_at can carry the payment date.\n",
        "ALTER TABLE purchase_orders DISABLE TRIGGER trg_purchase_orders_set_updated_at;\n\n",
        "\n".join(updates_sql),
        "\nALTER TABLE purchase_orders ENABLE TRIGGER trg_purchase_orders_set_updated_at;\n",
        "\n-- Document numbers were issued explicitly: move the counters to the highest number of each year.\n",
    ]
    for numbers, doc_type, prefix in ((inv_numbers, "invoice_receipt", "IR"), (pay_numbers, "payment", "PY")):
        for (p, year), last in sorted(numbers.last.items()):
            out.append(f"UPDATE number_ranges SET last_value = {last}\n"
                       f" WHERE document_type = '{doc_type}' AND fiscal_year = {year};\n")
    out.append("\n-- Sequences continue after the explicit ids.\n")
    for table in ["invoice_receipts", "invoice_receipt_items", "payments"]:
        out.append(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), (SELECT max(id) FROM {table}));\n")
    out.append("\n" + ENABLE_AUDIT_SQL + "COMMIT;\n")
    return "".join(out)


# ---------------------------------------------------------------------------
# checks and report
# ---------------------------------------------------------------------------
def check(invoices, payments, receipts, master):
    receipts_by_order = defaultdict(list)
    for r in receipts:
        if r["status"] == "posted":
            receipts_by_order[r["order"]["id"]].append(r)
    finance = {u["id"] for u in master["users"] if u["role"] == "finance"}

    assert len({i["document_number"] for i in invoices}) == len(invoices)
    assert len({(i["supplier_id"], i["number"]) for i in invoices}) == len(invoices)
    assert len({p["document_number"] for p in payments}) == len(payments)
    assert len({p["invoice"]["id"] for p in payments}) == len(payments), "two payments for one invoice"
    for i in invoices:
        assert i["posting_date"].weekday() < 5 and i["posting_date"] <= END and i["invoice_date"] <= END
        assert i["due_date"] >= i["invoice_date"] and i["posting_date"] >= i["invoice_date"]
        assert i["gross"] == sum((l["total"] for l in i["items"]), Decimal("0.00"))
        assert i["created_at"].date() == i["posting_date"] and i["updated_at"] >= i["created_at"]
        assert i["updated_at"] <= END_DT
        assert i["status"] != "blocked" or i["block_reason"]
        assert (i["status"] in ("approved", "paid")) == (i["approved_at"] is not None)
        if i["approved_at"]:
            assert i["approved_at"] > i["created_at"] and i["approved_by"]
        assert (i["payment"] is not None) == (i["status"] in ("approved", "paid"))
        last_receipt = max(r["date"] for r in receipts_by_order[i["order"]["id"]])
        if i["kind"] == "before_receipt":
            assert i["invoice_date"] < last_receipt and i["posting_date"] < last_receipt
            assert i["approved_by"] is None or (i["approved_by"] in finance and i["approved_at"].date() > last_receipt)
        elif i["kind"] != "duplicate":
            assert i["invoice_date"] > last_receipt
        for l in i["items"]:
            assert l["quantity"] > 0 and l["price"] >= 0
    for kind_check in invoices:
        if kind_check["kind"] == "noise":
            diffs = [pct_bps(l["price"], o["unit_price"]) for l, o in zip(kind_check["items"], kind_check["order"]["items"])]
            assert 30 <= max(diffs) <= 180 and all(d == 0 or 30 <= d <= 180 for d in diffs)
        if kind_check["kind"] == "price":
            diffs = [pct_bps(l["price"], o["unit_price"]) for l, o in zip(kind_check["items"], kind_check["order"]["items"])]
            hits = [d for d in diffs if d]
            assert 1 <= len(hits) <= PRICE_MAX_ITEMS and all(250 <= d <= 1200 for d in hits)
        if kind_check["kind"] == "quantity":
            diffs = [pct_bps(l["quantity"], Decimal(sum(qn for r in receipts_by_order[kind_check["order"]["id"]]
                                                       for it, qn in r["items"] if it["id"] == l["purchase_order_item_id"])))
                     for l in kind_check["items"]]
            hits = [d for d in diffs if d]
            assert len(hits) == 1 and 600 <= hits[0] <= 2000
    for p in payments:
        inv = p["invoice"]
        assert p["amount"] == inv["gross"] and p["scheduled_for"].weekday() < 5
        assert p["scheduled_for"] >= snap(inv["due_date"])
        assert (p["status"] == "paid") == (inv["status"] == "paid") == (p["scheduled_for"] <= END)
        assert p["created_at"] >= inv["approved_at"] and p["created_by"] in finance
        assert p["reference"][:4] in ("PIX-", "BOL-", "TED-") and len(p["reference"]) == 12


def print_report(invoices, payments, orders, statuses):
    print(f"\nFaturas: {len(invoices)} ({sum(len(i['items']) for i in invoices)} itens)")
    by_status = Counter(i["status"] for i in invoices)
    for status in ("received", "matched", "blocked", "approved", "paid", "cancelled"):
        print(f"  {status}: {by_status[status]}")

    print(f"\nPagamentos: {len(payments)}")
    print("  por status: " + ", ".join(f"{s}: {n}" for s, n in sorted(Counter(p['status'] for p in payments).items())))
    methods = Counter(p["method"] for p in payments)
    print("  por método: " + ", ".join(f"{m}: {methods[m]} ({100 * methods[m] / len(payments):.0f}%)"
                                       for m in PAYMENT_METHODS))

    kinds = Counter(i["kind"] for i in invoices)
    late = [p for p in payments if p["late"]]
    stale = [i for i in invoices if i["stale"]]
    dup_paid = [i for i in invoices if "duplicate_payment" in i["tags"]]
    exceptions = {i["seq"] for i in invoices if i["kind"] in ("price", "quantity", "before_receipt", "duplicate")}
    with_late = exceptions | {p["invoice"]["seq"] for p in late}
    print(f"\nTaxa de exceção: {100 * len(exceptions) / len(invoices):.1f}% "
          f"({len(exceptions)} de {len(invoices)} faturas: divergência de preço ou quantidade, fatura antes "
          f"do recebimento ou duplicidade)")
    print(f"  incluindo pagamento atrasado: {100 * len(with_late) / len(invoices):.1f}% ({len(with_late)})")

    print("\nAnomalias:")
    print(f"  price_divergence: {kinds['price']} "
          f"(faixa até 5%: {sum(1 for i in invoices if i['kind'] == 'price' and not i['price_band_high'])}, "
          f"acima de 5%: {sum(1 for i in invoices if i['kind'] == 'price' and i['price_band_high'])})")
    print(f"  quantity_divergence: {kinds['quantity']}")
    print(f"  invoice_before_receipt: {kinds['before_receipt']}")
    print(f"  duplicate_invoice: {kinds['duplicate']} "
          f"(pagas: {len(dup_paid)}, blocked: {sum(1 for i in invoices if i['kind'] == 'duplicate' and i['status'] == 'blocked')}, "
          f"cancelled: {sum(1 for i in invoices if i['kind'] == 'duplicate' and i['status'] == 'cancelled')})")
    print(f"  duplicate_payment: {len(dup_paid)}")
    print(f"  late_payment: {len(late)}")
    print(f"  stale_blocked_invoice: {len(stale)}")
    print(f"  ruído dentro da tolerância (não é exceção): {kinds['noise']}")

    invoiced = sum((i["gross"] for i in invoices), Decimal("0.00"))
    duplicates = sum((i["gross"] for i in invoices if i["kind"] == "duplicate"), Decimal("0.00"))
    invoiced_orders = {i["order"]["id"] for i in invoices}
    received_all = sum((o["total"] for o in orders if statuses[o["id"]] == "received"), Decimal("0.00"))
    received_invoiced = sum((o["total"] for o in orders if o["id"] in invoiced_orders), Decimal("0.00"))
    print("\nTotal faturado x pedidos recebidos:")
    print(f"  faturado (todas as faturas): {brl(invoiced)}, das quais duplicatas: {brl(duplicates)}")
    print(f"  pedidos recebidos por completo: {brl(received_all)} ({sum(1 for o in orders if statuses[o['id']] == 'received')} pedidos)")
    print(f"  pedidos recebidos que já têm fatura: {brl(received_invoiced)} ({len(invoiced_orders)} pedidos)")
    print(f"  faturado sem duplicatas - pedidos com fatura: {brl(invoiced - duplicates - received_invoiced)} "
          f"(preço/quantidade a maior nas faturas)")

    blocked = [i for i in invoices if i["status"] == "blocked"]
    total_blocked = sum((i["gross"] for i in blocked), Decimal("0.00"))
    ages = sorted((END - i["posting_date"]).days for i in blocked)
    print(f"\nBloqueadas: {len(blocked)}, valor total {brl(total_blocked)}")
    if ages:
        print(f"  idade (dias desde a posting_date até o corte): mínima {ages[0]}, mediana {ages[len(ages) // 2]}, "
              f"máxima {ages[-1]}; com mais de {STALE_MIN_AGE_DAYS} dias: {sum(1 for a in ages if a > STALE_MIN_AGE_DAYS)}")
    by_reason = defaultdict(lambda: [0, Decimal("0.00")])
    for i in blocked:
        by_reason[i["block_reason"]][0] += 1
        by_reason[i["block_reason"]][1] += i["gross"]
    for reason, (n, value) in sorted(by_reason.items()):
        print(f"  {reason}: {n}, {brl(value)}")


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    master, _, _, orders, _, _ = build_purchasing()
    receipts, _, _, _ = build_receipts(random.Random(RECEIPTS_SEED), master, orders)
    number_receipts(receipts)
    statuses, _ = order_statuses(orders, receipts)

    rng = random.Random(SEED)
    invoices = build_invoices(rng, master, orders, receipts, statuses)
    payments, inv_numbers, pay_numbers = number_documents(invoices)
    check(invoices, payments, receipts, master)

    closed = {}
    for p in payments:
        if p["status"] == "paid":
            oid = p["invoice"]["order"]["id"]
            closed[oid] = min(closed.get(oid, p["paid_at"]), p["paid_at"])

    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_SQL, "w", encoding="utf-8", newline="\n") as f:
        f.write(render_sql(invoices, payments, closed, inv_numbers, pay_numbers))

    def rows(kind_tag, table, docs):
        return [(kind_tag, table, d) for d in sorted(docs)]
    manifest = (
        rows("price_divergence", "invoice_receipts", (i["document_number"] for i in invoices if i["kind"] == "price"))
        + rows("quantity_divergence", "invoice_receipts", (i["document_number"] for i in invoices if i["kind"] == "quantity"))
        + rows("invoice_before_receipt", "invoice_receipts", (i["document_number"] for i in invoices if i["kind"] == "before_receipt"))
        + rows("duplicate_invoice", "invoice_receipts", (i["document_number"] for i in invoices if i["kind"] == "duplicate"))
        + rows("duplicate_payment", "payments", (i["payment"]["document_number"] for i in invoices if "duplicate_payment" in i["tags"]))
        + rows("late_payment", "payments", (p["document_number"] for p in payments if p["late"]))
        + rows("stale_blocked_invoice", "invoice_receipts", (i["document_number"] for i in invoices if i["stale"])))
    update_manifest({"price_divergence", "quantity_divergence", "invoice_before_receipt", "duplicate_invoice",
                     "duplicate_payment", "late_payment", "stale_blocked_invoice"}, manifest)

    print(f"Wrote {OUTPUT_SQL}")
    print_report(invoices, payments, orders, statuses)


if __name__ == "__main__":
    main()