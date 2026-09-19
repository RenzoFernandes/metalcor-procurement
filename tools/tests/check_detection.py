"""Checks that the three-way match views detect the anomalies planted by the seed generator.

Compares, for each invoice anomaly type in db/seed/anomalies_manifest.csv, the set of
invoice document numbers in the manifest with the set of invoices that vw_invoice_match
flags with the matching exception type. Read-only: only SELECT statements are sent.

Requires the Docker container metalcor-db running with V1..V8 and the seeds loaded.
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

# manifest type -> exception type in vw_invoice_match.exception_types
TYPES = {
    "price_divergence": "price_variance",
    "quantity_divergence": "quantity_variance",
    "invoice_before_receipt": "invoice_before_receipt",
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
    expected = {t: set() for t in TYPES}
    with open(MANIFEST, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row["type"] in expected:
                expected[row["type"]].add(row["document_number"])
    return expected


def detected_by_view(exception_type):
    rows = run_query(
        "SELECT invoice_number FROM vw_invoice_match "
        f"WHERE '{exception_type}' = ANY (exception_types)"
    )
    return {r[0] for r in rows}


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
        for manifest_type, view_type in TYPES.items():
            expected = expected_by_type[manifest_type]
            detected = detected_by_view(view_type)
            missing = expected - detected
            extra = detected - expected
            ok = not missing and not extra
            failed = failed or not ok

            print(f"[{manifest_type}] (view type: {view_type})")
            print(f"  expected: {len(expected)}")
            print(f"  detected: {len(detected)}")
            print(f"  missing:  {len(missing)}  {fmt(missing)}")
            print(f"  extra:    {len(extra)}  {fmt(extra)}")
            print(f"  {'PASS' if ok else 'FAIL'}")
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