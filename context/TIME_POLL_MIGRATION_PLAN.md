# Time Suggestions → Polls Migration Plan

Plan for replacing the standalone `TimeSuggestion` stack with `attributeType="TIME"` polls on the existing poll substrate. No `TimeSuggestion` data has shipped to production, so this is a clean rewrite with no data migration.

## Goal

Unify time suggestions with LOCATION/DESCRIPTION attribute suggestions onto the poll model. A time suggestion becomes a `PollOption` with typed `timeInput: TimeInfo` on a poll where `attributeType == "TIME"`. Support (+1) is a `Vote`. Auto-adoption is driven by EventBridge schedules tied to the poll.

## Non-goals

- Nested/hierarchical time suggestions (e.g., "weekend" → "Saturday" → "9pm"). The substrate shouldn't preclude them, but this PR ships flat only.
- Changes to LOCATION or DESCRIPTION promotion behavior — those continue computing status on read with no scheduled auto-apply.
- Data migration — no production `TimeSuggestion` rows exist.
- Strategy-registry refactor. LOCATION/DESCRIPTION stay where they live; TIME logic goes in a single new `TimePollService`.

## Pinned decisions

| # | Decision |
|---|---|
| 1 | TIME evaluation uses the 5-way matrix below (24h fast-path preserved; permissive leader rule at 48h). |
| 2 | Adding an option to an existing TIME poll slides the 48h schedule forward if <24h remain. |
| 3 | TIME polls don't accept string options — `timeInput` is required. Client capability exposed via `canAddOptions` on the DTO. |
| 4 | Creating a TIME poll when an active one exists returns 400 `VALIDATION_ERROR`. |
| 5 | Backend server-generates `PollOption.text` from `TimeInfo` so clients that don't recognize `attributeType="TIME"` can still render as a generic poll. |
| 6 | `PollOption.structuredValue` stays (LOCATION uses it). TIME uses the new typed `timeInput` field. |
| 7 | No strategy registry. Inline TIME logic in `TimePollService`. |
| 8 | `TimePollConfig.minTimeSuggestionVersion` uses sentinel `"UNKNOWN"` until iOS/Android pin a real version. `computeCanAddOptions` fails open (returns `true`) when the sentinel is set so shipping backend before clients doesn't hide the Add button. |
| 9 | `evaluateAndAdopt(hangoutId, pollId)` — hangoutId threaded through the EventBridge payload instead of a GSI lookup. Matches plan semantics; signature differs from plan's one-arg form. |
| 10 | Scheduler config reuses `time-suggestion.auto-adoption.short-window-hours` / `long-window-hours` for env-parity with the legacy stack. Rename to `time-polls.*` is a follow-up. |
| 11 | Authorization for TIME create/add-option/vote uses `canUserEditHangout` / `canUserViewHangout` (both currently collapse to "member of any associated group"). No explicit group-member re-check at TIME call sites — safe only while `canUserEditHangout == canUserViewHangout`. Revisit if host-only edits diverge. |

## TIME evaluation rule (the 5-way matrix)

Let `T = now - poll.createdAt`. A TIME poll is **READY** when all hold:

- `poll.isActive == true`
- `poll.promotedAt == null`

…and one of these cases matches:

| Options total | Options with ≥1 vote | T threshold | Outcome |
|---|---|---|---|
| 0 | — | any | PENDING (guard) |
| 1 | 0 | ≥ 48h | READY (silent consent) |
| 1 | 1 | ≥ 24h | **READY (fast path)** |
| ≥2 | exactly 1 | ≥ 48h | READY (permissive leader) |
| ≥2 | ≥2 | any | CONTESTED — never READY |

The **winning option** at READY time is:
- The single voted option, if any.
- Otherwise the only option on the poll.
- Otherwise don't adopt (guard).

## Scheduling model

Two EventBridge schedules per TIME poll, named by convention:

- `poll-adopt-{pollId}-24h` — fixed at `createdAt + 24h`.
- `poll-adopt-{pollId}-48h` — starts at `createdAt + 48h`. Slides forward on option-add per decision #2.

Persist `Poll.scheduledFinalAdoptionAt: Long?` (epoch ms) as the current target for the 48h (sliding) schedule. The 24h schedule is fixed and not stored.

**Sliding rule on option-add:**
- If `now + 24h > scheduledFinalAdoptionAt` → cancel the 48h schedule, recreate at `now + 24h`, persist new `scheduledFinalAdoptionAt`.
- Otherwise no change.

**Cancellation points (both schedules, as an atomic pair):**
- `evaluateAndAdopt` when it adopts.
- `onSupersede` (direct time edit on hangout).
- `onPollDeleted`.
- `onHangoutDeleted`.

**Handler re-evaluates from scratch.** If the fast-path rule no longer holds at the 24h firing, the handler no-ops — the 48h schedule carries on. If the 48h handler fires on a cancelled/stale schedule (possible during cancel+recreate races), re-evaluation sees `isActive=false` or `promotedAt != null` and no-ops.

## DTO extensions on `PollWithOptionsDTO`

All computed per-request at DTO transformation time. Never persisted.

| Field | Source | Default-when-absent |
|---|---|---|
| `isActive` | `Poll.isActive` (read-through) | always present |
| `viewable` | Stub = `true`. Future: version-gated per attributeType. | `true` |
| `canAddOptions` | Non-TIME: `true`. TIME: `clientVersion >= MIN_TIME_SUGGESTION_VERSION`. | `true` |

`MIN_TIME_SUGGESTION_VERSION` lives in config. Must match whatever version iOS/Android ship together with this backend change.

`canAddOptions` is a **client capability gate, not an authorization decision**. Annotate the computation site with a comment; don't wire it into auth.

## New/changed models and DTOs

### `PollOption`
Add nullable typed field:
```java
@DynamoDbAttribute("timeInput")
private TimeInfo timeInput;
```
`structuredValue` (JSON, LOCATION) stays untouched.

### `Poll`
Add nullable field:
```java
@DynamoDbAttribute("scheduledFinalAdoptionAt")
private Long scheduledFinalAdoptionAt;
```

### `CreatePollRequest.options`
Widen from `List<String>` to a polymorphic shape accepting both:
- Legacy: `List<String>` (LOCATION/DESCRIPTION plain text).
- New: `List<PollOptionInput>` where `PollOptionInput { text?, timeInput? }`.

For `attributeType="TIME"`, reject any option that doesn't carry `timeInput`.

### `AddPollOptionRequest`
Add optional `timeInput: TimeInfo`. Relax `text` validation: required unless `timeInput` is present.

For adds against a TIME poll, require `timeInput`; reject if only `text`.

### `PollWithOptionsDTO`
Add three fields listed above. `PollOptionDTO` already carries enough for rendering; add a `timeInput` field on it so clients don't have to round-trip to `GET /polls/{id}`.

## API contract

See `TIME_POLL_API_CONTRACT.md` for iOS-facing details. Key callouts:

- `POST /hangouts/{id}/polls` with `attributeType="TIME"` on a hangout that already has an active TIME poll → 400 `VALIDATION_ERROR` with message pointing at the existing poll.
- `POST /hangouts/{id}/polls/{pollId}/options` on a TIME poll without `timeInput` → 400 `VALIDATION_ERROR` with message `"Adding time options requires an updated app version."`
- Same message for `POST /hangouts/{id}/polls` with legacy `List<String>` options and `attributeType="TIME"`.
- `POST /hangouts/{id}/polls/{pollId}/vote` repeated for the same `optionId` by the same user → idempotent 200 (new behavior; current code throws 400 `IllegalStateException`). Also applies to non-TIME polls.

## Work breakdown

### Stream A — poll substrate extensions

1. Accept `"TIME"` in `CreatePollRequest.attributeType` validation (`PollServiceImpl.createPoll`).
2. Add `timeInput: TimeInfo` to `PollOption` model. Verify round-trip via integration test (not just unit).
3. Add `scheduledFinalAdoptionAt` to `Poll` model.
4. Widen `CreatePollRequest.options` — polymorphic deserializer or type change. Migrate existing LOCATION/DESCRIPTION callers in same PR.
5. Widen `AddPollOptionRequest` — add `timeInput`, relax `text` validation.
6. Server-generate `PollOption.text` from `timeInput` on write (TIME only). Small formatter. Use hangout's group timezone or creator's timezone; do not render UTC. Tests can cover representative fuzzy and exact cases.
7. Validate `timeInput` on create/add-option via `FuzzyTimeService.convert()`.
8. Dedupe guard: reject option-add when an existing option on the same TIME poll has an equivalent `timeInput` (same fuzzy bucket + start, or same exact start/end). Prevents the fast-path-break pitfall (see N10).
9. Single-active-TIME-poll invariant: in `PollServiceImpl.createPoll`, if `attributeType="TIME"` and an active TIME poll exists on the hangout, reject with 400.
10. Compute `isActive`, `viewable`, `canAddOptions` in `transformToPollWithOptionsDTO` (and the detail variant). `ClientInfo` is already on the request attribute — read it; if null, default all three to `true`.
11. Make `voteOnPoll` idempotent when the user votes again for an option they already voted for. Return the existing `Vote` without saving. Covers TIME and all other polls.

### Stream B — `TimePollService`

Single new service. No interface needed yet (one impl).

Methods:
- `onPollCreated(Poll poll)` — creates both schedules.
- `onOptionAdded(Poll poll)` — slides the 48h schedule if `now + 24h > scheduledFinalAdoptionAt`.
- `evaluateAndAdopt(String pollId)` — handler entry; re-evaluates the full matrix and either adopts or no-ops.
- `onSupersede(String hangoutId)` — flips all active TIME polls to `isActive=false`, cancels both schedules per poll, triggers pointer update + ETag invalidation.
- `onPollDeleted(String pollId)` — cancels both schedules.
- `onHangoutDeleted(String hangoutId)` — cancels schedules for every TIME poll on the hangout.

Shared helper: `cancelSchedulesFor(pollId)` cancels both named schedules atomically. Every terminal path calls it.

Call sites:
- `PollServiceImpl.createPoll` — after save, when `attributeType="TIME"`.
- `PollServiceImpl.addPollOption` — after save, when parent poll is TIME.
- `PollServiceImpl.deletePoll` — before delete returns.
- `HangoutServiceImpl.updateHangout` — when `timeChanged && startTimestamp != null` (replaces today's `invalidateActiveSuggestions` call).
- `HangoutServiceImpl.deleteHangout` — before cascading deletes.
- EventBridge handler — wire to `evaluateAndAdopt`.

**Adoption implementation:** on READY, set `poll.promotedAt = now`, `poll.isActive = false`, save. Apply to hangout: `hangout.timeInput = winningOption.timeInput`, recompute start/end via `FuzzyTimeService`, save hangout, update pointers via `HangoutPointerFactory.applyHangoutFields`, roll group timestamps, recompute momentum (`momentumService.recomputeMomentum(hangoutId)`).

### Stream C — direct-edit supersession

`HangoutServiceImpl.updateHangout`: when `timeChanged && hangout.startTimestamp != null`, call `timePollService.onSupersede(hangoutId)`. Remove the old `timeSuggestionService.invalidateActiveSuggestions(hangoutId)` call.

### Stream D — demolition

Delete:
- `model/TimeSuggestion.java`, `TimeSuggestionStatus.java`
- `dto/TimeSuggestionDTO.java`, `CreateTimeSuggestionRequest.java`, `TimeSuggestionPointerView.java`
- `controller/TimeSuggestionController.java`
- `service/TimeSuggestionService.java` and `service/impl/TimeSuggestionServiceImpl.java`
- `service/TimeSuggestionSchedulerService.java`
- Repository methods `findActiveTimeSuggestions`, `saveTimeSuggestion`, `findTimeSuggestionById`
- `HangoutPointer.timeSuggestions` field (getter, setter, backing field)
- The clearing rule in `HangoutPointerFactory.applyHangoutFields` for `timeSuggestions`
- `@Mock TimeSuggestionService` in `HangoutServiceTestBase`
- `TimeSuggestionServiceImplTest.java` and TimeSuggestion-specific assertions in `HangoutServiceUpdateTest` (replace with `TimePollService` equivalents)
- Transformation code in `HangoutSummaryDTO`'s constructor that reads `pointer.getTimeSuggestions()`
- EventBridge schedule group for TimeSuggestion (operational — can age out)

### Stream E — tests

Required before merge:

- **Evaluator matrix unit test** — every row of the 5-way table at its boundary (T=23h59m / T=24h and T=47h59m / T=48h).
- **Sliding window test** — option added at T=47h30m with 48h = T+48h, assert `scheduledFinalAdoptionAt` slides to `T+47h30m+24h`, assert cancel-then-create on the named schedule (mock `TimePollScheduler`).
- **Dedup guard test** — option-add with equivalent `timeInput` → 400.
- **Single-active-invariant test** — create TIME poll on hangout with active TIME poll → 400.
- **`canAddOptions` test** — `X-App-Version: 0.0.1` returns `canAddOptions=false` for TIME polls, `true` for LOCATION. Current version returns `true` for both.
- **Idempotent vote test** — vote twice for same option → 200 both times, vote count stays 1.
- **Direct-edit supersession test** — PATCH hangout time → `onSupersede` called; both schedules cancelled; active TIME polls flipped inactive.
- **Legacy-shape rejection test** — `POST /polls` with `attributeType="TIME"` and `options: ["foo"]` → 400 with the exact error message.
- **Round-trip integration test** — create TIME poll → add option → vote → trigger `evaluateAndAdopt` at T=24h+1s → hangout time set, poll inactive/promoted, pointer reflects it, feed ETag rolled.
- **Handler re-evaluation test** — fire `evaluateAndAdopt` for a poll whose 48h schedule has slid; if T < new target, no-op; if ≥ new target, adopt.
- **Feed/detail response test** — `GET /groups/{g}/feed` returns summary with TIME polls in `polls[]`, each option has populated `timeInput`; no `timeSuggestions` field.

Delete all TimeSuggestion-specific tests.

## Easy to get wrong

1. **Schedule leak (doubled).** Two schedules per poll. Every terminal path must cancel both. Treat "cancel schedules for poll X" as a single helper; never cancel one checkpoint at a call site without the other.

2. **`HangoutPointer.timeSuggestions` ghost attribute.** When you remove the field, existing DDB rows keep the attribute until the next pointer write. Verify that the next write from `HangoutPointerFactory.applyHangoutFields` causes the attribute to disappear (or is left harmlessly inert — no reader should touch it).

3. **`CreatePollRequest.options` compat.** Widening to `List<PollOptionInput>` can break LOCATION/DESCRIPTION creation if the existing callers only send strings. Polymorphic deserializer, or migrate all call sites in the same PR. Test with both shapes.

4. **`PollOption.timeInput` DDB bean registration.** Missing `@DynamoDbAttribute`, or forgetting `TimeInfo` is `@DynamoDbBean`, silently drops the field on write. Write-and-read-back integration test is the only reliable check.

5. **Vote idempotency vs. current behavior.** The current `voteOnPoll` throws `IllegalStateException` on re-vote. Several tests likely depend on this. Update tests when you flip the behavior.

6. **Authorization parity.** Old TimeSuggestion path required `groupRepository.isUserMemberOfGroup`. Poll path uses `authorizationService.canUserViewHangout` for votes and `canUserEditHangout` for creates. Different for public hangouts. Verify the TIME flows land on the right checks; if not, tighten with an explicit group-member check at the TIME-specific call sites.

7. **Pointer size growth.** Today's pointer denormalizes `polls`/`pollOptions`/`votes` unboundedly. TIME suggestions pile on. The old path capped suggestions at 5 at write time; you lose that cap. Ship as-is but track pointer size in telemetry, and be ready to add a per-poll option cap if it bites.

8. **Single-active-TIME race.** Two concurrent creates under low-contention shouldn't both succeed. Use a query-before-save with a conditional write on the save. 400 on the loser is correct; two active polls is not.

9. **Duplicate options breaking the fast path.** If someone (or a bug) submits two equivalent `timeInput` values on the same poll, the poll has 2 options and the fast path no longer applies — silently pushing adoption to 48h. The dedupe guard at add-option time prevents this.

10. **`MIN_TIME_SUGGESTION_VERSION` drift.** Off-by-one means the version you ship either can't add time options, or a pre-release build can. Coordinate with iOS/Android teammates; put the constant in config, not a magic number. Write a test asserting the gating for old/new version stubs.

11. **Timezone in generated text.** Server-generated `PollOption.text` for TIME renders the `timeInput` as a human string. Use hangout/group timezone; don't render UTC. Display-only; new clients ignore `text` and render from `timeInput` directly.

12. **Adoption must recompute momentum.** `MomentumService.recomputeMomentum(hangoutId)` needs to fire after TIME adoption, same as today's TimeSuggestion adoption. Easy to drop during the refactor.

13. **Group timestamp invalidation.** Every poll write path calls `groupTimestampService.updateGroupTimestamps`. The new TIME flows inherit this for free when they route through `updatePointersWithPolls`. Verify with a test that creating a TIME poll rolls the group ETag.

14. **`ClientInfo` null defaults.** If the request attribute is missing or version is unparseable, `viewable` and `canAddOptions` must default to `true`. Untested old request paths would otherwise hide TIME polls from their own creators.

15. **Handler failure resilience.** If `evaluateAndAdopt` throws (e.g., DDB blip mid-adopt), EventBridge retries per the schedule's retry policy. The operation must be idempotent: re-evaluating an already-promoted poll no-ops (promotedAt non-null). Verify.

## Rollout

Single deploy. No data migration. Pre-deploy check: scan DDB for `itemType = "TIME_SUGGESTION"` rows — if any exist from manual testing, leave them inert (no code reads them after deploy).

Post-deploy smoke in staging:

1. Create a timeless hangout, create a TIME poll via `POST /hangouts/{id}/polls`, verify it appears in `GET /groups/{g}/feed` under `polls[]`.
2. Vote from a second user, verify `voteCount=1` and `userVoted=true` for that user.
3. Force an EventBridge fire (or fast-forward test clock), verify hangout time is set and poll goes to `isActive=false, promotedAt != null`.
4. Direct-PATCH a hangout time on a separate test hangout with an active TIME poll, verify the poll goes inactive and both schedules are cancelled.
5. Fetch feed with `X-App-Version: 0.0.1`, assert `canAddOptions=false` on TIME polls, `true` on LOCATION polls.

If any step fails, rollback is a single revert. No forward-only state.

## Coordination with clients

- iOS/Android must ship a version that (a) honors `canAddOptions`, (b) honors `viewable`, (c) renders unknown `attributeType` as a generic poll, (d) reads time suggestions from `summary.polls` rather than `summary.timeSuggestions`. See `TIME_POLL_API_CONTRACT.md` for the iOS/Android-facing contract.
- `MIN_TIME_SUGGESTION_VERSION` on the backend must be set to whatever version ships with those client changes.
- Stale app versions in the wild will render TIME polls as generic polls (title + options by `text` + votes). They cannot create or vote with rich UX but can cast votes via the poll UI if they see it. Acceptable per decision #3.

## Effort estimate

Medium PR: two thin model additions, one new service, one request-shape change, and demolition of a parallel stack. Most risk concentrated in schedule lifecycle (item 1) and request-shape compat (item 3). Roughly 1–2 focused days of implementation plus the same for test coverage and verification.
