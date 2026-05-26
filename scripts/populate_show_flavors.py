#!/usr/bin/env python3
"""
populate_show_flavors.py — Upsert curated ShowFlavor records into DynamoDB.

This script is the ONLY sanctioned way to write to the ShowFlavors table.
There is no in-app write API — curation is intentionally offline so curated
data is reviewed before it touches prod.

READ context/SHOW_FLAVOR_CURATION.md BEFORE RUNNING.

Quick reference:
    # Always dry-run first
    python3 scripts/populate_show_flavors.py \\
        --csv ~/Downloads/flavors.csv \\
        --env staging \\
        --source agent-v1 \\
        --dry-run

    # Staging write
    python3 scripts/populate_show_flavors.py \\
        --csv ~/Downloads/flavors.csv \\
        --env staging \\
        --source agent-v1

    # Prod write (interactive)
    python3 scripts/populate_show_flavors.py \\
        --csv ~/Downloads/flavors.csv \\
        --env prod \\
        --source agent-v1

To add a new ShowFlavor attribute, add it to COLUMN_MAP below AND to the
Java entity at src/main/java/com/bbthechange/inviter/model/ShowFlavor.java.
"""

import argparse
import csv
import sys
import time

try:
    import boto3
    from botocore.exceptions import ClientError, NoCredentialsError
except ImportError:
    sys.stderr.write("ERROR: boto3 is required. Install with: pip install boto3\n")
    sys.exit(2)


REGION = "us-west-2"
TABLE = "ShowFlavors"

# CSV column → (DDB attribute name, type code, parser function).
# Type code is informational (PutItem auto-types from the Python value); kept
# for documentation and for any future validation we add.
#
# To add a new ShowFlavor field:
#   1. Add it to ShowFlavor.java as a nullable getter/setter.
#   2. Add an entry here.
#   3. Update context/SHOW_FLAVOR_CURATION.md §4 (CSV Format).
COLUMN_MAP = {
    "tvmaze_show_id": ("showId",    "N", lambda v: int(v.strip())),  # PK, required
    "short_name":     ("shortName", "S", lambda v: v.strip()),        # optional
    # Future fields, e.g.:
    # "emoji":        ("emoji",       "S", lambda v: v.strip()),
    # "accent_color": ("accentColor", "S", lambda v: v.strip()),
}

REQUIRED_COLUMNS = ["tvmaze_show_id"]

# Columns that, if all blank for a given row, mean "nothing curatable" — skip.
# Update when adding new curated attributes so a row with only emoji (no shortName)
# still writes.
CURATABLE_COLUMNS = ["short_name"]


def parse_args():
    p = argparse.ArgumentParser(
        description="Upsert curated ShowFlavor records into DynamoDB.",
        epilog="Read context/SHOW_FLAVOR_CURATION.md before running.",
    )
    p.add_argument("--csv", required=True,
                   help="Path to CSV file. Header row required. Required column: "
                        "tvmaze_show_id. Optional curated columns: short_name "
                        "(and future fields per COLUMN_MAP).")
    p.add_argument("--env", required=True, choices=["staging", "prod"],
                   help="Target environment. Determines default AWS profile.")
    p.add_argument("--source", required=True,
                   help="Audit tag written to the 'source' field of every row. "
                        "Examples: 'agent-v1', 'manual-2026-05-26', 'curator-bbutler'. "
                        "Required for audit — there is no default.")
    p.add_argument("--dry-run", action="store_true",
                   help="Validate CSV and show what would be written; no writes.")
    p.add_argument("--yes", action="store_true",
                   help="Skip the interactive confirmation prompt. For staging "
                        "automation. For prod, also requires --i-really-mean-prod.")
    p.add_argument("--i-really-mean-prod", action="store_true",
                   help="Required together with --yes when --env=prod. Deliberately "
                        "cumbersome — prevents pasting a staging command at prod.")
    p.add_argument("--profile", default=None,
                   help="AWS profile override. Defaults: 'default' for staging, "
                        "'prod' for prod.")
    return p.parse_args()


def load_csv(path):
    # utf-8-sig tolerates the BOM that Excel and some text editors prepend when
    # saving as UTF-8. Without it, the first header cell ends up as "﻿{col}"
    # and the required-columns check fails with a confusing error.
    try:
        f = open(path, encoding="utf-8-sig")
    except OSError as e:
        sys.exit(f"ERROR: could not open CSV at {path}: {e}")
    with f:
        reader = csv.DictReader(f)
        if not reader.fieldnames:
            sys.exit(f"ERROR: CSV at {path} has no header row.")

        missing = [c for c in REQUIRED_COLUMNS if c not in reader.fieldnames]
        if missing:
            sys.exit(f"ERROR: CSV missing required columns: {missing}. "
                     f"Header was: {list(reader.fieldnames)}")

        unknown = [c for c in reader.fieldnames if c not in COLUMN_MAP]
        if unknown:
            sys.stderr.write(
                f"WARNING: ignoring unknown columns (add to COLUMN_MAP if "
                f"intentional): {unknown}\n"
            )

        # PutItem semantics: every write replaces the row in full. If the CSV is
        # missing curatable columns that COLUMN_MAP knows about, any existing
        # values for those attributes will be erased on re-write. Surface this
        # loudly so the curator can choose to include the column (even if blank
        # to clear it deliberately) or proceed knowingly.
        present = set(reader.fieldnames)
        absent_curatable = [c for c in CURATABLE_COLUMNS if c not in present]
        if absent_curatable:
            sys.stderr.write(
                f"WARNING: CSV is missing curatable columns {absent_curatable}. "
                f"PutItem replaces whole rows, so any existing values for these "
                f"attributes will be ERASED on every row written. See "
                f"SHOW_FLAVOR_CURATION.md §9 for the all-columns-CSV discipline.\n"
            )

        return list(reader)


def build_item(row, source, now_ms, line_no):
    """Build a DDB item dict from one CSV row, or None if the row should be skipped."""
    raw_show_id = (row.get("tvmaze_show_id") or "").strip()
    if not raw_show_id:
        sys.stderr.write(f"  line {line_no}: skipping — empty tvmaze_show_id\n")
        return None
    try:
        show_id = int(raw_show_id)
    except ValueError:
        sys.stderr.write(
            f"  line {line_no}: skipping — non-numeric tvmaze_show_id "
            f"{raw_show_id!r}\n"
        )
        return None

    # Skip rows where every curatable column is blank — no point writing a row
    # that only has the audit metadata.
    has_curated = any((row.get(c) or "").strip() for c in CURATABLE_COLUMNS)
    if not has_curated:
        return None  # silent skip — uncurated rows are expected (long tail)

    item = {
        "showId":      show_id,
        "lastUpdated": now_ms,
        "source":      source,
    }
    for csv_col, (ddb_attr, _ddb_type, parse_fn) in COLUMN_MAP.items():
        if csv_col == "tvmaze_show_id":
            continue
        raw = (row.get(csv_col) or "").strip()
        if raw:
            try:
                item[ddb_attr] = parse_fn(raw)
            except (ValueError, TypeError) as e:
                sys.stderr.write(
                    f"  line {line_no}: skipping — could not parse {csv_col}={raw!r}: {e}\n"
                )
                return None
    return item


def preview(env, table, items, dry_run):
    print()
    print(f"  Env:    {env}")
    print(f"  Table:  {table} (region: {REGION})")
    print(f"  Rows:   {len(items)}")
    print()
    sample = items[:5]
    print(f"  First {len(sample)} rows to write:")
    for it in sample:
        sn = it.get("shortName", "")
        print(f"    showId={it['showId']:>7}  shortName={sn!r:30s}  source={it['source']!r}")
    if dry_run:
        print()
        print("  --dry-run set; no writes will happen.")


def write_batch(items, profile):
    """
    Write all items in chunks of 25 (DynamoDB BatchWriteItem max).

    We process explicit chunks rather than streaming through a single
    `batch_writer` context so that on failure mid-run the caller knows
    exactly how many items have been written and which is the first
    unwritten one. boto3's `batch_writer` retries `UnprocessedItems`
    automatically; we rely on that within each chunk's `with` block.

    Returns (written_count, total_count). On exception, the exception
    propagates after partial progress is recorded — caller should report
    written_count before re-raising or exiting.
    """
    session = boto3.Session(profile_name=profile)
    table = session.resource("dynamodb", region_name=REGION).Table(TABLE)
    written = 0
    total = len(items)
    chunk_size = 25
    for start in range(0, total, chunk_size):
        chunk = items[start:start + chunk_size]
        with table.batch_writer() as batch:
            for item in chunk:
                batch.put_item(Item=item)
        # Reached here only if the chunk's batch_writer flushed cleanly.
        written += len(chunk)
        if total > chunk_size:
            sys.stderr.write(f"  ...wrote {written}/{total}\n")
    return written, total


def main():
    args = parse_args()

    # Safety gate: prod + automated requires the extra flag.
    if args.env == "prod" and args.yes and not args.i_really_mean_prod:
        sys.exit("ERROR: --yes with --env=prod requires --i-really-mean-prod. "
                 "This is deliberately cumbersome.")

    rows = load_csv(args.csv)
    now_ms = int(time.time() * 1000)

    items = []
    skipped = 0
    # csv line numbers start at 2 (line 1 is the header).
    for line_no, row in enumerate(rows, start=2):
        item = build_item(row, args.source, now_ms, line_no)
        if item is None:
            skipped += 1
        else:
            items.append(item)

    print(f"\nCSV parsed: {len(rows)} data rows / "
          f"{len(items)} curatable / {skipped} skipped")

    if not items:
        print("Nothing curatable to write. Exiting.")
        return

    preview(args.env, TABLE, items, args.dry_run)

    if args.dry_run:
        return

    skip_prompt = args.yes
    if not skip_prompt:
        if args.env == "prod":
            print()
            print("  *** PROD WRITE *** This will modify production data.")
        answer = input("\n  Type 'yes' to proceed: ").strip().lower()
        if answer != "yes":
            print("Aborted.")
            return

    profile = args.profile or ("prod" if args.env == "prod" else "default")
    print(f"\nConnecting with AWS profile: {profile!r}")

    written = 0
    total = len(items)
    try:
        written, total = write_batch(items, profile)
    except NoCredentialsError:
        sys.exit(f"ERROR: no AWS credentials for profile {profile!r}. "
                 f"Check ~/.aws/credentials.")
    except ClientError as e:
        # Partial write is possible — every prior chunk landed successfully.
        # Re-running the script with the same CSV is safe (PutItem is upsert).
        sys.exit(
            f"ERROR: DynamoDB write failed after {written}/{total} rows: {e}\n"
            f"PutItem is upsert, so re-running with the same CSV is safe — "
            f"the {written} already-written rows will be re-written identically."
        )
    except Exception as e:  # pragma: no cover — defensive
        sys.exit(
            f"ERROR: unexpected failure after {written}/{total} rows: {e}\n"
            f"Re-running with the same CSV is safe (PutItem upsert)."
        )

    print(f"\nWrote {written} ShowFlavor records to {TABLE} ({args.env}).")
    print()
    print("Reminder: ShowFlavorService caches lookups for 60 minutes (Caffeine).")
    print("To make the change visible on EXISTING watch-party hangouts immediately,")
    print("call POST /internal/watch-party/{seriesId}/reformat-titles for each")
    print("affected series. New watch parties pick up the flavor on creation.")
    print()
    print("See context/SHOW_FLAVOR_CURATION.md §7 for the cache caveat in full.")


if __name__ == "__main__":
    main()
