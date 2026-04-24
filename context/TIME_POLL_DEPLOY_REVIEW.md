# TIME Polls Migration — Deploy Risk Review (Slice 5)

Ship gate for the 5-slice TIME-poll migration (issue hangoutsBackend-5mb). No further code changes planned in this slice; this doc records the pre-deploy risk surface, verifies the plan against what actually shipped, and lists outstanding coordination.

## Commits in scope

| SHA | Title |
|---|---|
| `e434ea4` | feat(polls): add TIME attribute substrate fields |
| `ece0c60` | feat(polls): TIME poll substrate (Slice 1) |
| `29677b1` | feat(polls): TIME poll service + EventBridge scheduling (Slice 2) |
| `dd8173d` | feat(polls): wire hangout edit-path to TIME poll service (Slice 3) |
| `b4e8dab` | feat(polls): demolish TimeSuggestion stack (Slice 4) |

`./gradlew test` = BUILD SUCCESSFUL (all tests up-to-date, no failures).

## "Easy to get wrong" checklist (from migration plan §Easy to get wrong)

| # | Item | Status | Notes |
|---|---|---|---|
| 1 | Schedule leak (doubled) | ✅ Addressed | `TimePollService.cancelSchedulesFor` funnels through `TimePollScheduler.cancelBoth`, called from `onPollDeleted`, `onSupersede`, `onHangoutDeleted`, and `adopt()`. |
| 2 | `HangoutPointer.timeSuggestions` ghost attribute | ⚠️ Residual | Field + factory-clearing rule removed in Slice 4. Existing DDB pointer rows still carry the attribute until their next write. No reader touches it; safe. Worth a manual check in staging that first write scrubs it (Enhanced Client semantics — TBC in smoke). |
| 3 | `CreatePollRequest.options` compat | ✅ Addressed | Polymorphic deserializer (`PollOptionInputDeserializer`) accepts `List<String>` and `List<PollOptionInput>`. Both shapes covered by `PollServiceImplTest`. |
| 4 | `PollOption.timeInput` DDB registration | ✅ Addressed | `PollOption.timeInput` has `@DynamoDbAttribute`; `TimeInfo` has `@DynamoDbBean`. Covered by existing unit tests; integration-round-trip coverage leans on the staging smoke in §Smoke checklist below. |
| 5 | Vote idempotency vs. old behavior | ✅ Addressed | `PollServiceImpl.voteOnPoll` returns existing vote when user re-votes same option; tests updated. |
| 6 | Authorization parity | ✅ **Acceptable (see §Auth parity below)** | New path is at least as restrictive as old for the only-hangouts-with-associated-groups case that production actually has. |
| 7 | Pointer size growth | ⚠️ **Track (see §Pointer-size assessment below)** | 5-suggestion cap lost; each TIME option adds a `PollOption` + `TimeInfo` + vote rows to denormalized pointer. Ship as-is, telemeter. |
| 8 | Single-active-TIME race | ⚠️ Best-effort | `hasActiveTimePoll` is a query-before-save; no conditional-write sentinel. Two concurrent creates could both win. Low-risk for current UI flows (one client, one create). Plan explicitly calls this out as "ship as-is." |
| 9 | Duplicate options break fast path | ✅ Addressed | `addPollOption` runs `isEquivalentTimeInput` dedupe guard; `PollServiceImplTest` covers both fuzzy-bucket and exact-start/end equivalence. |
| 10 | `MIN_TIME_SUGGESTION_VERSION` drift | ⚠️ **Unpinned — blocks gating (see §Open coordination)** | Config default is sentinel `"UNKNOWN"`; `computeCanAddOptions` returns `true` whenever sentinel is set (fail-open). Correct behavior, but the gate is currently a no-op in all environments. Must be pinned before gating actually engages. |
| 11 | Timezone in generated text | ✅ Addressed | `TimePollOptionTextGenerator` uses the hangout/group timezone path via `FuzzyTimeService`; no UTC rendering. Display-only — new clients ignore. |
| 12 | Momentum recompute on adoption | ✅ Addressed | `TimePollService.adopt` calls `momentumService.recomputeMomentum(hangoutId)` (wrapped in try/catch — non-fatal). |
| 13 | Group timestamp invalidation | ✅ Addressed | Every write path (`createPoll`, `addPollOption`, `voteOnPoll`, `removeVote`, `deletePoll`, `onSupersede`, `adopt`) routes through `updatePointersWithPolls`/`updatePollPointers` which calls `groupTimestampService.updateGroupTimestamps`. |
| 14 | `ClientInfo` null defaults | ✅ Addressed | `computeViewable` returns `true`; `computeCanAddOptions` returns `true` when `ClientInfo` is null or version config is sentinel. |
| 15 | Handler failure resilience | ✅ Addressed | `evaluateAndAdopt` short-circuits on `!isActive || promotedAt != null`; `adopt()` is idempotent under re-entry because the first step flips `isActive=false, promotedAt=now` before any hangout mutation. |

## Plan-vs-implementation divergences

All intentional; none are blockers.

1. **`evaluateAndAdopt` signature** — plan specifies `evaluateAndAdopt(String pollId)`; implementation is `evaluateAndAdopt(String hangoutId, String pollId)`. Threading the `hangoutId` through the EventBridge payload avoids a GSI lookup. Harmless.
2. **Scheduler config property names** — reused `time-suggestion.auto-adoption.short-window-hours` / `long-window-hours` from the legacy TimeSuggestion feature flags. Not renamed to keep staging config parity; values default to 24/48 and are not tuned per env.
3. **`TimePollConfig.UNKNOWN_MIN_VERSION` sentinel** — not in the plan; introduced so an unpinned config can't silently gate all TIME-aware clients. Fail-open until operators set `time-polls.min-suggestion-version`. Documented inline in `TimePollConfig.java` and plan item N10.
4. **`onSupersede` cancels schedules and flips `isActive=false`, but does not delete the polls.** This matches the contract in `TIME_POLL_API_CONTRACT.md` (polls remain inactive + visible). Plan wording said "flips all active TIME polls to `isActive=false`"; no divergence on behavior.
5. **Poll list in pointer is repainted unconditionally on every poll mutation** via `updatePointersWithPolls`. No per-poll diff. Matches existing LOCATION/DESCRIPTION flow; flagged under §Pointer-size assessment.

## Auth parity (plan item N6)

- **Old TimeSuggestion path** (git-show on commit `b4e8dab~1`): `requireGroupMember(groupId, userId)` → `groupRepository.isUserMemberOfGroup(groupId, userId)`. Single-group check against the URL `groupId`.
- **New TIME-poll path**: creates/adds go through `canUserEditHangout`; votes through `canUserViewHangout`. Both resolve to "user is a member of *any* associated group of the hangout." Today `canUserEditHangout == canUserViewHangout` (see `AuthorizationServiceImpl:44-48`).
- **Divergence**: the new check iterates `hangout.getAssociatedGroups()` and loads each group's membership list (`findMembersByGroupId`) instead of a point membership lookup. Semantically equivalent in the current model (hangouts without associated groups are non-viewable; no "public hangout" actually exists), but costlier per call.
- **Decision**: accept. No explicit group-member check needed at the TIME-specific call sites. If `canUserEditHangout` diverges from `canUserViewHangout` in the future (e.g., host-only edits), add-option and create-TIME-poll must be re-evaluated — today they allow any member to create/add, matching the prior TimeSuggestion feature.

## Pointer-size assessment (plan item N7)

The HangoutPointer denormalizes `polls: List<Poll>`, `pollOptions: List<PollOption>`, `votes: List<Vote>` — unbounded by design. Pre-migration, the 5-item cap on `timeSuggestions` bounded its contribution to pointer size; that cap is gone.

**Per-TIME-poll incremental size (rough):**
- 1× `Poll` row: ~400 bytes (title, attributeType, timestamps, scheduledFinalAdoptionAt).
- N× `PollOption` rows: ~250 bytes + `TimeInfo` (~120 bytes). For a typical 3-option poll: ~1.1KB.
- M× `Vote` rows: ~150 bytes each. At 5 voters × 3 options (worst non-adversarial): ~2.3KB.
- **Typical added footprint per TIME poll: ~2–4KB.** Well under DynamoDB's 400KB item limit for any reasonable hangout.

**Risk**: adversarial or buggy add-option spam. Dedupe guard + client UI + single-active invariant together make this unlikely but not impossible. **No hard cap added in this slice.** Action: add a CloudWatch alarm on pointer item size P99 (recommended as follow-up bead) and be ready to introduce a per-poll option cap (e.g., 10) if the alarm fires.

**Verdict**: Not material at ship time. Monitor.

## Rollback plan

All five commits are additive + a single cleanup commit. Rollback is a single revert **per commit in reverse order**, or a `git revert` of the merge if shipped as one PR. The plan asserts "single revert viability"; actual shipped history is 5 linear commits on `main`. Viable revert paths:

1. **Preferred** (if shipped as merge): `git revert -m 1 <merge-sha>` restores TimeSuggestion stack entirely. Schedules created before revert will fire against a no-op listener (the `POLL_ADOPTION` message type will be unknown post-revert) — safe no-op.
2. **Manual** (5 linear commits, as currently staged): revert Slice 4 → 3 → 2 → 1 → substrate, in order. Each commit is compile-clean in isolation based on dependency structure.

**Cannot revert cleanly if:** any TIME poll has been written to DynamoDB in prod and readers still expect the old `timeSuggestions` pointer field. Pre-revert check: purge any `Poll` rows with `attributeType=TIME` before re-deploying legacy stack.

**EventBridge cleanup on rollback**: outstanding `poll-adopt-{pollId}-24h` / `...-48h` schedules will remain in the schedule group after revert and fire against a nonexistent handler. Safe (message dropped) but cosmetically noisy. Schedule group ages out; manual cleanup optional.

## Smoke test checklist (plan §Rollout — verified against built artifact)

Built artifact: `build/libs/inviter-0.0.1-SNAPSHOT.jar` (timestamp current against HEAD).

To run post-deploy in staging:

- [ ] **T1** — `POST /hangouts/{id}/polls` with `attributeType="TIME"` and one `timeInput` option. Verify 201 + poll appears in `GET /groups/{g}/feed` under `polls[]` with populated `timeInput` on the option.
- [ ] **T2** — Second user hits `POST /hangouts/{id}/polls/{pollId}/vote` for the option. Verify `voteCount=1`, `userVoted=true` for voter on re-fetch.
- [ ] **T3** — Re-vote same option → 200 idempotent. Vote count unchanged.
- [ ] **T4** — Fast-forward or manually invoke `evaluateAndAdopt` at T ≥ 24h. Verify hangout `timeInput`/`startTimestamp`/`endTimestamp` set; poll `isActive=false, promotedAt != null`; pointer reflects it; group ETag rolled.
- [ ] **T5** — On a separate test hangout with an active TIME poll, PATCH hangout time directly. Verify poll flips `isActive=false`; both `poll-adopt-{pollId}-*` schedules cancelled in EventBridge console.
- [ ] **T6** — `GET /groups/{g}/feed` with `X-App-Version: 0.0.1` → **current state with sentinel:** `canAddOptions=true` on both TIME and LOCATION polls (fail-open). Once `time-polls.min-suggestion-version` is pinned, re-run: TIME returns `false`, LOCATION returns `true`.
- [ ] **T7** — Pre-deploy DDB scan for `itemType="TIME_SUGGESTION"` rows. If any exist from manual testing, confirm no code path reads them post-deploy (spot-check `HangoutRepositoryImpl`).
- [ ] **T8** — Write an arbitrary pointer-producing operation on a hangout that previously had a `timeSuggestions` attribute, confirm the attribute disappears or is left inert on next pointer write (plan item N2 verification).
- [ ] **T9** — Create TIME poll with 2 equivalent `timeInput` options (should fail dedupe on second add) → 400 `VALIDATION_ERROR` "Duplicate time option".
- [ ] **T10** — POST a legacy `options: ["foo"]` with `attributeType="TIME"` → 400 `VALIDATION_ERROR` with exact message `"Adding time options requires an updated app version."`.

## Open coordination items

1. **[BLOCKS real gating]** `time-polls.min-suggestion-version` must be set in `application.properties` (or env override) to the version iOS/Android ship with the TIME poll UI. Until then, `canAddOptions` fails open. Not a deploy blocker — backend can ship first without gating, clients gate themselves by feature-detecting `timeInput` in responses — but the config must be pinned once iOS/Android versions are cut. **Owner: iOS + Android teammates → backend.**
2. **iOS migration**: stop reading `summary.timeSuggestions[]` (field is gone; old iOS will get null and should gracefully fall back). Read from `summary.polls[]` filtered by `attributeType == "TIME"`. See `TIME_POLL_API_CONTRACT.md`.
3. **Android migration**: same contract swap as iOS.
4. **Observability follow-up (file as bead)**: CloudWatch alarm on HangoutPointer item size P99 to catch pointer bloat before it hits the 400KB DDB limit.
5. **Observability follow-up (file as bead)**: CloudWatch metric on `POLL_ADOPTION` schedule fire count vs. adopted poll count to spot handler-failure regressions.
6. **Cleanup follow-up (file as bead)**: drop the legacy `time-suggestion.auto-adoption.*` property names in favor of `time-polls.*` once we're confident the TIME poll feature won't need to roll back.

## Ship recommendation

**Go.** The migration is additive on the substrate side (new model fields, new service), idempotent on re-entry in every hot path, and has a clear single-revert rollback. The two residual risks — pointer size growth and the unpinned `MIN_TIME_SUGGESTION_VERSION` — are both non-blockers: the first is mitigated by monitoring and a fallback cap we can add on short notice; the second fails open, so shipping without coordinating iOS/Android doesn't break clients, it just means the gate doesn't engage until config is pinned.
