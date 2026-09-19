"""Checks that the SQL views detect the anomalies planted by the seed generator.

Compares, for each anomaly type in db/seed/anomalies_manifest.csv, the set of document numbers in
the manifest with the set of documents that the matching view flags. The document number is the
one the manifest really holds for each type: invoices (IR), purchase orders (PO), goods receipts
(GR) or payments (PY). Read-only: only SELECT statements are sent.

  v0.2a (V8): price_divergence, quantity_divergence, invoice_before_receipt
  v0.2b (V9): order_without_approval, duplicate_invoice, duplicate_payment, late_payment,
              stale_blocked_invoice, partial_delivery, reversed_receipt

Requires the Docker container metalcor-db running with V1..V9 and the seeds loaded.
Standard library only.

Usage (from the repository root):
    python tools/tests/check_detection.py

Exit code: 0 if every type passes, 1 if any type fails, 2 if the database cannot be queried.
"""

import csv
import subprocess
import sys
from pathlib import Path

MANIFEST = Path(__file__).resolve().parents[2] / "db" / "seed" / "anomalies_manifest.csv"

PSQL = ["docker", "exec", "metalcor-db", "psql", "-U", "metalcor", "-d", "metalcor",
        "-v", "ON_ERROR_STOP=1", "-At", "-F", ","]

def match_sql(exception_type):
    return ("SELECT invoice_number FROM vw_invoice_match "
            f"WHERE '{exception_type}' = ANY (exception_types)")


# manifest type -> (what the view flags, SELECT returning the document numbers, alternatives).
# alternatives: other plausible definitions of the same type, printed for comparison only.
CHECKS = {
    "price_divergence": ("vw_invoice_match price_variance", match_sql("price_variance"), []),
    "quantity_divergence": ("vw_invoice_match quantity_variance", match_sql("quantity_variance"), []),
    "invoice_before_receipt": ("vw_invoice_match invoice_before_receipt", match_sql("invoice_before_receipt"), []),
    "order_without_approval": (
        "vw_order_approval_check.no_approval",
        "SELECT po_number FROM vw_order_approval_check WHERE no_approval",
        []),
    "duplicate_invoice": (
        "vw_duplicate_invoice_candidates.duplicate_invoice",
        "SELECT duplicate_invoice FROM vw_duplicate_invoice_candidates",
        []),
    "duplicate_payment": (
        "vw_duplicate_payment_check.excess_payment",
        "SELECT excess_payment FROM vw_duplicate_payment_check",
        []),
    "late_payment": (
        "vw_payment_timeliness.is_late",
        "SELECT payment_number FROM vw_payment_timeliness WHERE is_late",
        []),
    # Ambiguous: the manifest lists only blocked match divergences (6). Blocked duplicates over 45 days
    # are also blocked and stale by age, so the two definitions give different counts.
    "stale_blocked_invoice": (
        "vw_stale_blocked_invoices.is_stale, without duplicates",
        "SELECT invoice_number FROM vw_stale_blocked_invoices WHERE is_stale AND NOT is_duplicate_candidate",
        [("vw_stale_blocked_invoices.is_stale, including blocked duplicates",
          "SELECT invoice_number FROM vw_stale_blocked_invoices WHERE is_stale"),
         ("vw_invoice_match.is_stale (open match exceptions over 45 days)",
          "SELECT invoice_number FROM vw_invoice_match WHERE is_stale")]),
    "partial_delivery": (
        "vw_receipt_exceptions partial_delivery (first receipt)",
        "SELECT goods_receipt_number FROM vw_receipt_exceptions WHERE exception_type = 'partial_delivery'",
        []),
    "reversed_receipt": (
        "vw_receipt_exceptions reversed_receipt",
        "SELECT goods_receipt_number FROM vw_receipt_exceptions WHERE exception_type = 'reversed_receipt'",
        []),
}

LIST_LIMIT = 10


class QueryError(Exception):
    pass


def run_query(sql):
    """Runs a single SELECT through psql and returns the output rows as lists of fields."""
    if not sql.lstrip().lower().startswith("select"):
        raise ValueError("only SELECT statements are allowed")
    try:
        result = subprocess.run(PSQL + ["-c", sql], capture_output=True, text=True, encoding="utf-8")
    except FileNotFoundError:
        raise QueryError("docker command not found")
    if result.returncode != 0:
        raise QueryError(result.stderr.strip() or f"psql exited with code {result.returncode}")
    return [line.split(",") for line in result.stdout.splitlines() if line]


def read_manifest():
    expected = {t: set() for t in CHECKS}
    with open(MANIFEST, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["type"] in expected:
                expected[row["type"]].add(row["document_number"])
    return expected


def detected(sql):
    return {r[0] for r in run_query(sql)}


def fmt(numbers):
    shown = sorted(numbers)[:LIST_LIMIT]
    text = ", ".join(shown) if shown else "-"
    if len(numbers) > LIST_LIMIT:
        text += f", ... (+{len(numbers) - LIST_LIMIT} more)"
    return text


def main():
    if not MANIFEST.exists():
        print(f"Manifest not found: {MANIFEST}")
        return 2

    expected_by_type = read_manifest()
    failed = False

    try:
        for manifest_type, (label, sql, alternatives) in CHECKS.items():
            expected = expected_by_type[manifest_type]
            found = detected(sql)
            missing = expected - found
            extra = found - expected
            ok = not missing and not extra
            failed = failed or not ok

            print(f"[{manifest_type}] (view: {label})")
            print(f"  expected: {len(expected)}")
            print(f"  detected: {len(found)}")
            print(f"  missing:  {len(missing)}  {fmt(missing)}")
            print(f"  extra:    {len(extra)}  {fmt(extra)}")
            print(f"  {'PASS' if ok else 'FAIL'}")
            if alternatives:
                print("  ambiguous definition, other counts for comparison:")
                for alt_label, alt_sql in alternatives:
                    alt = detected(alt_sql)
                    print(f"    {alt_label}: {len(alt)} "
                          f"(missing {len(expected - alt)}, extra {len(alt - expected)}: {fmt(alt - expected)})")
            print()

        print("Invoices by resolution (vw_invoice_match):")
        for resolution, count in run_query(
            "SELECT resolution, count(*) FROM vw_invoice_match GROUP BY resolution ORDER BY resolution"
        ):
            print(f"  {resolution:<10} {count}")
    except QueryError as e:
        print(f"Could not query the database: {e}")
        return 2

    print()
    print("RESULT: FAIL" if failed else "RESULT: PASS")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())