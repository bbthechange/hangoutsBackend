# Time Suggestions API Contract (iOS/Android)

Contract for consuming time suggestions on the unified poll substrate. See `TIME_POLL_MIGRATION_PLAN.md` for the backend implementation plan.

## Summary

Time suggestions are no longer a standalone resource. A time suggestion is a `PollOption` on a poll where `attributeType == "TIME"`, carrying a typed `timeInput: TimeInfo`. A +1 is a `Vote`. All poll mechanics (create, add option, vote, remove vote, delete) apply.

iOS/Android must:

1. Read time suggestions from the `polls` array on feed/detail responses, not from a separate field.
2. Honor the new `viewable` and `canAddOptions` fields on `PollWithOptionsDTO`.
3. Render unknown `attributeType` values as generic polls (title + options + votes), never skip them.
4. Read each TIME option's time from `timeInput: TimeInfo`; do not rely on the server-generated `text`.

## Concept mapping (old → new)

| Old concept | New concept |
|---|---|
| `TimeSuggestion` record | One `PollOption` on a Poll where `attributeType == "TIME"` |
| `supporterIds: [userId]` | `voteCount` + `userVoted` on the option |
| Create suggestion | Create a TIME poll if none active; else add option to existing TIME poll |
| Support (+1) | Cast a vote on the option |
| Un-support | Delete the vote |
| List suggestions | Filter `polls[]` by `attributeType == "TIME"` |
| `status: ACTIVE / ADOPTED / REJECTED` | `isActive: bool` + `promotedAt: Long?` |

One active TIME poll per hangout. After adoption or direct time edit, the poll goes `isActive=false` (and `promotedAt` is set on adoption).

## Headers (no change)

- `Authorization: Bearer <jwt>` (required)
- `X-Client-Type: mobile`
- `X-App-Version: <semver>` — backend uses this to compute `viewable` and `canAddOptions` per request

> **As-shipped note:** the backend config `time-polls.min-suggestion-version` defaults to the sentinel `"UNKNOWN"`. Until operators pin a real minimum version, `canAddOptions` returns `true` for all requests (fail-open). Clients should still honor `canAddOptions` as documented; they will just always receive `true` until the gate is turned on.

## Primary read paths

### `GET /groups/{groupId}/feed`

Unchanged envelope. Each `HangoutSummaryDTO` in `withDay` / `needsDay` carries `polls: PollWithOptionsDTO[]` that now includes TIME polls.

- `timeSuggestions[]` on the summary is **removed**. Stop reading it.
- Continue sending `If-None-Match` for 304s; backend may add `Vary: X-App-Version, X-Client-Type` in a future rev when version-gated `viewable` rules ship. No client change needed.

### `GET /hangouts/{hangoutId}`

Returns `HangoutDetailDTO` with `polls: PollWithOptionsDTO[]`. TIME polls appear in this array. There is no `timeSuggestions` field on the detail DTO.

## `PollWithOptionsDTO` shape

```json
{
  "pollId": "11111111-1111-1111-1111-111111111111",
  "title": "Vote on a time",
  "description": null,
  "multipleChoice": true,
  "attributeType": "TIME",
  "isActive": true,
  "promotedAt": null,
  "viewable": true,
  "canAddOptions": true,
  "createdAtMillis": 1714000000000,
  "totalVotes": 4,
  "options": [
    {
      "optionId": "22222222-2222-2222-2222-222222222222",
      "text": "Sat evening",
      "voteCount": 3,
      "userVoted": true,
      "createdBy": "87654321-4321-4321-4321-210987654321",
      "structuredValue": null,
      "timeInput": {
        "periodGranularity": "evening",
        "periodStart": "2026-05-02T00:00:00-07:00",
        "startTime": null,
        "endTime": null
      },
      "votes": []
    },
    {
      "optionId": "33333333-3333-3333-3333-333333333333",
      "text": "Sun 7:00–9:00 PM",
      "voteCount": 1,
      "userVoted": false,
      "createdBy": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
      "structuredValue": null,
      "timeInput": {
        "periodGranularity": null,
        "periodStart": null,
        "startTime": "2026-05-03T19:00:00-07:00",
        "endTime": "2026-05-03T21:00:00-07:00"
      },
      "votes": []
    }
  ]
}
```

### New/changed fields

- `attributeType`: `"TIME"` | `"LOCATION"` | `"DESCRIPTION"` | `null` | future values.
- `isActive`: `true` = open for voting. `false` = adopted, superseded, or closed.
- `promotedAt`: epoch millis when the winning option was promoted; null if open.
- `viewable`: `true` = render; `false` = hide entirely. Missing → treat as `true`.
- `canAddOptions`: `true` = your app version may add options to this poll; `false` = do not offer "add option" UI. Missing → treat as `true`. Not an authorization signal.
- `PollOptionDTO.timeInput`: populated only for TIME polls. Same `TimeInfo` shape the app uses for hangouts.
- `PollOptionDTO.text` on TIME polls: server-generated display string. Do not rely on it.

## Client rendering rules

For each poll in feed/detail:

1. If `viewable == false` → skip entirely. Missing = `true`.
2. If `isActive == false` → render read-only (show counts, no vote UI). For TIME polls with `promotedAt != null`, the hangout time is set; the poll is redundant with the hangout's displayed time and may be hidden.
3. If `attributeType == "TIME"` → render with time-suggestion UI. Use each option's `timeInput` for display.
4. If `attributeType` is `"LOCATION"` or `"DESCRIPTION"` → existing handling.
5. Any other `attributeType` (including null or unknown) → render as a generic poll (title + options by `text` + vote counts). Never skip.
6. If `canAddOptions == false` → hide the "suggest another time / add option" button. Missing = `true`.

Don't assume "at most one TIME poll per hangout" as a hard UI invariant. Today there will be one. Render the array as-is.

## Write endpoints

All JWT-authenticated. Path base: `/hangouts/{hangoutId}/polls`.

### Create a TIME poll (first suggestion on a hangout)

```
POST /hangouts/{hangoutId}/polls
Content-Type: application/json
```

```json
{
  "title": "Vote on a time",
  "multipleChoice": true,
  "attributeType": "TIME",
  "options": [
    { "timeInput": { "periodGranularity": "evening", "periodStart": "2026-05-02T00:00:00-07:00" } }
  ]
}
```

- `options` for TIME polls: `[{ "timeInput": TimeInfo }]`. Legacy `[string]` shape is rejected for TIME.
- `multipleChoice`: `true` for TIME.
- `title`: required, 1–200 chars. Use "Vote on a time" or similar — this is what clients without TIME awareness display.

**Response 201:** a `Poll` object (no options/votes). Refetch the feed or hangout detail to get option IDs for voting.

**Errors:**
- Hangout already has an active TIME poll → **400 VALIDATION_ERROR** (`"Hangout already has an active time poll"`).
- `options` is a `List<String>` with `attributeType="TIME"` → **400 VALIDATION_ERROR** (`"Adding time options requires an updated app version."`).
- `timeInput` malformed → **400 VALIDATION_ERROR** with field detail.

### Add a time option to an existing TIME poll

```
POST /hangouts/{hangoutId}/polls/{pollId}/options
Content-Type: application/json
```

```json
{ "timeInput": { "periodGranularity": "evening", "periodStart": "2026-05-02T00:00:00-07:00" } }
```

TIME polls require `timeInput`; `text` is ignored. For non-TIME polls, send `text` as before.

**Response 201:** the created `PollOption`.

**Errors:**
- TIME poll, body lacks `timeInput` → **400 VALIDATION_ERROR** (`"Adding time options requires an updated app version."`).
- `timeInput` equivalent to an existing option on the same poll → **400 VALIDATION_ERROR** (duplicate).
- `timeInput` malformed → **400 VALIDATION_ERROR**.

### Vote on an option (+1 a time)

```
POST /hangouts/{hangoutId}/polls/{pollId}/vote
Content-Type: application/json
```

```json
{ "optionId": "22222222-2222-2222-2222-222222222222", "voteType": "YES" }
```

`voteType` = `"YES" | "NO" | "MAYBE"`. Default `"YES"`. For support, send `"YES"`.

**Response 200:** the `Vote` record. Re-voting for an option you already voted for is **idempotent** (returns 200, no duplicate vote). This differs from prior behavior, which returned 400.

### Remove a vote (un-support)

```
DELETE /hangouts/{hangoutId}/polls/{pollId}/vote?optionId={optionId}
```

**Response 204.** Omitting `optionId` removes all the user's votes on the poll.

### Delete a poll (host only)

```
DELETE /hangouts/{hangoutId}/polls/{pollId}
```

**Response 204.** 403 if the user isn't a host.

### Get a single poll (fallback / debug)

```
GET /hangouts/{hangoutId}/polls/{pollId}
```

Returns `PollDetailDTO`. Use only if you need per-vote user detail. For feed display, use the feed/detail endpoints.

## Adoption flow (informational)

- 24h fast path: single option with ≥1 vote adopts at `createdAt + 24h`.
- 48h: single option with 0 votes, or multiple options with exactly one voted, adopts at `createdAt + 48h`.
- Multiple options with votes on ≥2 of them → never auto-adopts; stays open.
- Adding an option when <24h remain on the 48h timer slides it to `now + 24h`.
- Adoption sets `hangout.timeInput`/`startTimestamp`/`endTimestamp`, flips poll to `isActive=false, promotedAt=<ms>`.
- Direct PATCH of hangout time by a host supersedes all active TIME polls (flips `isActive=false`, cancels auto-adopt).

Client behavior after adoption: normal refresh of feed/detail shows the hangout time set and the TIME poll as `isActive=false`.

## Migration checklist

- Stop reading `summary.timeSuggestions[]` — the field is gone.
- Read time suggestions from `summary.polls[]` filtered by `attributeType == "TIME"` and `viewable != false`.
- Filter out `isActive == false` polls from interactive UI, or render them read-only.
- Honor `canAddOptions` on the "add time suggestion" button for TIME polls. Hide when `false`.
- Render the TIME option's time from `timeInput`, not `text`.
- Delete the old `TimeSuggestion` model and `/groups/{g}/hangouts/{h}/time-suggestions` endpoint calls.
- When creating a first suggestion, check for an existing active TIME poll on the hangout: if present, call `POST .../options`; else call `POST .../polls`.

## Error response shape

All errors use `BaseController`'s JSON shape:

```json
{ "error": "<CODE>", "message": "<human message>" }
```

| Condition | HTTP | Code |
|---|---|---|
| Invalid JWT | 401 | UNAUTHORIZED |
| Not a group/hangout member (where required) | 403 | UNAUTHORIZED |
| Hangout or poll not found | 404 | NOT_FOUND |
| Hangout already has an active TIME poll | 400 | VALIDATION_ERROR |
| TIME option add without `timeInput` | 400 | VALIDATION_ERROR |
| TIME poll create with legacy `List<String>` options | 400 | VALIDATION_ERROR |
| Duplicate option on TIME poll | 400 | VALIDATION_ERROR |
| Invalid `timeInput` shape | 400 | VALIDATION_ERROR |
| Invalid `attributeType` value | 400 | VALIDATION_ERROR |
| Title missing / too long | 400 | VALIDATION_ERROR |
| Vote on non-existent option | 400 | VALIDATION_ERROR |
| Poll deletion by non-host | 403 | UNAUTHORIZED |
| Malformed UUID in path | 400 | VALIDATION_ERROR |
| Server error | 500 | INTERNAL_ERROR |

## Don't

- Skip polls with unknown `attributeType`. Render them generically.
- Use `canAddOptions` as an authorization signal. It's a client-capability gate.
- Rely on `PollOption.text` for TIME polls. Render from `timeInput`.
- Assume at most one active TIME poll per hangout in UI-level invariants.
- Call `/groups/{g}/hangouts/{h}/time-suggestions` — endpoint removed.

## Test plan

- Hangout with active TIME poll → feed and detail both return the poll in `polls[]` with populated `timeInput` per option.
- Second user votes via `POST /polls/{id}/vote` → `voteCount` increments; re-vote for same option returns 200 idempotently.
- Un-vote via `DELETE /polls/{id}/vote?optionId=...` → count decrements.
- Add a second option via `POST /polls/{id}/options` with `timeInput` → both options appear.
- Host PATCHes hangout time → next fetch shows TIME poll `isActive=false`.
- `X-App-Version: 0.0.1` → TIME polls return `canAddOptions=false`; LOCATION polls return `canAddOptions=true`.
- Server returns `attributeType: "FUTURE_TYPE"` with `viewable: true` → client renders generically; with `viewable: false` → client hides.
- TIME option with `periodGranularity`/`periodStart` and TIME option with `startTime`/`endTime` both render correctly.
