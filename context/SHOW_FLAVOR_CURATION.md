# Context: Show Flavor Curation

**AUDIENCE:** Curators, AI agents, and developers who need to populate or update
curated per-show metadata (currently `shortName`; later: RSVP labels, emoji,
accent color, push templates, etc.) in the `ShowFlavors` DynamoDB table.

**PREREQUISITE:** Read `TV_WATCH_PARTY_CONTEXT.md` first — specifically §5a
(ShowFlavor) and §5b (the reformat-titles admin endpoint).

---

## 1. Why a Script and Not an API

⚠️ **The mechanism by which curated show data reaches production is a script,
not an HTTP endpoint.** This is intentional but unusual for this codebase, so
this section explains the trade-off.

Curated show metadata is **editorial content**, not user data:

- **Every row shapes every user's experience** of a show. Each write deserves
  a human or agent review step before it hits prod. An API would invite
  drive-by writes.
- **Auditability.** Every row carries a `source` field; the script forces
  curators to supply it explicitly (no defaults).
- **Single chokepoint.** All writes go through one committed script
  (`scripts/populate_show_flavors.py`), so every change is reproducible from
  a CSV and git history.
- **Tight IAM scope.** The IAM principal for curation needs `BatchWriteItem`
  on `ShowFlavors` only — not on `InviterTable` or any live user data.

The trade-off: deploying flavor data means **running a script**, not hitting an
endpoint. This doc exists so that's unambiguous to anyone who looks at the
codebase later and wonders "how does the table get populated?"

---

## 1.5. AWS Account IDs (canonical)

| Env | Account ID | Notes |
|---|---|---|
| Staging | `575960429871` | Staging EB env `inviter-staging`. |
| Prod | `871070087012` | Production EB env `inviter-test`. Yes, the EB env is misleadingly named — the AWS account is prod. |

⚠️ The script verifies the resolved AWS account against the expected ID for
`--env` (via `sts:GetCallerIdentity`) and aborts on mismatch. **This is the
real staging-vs-prod safety check — profile names are not.** Anyone whose
`[default]` profile happens to point at the prod account (this has been seen
in the wild on this team) is protected by the account check regardless of
which `--env` they pass.

## 2. Data Model Recap

See `TV_WATCH_PARTY_CONTEXT.md` §5a for the canonical model. Quick reminder:

- **Table:** `ShowFlavors` (region `us-west-2`, on-demand billing, isolated
  from `InviterTable`).
- **Partition key:** `showId` (Number — TVMaze show ID).
- **No sort key. No GSI.**
- **Attributes (v1):** `showId`, `shortName`, `lastUpdated` (epoch ms), `source`.
- **Schemaless extension:** future fields (emoji, accent color, RSVP labels,
  etc.) are added as nullable attributes to the Java bean. The script picks
  them up automatically once they're added to `COLUMN_MAP` in
  `populate_show_flavors.py`.

---

## 3. When to Run the Script

Run it when any of these happen:

| Trigger | Example |
|---|---|
| **First population** | Initial seed of nicknames for top-N popular shows. |
| **New popular show appears** | Users start creating watch parties for a show that's not yet in the table. |
| **Editorial change** | Renaming a `shortName` (e.g., "Drag Race" → "All Stars") for a show. |
| **New attribute rollout** | A new field (e.g., `emoji`) is added to `ShowFlavor`; re-run with a CSV that populates it. |
| **Refresh / re-curation** | Periodic re-run of the population agent's output. |

**Do NOT run the script:**

- To delete rows. The script is upsert-only by design. Deletes are intentionally
  a manual `aws dynamodb delete-item` so they're not casual.
- During peak prod traffic windows. The 60-minute cache (see §7) means
  changes take time to propagate anyway, and write throttling at peak is
  undesirable.
- Without a dry-run first (see §5).

---

## 4. CSV Format

Header row required. Columns:

| Column | Required | Type | Notes |
|---|---|---|---|
| `tvmaze_show_id` | yes | integer | TVMaze show ID. Partition key. |
| `short_name` | optional | string | Colloquial nickname (e.g. `"All Stars"`). Blank → row skipped. |
| *future columns…* | — | — | Added as the entity grows. See §9. |

**Example:**

```csv
tvmaze_show_id,short_name
4596,All Stars
1526,Drag Race
40,The Office
73,Bake Off
```

**Rules:**

- Header row required. Column order doesn't matter.
- Empty `short_name` (or, more generally, all "curatable" columns blank) causes
  the row to be **silently skipped**. This is normal — uncurated long-tail
  shows have no entry, and the formatter falls back to the raw TVMaze title.
- Whitespace is trimmed on every cell.
- Unknown columns produce a stderr **warning** but don't fail the run
  (forward-compat for in-progress schema changes).
- Rows with a non-numeric `tvmaze_show_id` are skipped with a warning.
- UTF-8 encoding. Non-ASCII characters in nicknames are fine.

### How to Generate the CSV

Two paths:

**(a) Agent-generated.** A "find colloquial nicknames" agent produces this CSV
as its deliverable. Review the proposed names before running — spot-check 5–10
entries against your knowledge of the show. The agent should write the source
tag (e.g., `agent-v1`) in its handoff so you pass the matching `--source` flag.

**(b) Hand-curated.** For a small set of shows, open the CSV in any editor and
add rows manually. Find a show's TVMaze ID via search:

```bash
curl -s "https://api.tvmaze.com/singlesearch/shows?q=rupauls%20drag%20race%20all%20stars" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['id'])"
# → 4596
```

---

## 5. Running the Script

### Prerequisites

- Python 3.8+
- `boto3` installed: `pip install boto3`
- AWS credentials configured for the target environment (see §6).

### Step 0: Verify Your AWS Identity Before Anything

The script verifies the account internally, but a 10-second sanity check up
front is still smart:

```bash
aws sts get-caller-identity --profile staging
aws sts get-caller-identity --profile prod
```

Confirm `Account` matches §1.5: staging → `575960429871`, prod → `871070087012`.
If your `[default]` profile happens to be configured for either account, the
script will still catch a mismatch, but **never rely on `--profile default`** —
always pass the named profile that matches your intent.

### ✅ Always Dry-Run First

```bash
python3 scripts/populate_show_flavors.py \
    --csv ~/Downloads/flavors-2026-05-26.csv \
    --env staging \
    --profile staging \
    --source agent-v1 \
    --dry-run
```

Dry-run validates the CSV, **resolves and verifies the AWS account**, and
prints a sample of what *would* be written — without writing anything. **Do
this every time before any real write, even in staging.**

The output shows:

```
CSV parsed: 240 data rows / 187 curatable / 53 skipped
  Env:    staging
  Table:  ShowFlavors (region: us-west-2)
  Rows:   187
  First 5 rows to write:
    showId=   4596  shortName='All Stars'    source='agent-v1'
    ...

  AWS profile: 'staging'
  Account:     575960429871  (staging)
  Identity:    arn:aws:iam::575960429871:user/...

  --dry-run set; account verified, no writes performed.
```

If row counts, sample data, or the resolved account look off, **stop** and fix
before proceeding. On an account mismatch the script aborts before showing the
preview.

### Staging Write

```bash
python3 scripts/populate_show_flavors.py \
    --csv ~/Downloads/flavors-2026-05-26.csv \
    --env staging \
    --profile staging \
    --source agent-v1
```

Confirmation prompt:

```
  Type 'yes' to proceed:
```

Type `yes` to write. Anything else aborts. Use `--yes` to skip the prompt in
automation.

### Prod Write

```bash
python3 scripts/populate_show_flavors.py \
    --csv ~/Downloads/flavors-2026-05-26.csv \
    --env prod \
    --profile prod \
    --source agent-v1
```

You'll see `*** PROD WRITE ***` in the confirmation. Skipping the prompt in
prod requires BOTH `--yes` AND `--i-really-mean-prod` — deliberately cumbersome
to prevent accidental pastes of a staging command at prod.

### Flag Reference

| Flag | Purpose |
|---|---|
| `--csv PATH` | **Required.** CSV file to ingest. |
| `--env staging\|prod` | **Required.** Target environment. Verified against the resolved AWS account ID (see §1.5). |
| `--profile NAME` | **Required.** AWS profile from `~/.aws/credentials`. No default — explicit avoids the `[default]`-points-at-the-wrong-account trap. |
| `--source TAG` | **Required.** Audit tag written to `source` field. Examples: `agent-v1`, `manual-2026-05-26`, `curator-bbutler`. |
| `--dry-run` | Validate, verify account, and preview without writing. |
| `--yes` | Skip the interactive prompt (staging automation). |
| `--i-really-mean-prod` | Required with `--yes` when `--env=prod`. |

---

## 6. AWS Credentials

The script reads credentials from `~/.aws/credentials` and `~/.aws/config` via
boto3. **Set up named profiles per environment — do not use `[default]`** for
either staging or prod credentials. The reason is what just happened on this
team: `[default]` had silently been configured for prod by a prior `aws
configure` run, so any `--env=staging` invocation that omitted `--profile`
would have written to prod. The script's account verification (§1.5) catches
this, but the better fix is to never rely on `[default]` at all.

Recommended `~/.aws/credentials`:

```ini
[staging]
aws_access_key_id     = AKIA…STAGING…
aws_secret_access_key = …

[prod]
aws_access_key_id     = AKIA…PROD…
aws_secret_access_key = …
```

`~/.aws/config` (regions only — the account is set by which key/secret you
authenticate with):

```ini
[profile staging]
region = us-west-2

[profile prod]
region = us-west-2
```

Then always pass `--profile staging` or `--profile prod` explicitly. The
script verifies the resolved account matches the expected ID for `--env` —
mismatches abort before any write.

**Required IAM permissions** (scope tight — this is curator credentials, not
backend access):

```json
{
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["sts:GetCallerIdentity"],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "dynamodb:BatchWriteItem",
        "dynamodb:PutItem",
        "dynamodb:DescribeTable",
        "dynamodb:GetItem"
      ],
      "Resource": "arn:aws:dynamodb:us-west-2:*:table/ShowFlavors"
    }
  ]
}
```

`sts:GetCallerIdentity` is granted to every authenticated principal by
default, so most curators won't need to add it explicitly — but it's listed
for completeness in case a custom least-privilege policy strips it.

---

## 7. ⚠️ The Cache Caveat

`ShowFlavorService` caches every `getFlavor(showId)` result for **60 minutes**
via Caffeine (see `CacheConfig.java`). This affects what's visible after a
write:

- **Negative cache lingers.** If a show was looked up before its row existed
  (returning `Optional.empty()`), the negative result is cached for 60 minutes
  per backend pod. New writes won't be visible until the entry ages out.
- **First lookup wins.** For shows that have never been looked up, the next
  lookup hits the new row immediately.
- **Restarting EB instances flushes the cache.** Heavyweight; only worth it
  for urgent testing.

### Recommended Sequence to Make a Change Visible Right Now

1. Run the script (staging or prod).
2. For each existing watch-party series whose show was just curated, call the
   reformat endpoint:
   ```bash
   # Prod
   curl -X POST \
     -H "X-Api-Key: $INTERNAL_API_KEY" \
     "https://am6c8sp6kh.execute-api.us-west-2.amazonaws.com/prod/internal/watch-party/{seriesId}/reformat-titles"

   # Staging
   curl -X POST \
     -H "X-Api-Key: $INTERNAL_API_KEY" \
     "https://v7ihwy6uv9.execute-api.us-west-2.amazonaws.com/prod/internal/watch-party/{seriesId}/reformat-titles"
   ```
   The internal API key lives in SSM Parameter Store at
   `/inviter/scheduler/internal-api-key` (see `InternalApiKeyFilter`). Pull it
   per-env: `aws ssm get-parameter --name /inviter/scheduler/internal-api-key
   --with-decryption --profile staging` (or `prod`).

   This endpoint re-runs the formatter on every future-dated
   `isGeneratedTitle=true` hangout in the series. Idempotent. No notifications
   fired.
3. New watch parties created from this point pick up the flavor at creation
   time (no need to call reformat).

If you're populating a brand-new show and no one has looked it up yet, the
first user-driven lookup (e.g., creating a watch party) hits the new row
immediately — no cache lag.

---

## 8. Verifying a Write

```bash
# Read back a single row
aws dynamodb get-item \
    --table-name ShowFlavors \
    --region us-west-2 \
    --key '{"showId":{"N":"4596"}}' \
    --profile default   # or prod
```

End-to-end:

1. Create a new watch party for the show. Hangout titles should render with
   the shortName prefix (e.g., `"All Stars: How To Videos"`).
2. For pre-existing watch parties, call the reformat endpoint (§7).

---

## 9. Adding a New ShowFlavor Attribute

When `ShowFlavor.java` grows a new field (say `emoji`):

1. **Add the field to `ShowFlavor.java`** as a nullable attribute with
   getter/setter. DynamoDB tolerates absent attributes on existing rows.
2. **Add the corresponding entry to `COLUMN_MAP`** at the top of
   `scripts/populate_show_flavors.py`:
   ```python
   "emoji": ("emoji", "S", lambda v: v.strip()),
   ```
3. **If the new field counts as "curatable"** (i.e., a row with only `emoji`
   set and no `shortName` should still be written), add the CSV column name to
   `CURATABLE_COLUMNS` in the script.
4. **Update §4 (CSV Format)** in this doc.
5. **Wire the consumer** wherever the new field is used (RSVP renderer, push
   builder, etc.). Always wrap the lookup in `Optional<T>` with a fallback to
   existing behavior — shows without the new attribute must keep working.
6. Curators add the new column to their CSV and re-run the script.

### ⚠️ PutItem Replaces Whole Rows

`batch_writer` uses `PutItem`, which **replaces the entire row**. If your CSV
has only `tvmaze_show_id,short_name` but the row in DDB already has `emoji`,
running the script will **delete the emoji** because the new item doesn't
include that attribute.

**Workaround:** when re-running after a schema grows, always include every
column the row should retain in the CSV. The agent generating the CSV is
responsible for producing complete rows, not partial diffs.

If selective/additive updates ever become a real need, extend the script with
a fetch-then-merge mode (left out of v1 for simplicity).

---

## 10. Troubleshooting

### `ERROR: CSV missing required columns: ['tvmaze_show_id']`
The CSV header row doesn't have `tvmaze_show_id`. Add the column (or rename it
from `id`/`show_id`/etc.).

### `ERROR: no AWS credentials for profile 'prod'`
Check `~/.aws/credentials` has a `[prod]` section. Or pass `--profile <name>`
explicitly.

### `ERROR: AWS account mismatch — refusing to write`
The credentials behind your `--profile` resolved to an AWS account that
doesn't match the expected one for the `--env` you passed. The script prints
the expected vs resolved account IDs and identity ARN. Common causes:
- `--profile default` where `[default]` is configured for a different account
  than you assume. Solution: use a named profile (`--profile staging` /
  `--profile prod`) so the credential source is unambiguous.
- Stale or wrong credentials in the named profile. Run
  `aws sts get-caller-identity --profile <name>` to see what account those
  credentials actually authenticate to, and reconcile with §1.5.
- New AWS account brought online: update `EXPECTED_ACCOUNT_IDS` at the top of
  `scripts/populate_show_flavors.py` and update §1.5 here.

### `ResourceNotFoundException: Table ShowFlavors not found`
The app hasn't created the table yet in this environment. Boot the backend
once (`./gradlew bootRun` locally, or trigger an EB deploy) —
`DynamoDBTableInitializer` creates `ShowFlavors` on startup. Then re-run the
script.

### Push notification still shows the long name after script run
The `ShowFlavorService` cache is holding a stale entry. See §7 — wait, restart
EB instances, or call the reformat endpoint for the affected series.

### CSV has Unicode and characters look mangled
Save the CSV as UTF-8. The script reads with `encoding="utf-8-sig"`, which
tolerates the UTF-8 BOM that Excel and some text editors prepend — but
non-UTF-8 encodings (Windows-1252, etc.) will still produce mangled rows.

### Script exited mid-run with "DynamoDB write failed after N/M rows"
**Re-run with the same CSV.** PutItem is upsert, so re-writing the already-
written rows is a no-op semantically. The error message reports exactly how
many rows landed before the failure. Common causes: expired AWS creds during a
long run, throttling exceeded boto3's automatic retries, or a malformed item
the script didn't catch.

### "WARNING: CSV is missing curatable columns [...]" — what does this mean?
The script's `COLUMN_MAP` knows about more curatable attributes than your CSV
provides columns for. Because PutItem replaces the whole row, any existing
values for those attributes will be ERASED on write (see §9). Either add the
missing columns to your CSV (even empty, if you want to clear them
deliberately) or proceed knowing the erasure is intentional.

### `WARNING: ignoring unknown columns: [...]`
Your CSV has columns the script doesn't recognize. Either (a) add them to
`COLUMN_MAP` if intentional, or (b) remove them from the CSV. The run
continues either way.

### A row was silently skipped and I didn't expect it
Most likely the `short_name` (or whatever curatable column) was blank for that
row. Re-run with `--dry-run` and check stderr / row counts. Non-numeric
showIds and missing showIds are also logged to stderr.

---

## 11. Cross-References

| Topic | Location |
|---|---|
| Feature overview & data model | `context/TV_WATCH_PARTY_CONTEXT.md` §5a |
| Reformat-titles admin endpoint | `context/TV_WATCH_PARTY_CONTEXT.md` §5b |
| TVMaze API (for finding showIds) | `context/TVMAZE_API_CONTEXT.md` |
| The script itself | `scripts/populate_show_flavors.py` |
| The Java entity (add new fields here) | `src/main/java/com/bbthechange/inviter/model/ShowFlavor.java` |
| The service & cache config | `src/main/java/com/bbthechange/inviter/service/ShowFlavorService.java`, `config/CacheConfig.java` |
