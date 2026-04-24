# TIME Polls Slice 1 — Test Plan

Scope: unit-test the poll-substrate changes landed in this slice. Integration tests for
the full round-trip (create → add option → vote → adoption) belong with Slice 2 once
`TimePollService` exists.

## Unit tests (added in `PollServiceImplTest.TimePollTests`)

| # | Test | What it verifies |
|---|------|------------------|
| 1 | `createPoll_TimeAttribute_ValidatesAndGeneratesText` | TIME poll create calls `FuzzyTimeService.convert()` on every option and saves a `PollOption` with populated `timeInput` and a non-blank, server-generated `text`. |
| 2 | `createPoll_TimeAttribute_WithLegacyStringOptions_Rejects` | `attributeType="TIME"` + legacy `List<String>` options → 400 with message `"Adding time options requires an updated app version."`. |
| 3 | `createPoll_TimeAttribute_WhenActiveTimePollExists_Rejects` | Second TIME poll create on same hangout → 400 `"Hangout already has an active time poll"`. |
| 4 | `addPollOption_OnTimePoll_WithoutTimeInput_Rejects` | TIME poll add-option without `timeInput` → 400 with the standard update-app-version message. |
| 5 | `addPollOption_OnTimePoll_DedupesEquivalentTimeInput` | Adding an option whose `timeInput` matches an existing option's fuzzy bucket+start → 400 `"Duplicate time option"`. |
| 6 | `addPollOption_OnTimePoll_AcceptsNewEquivalent_WhenDifferent` | Different fuzzy day passes the dedupe guard and saves with `timeInput` populated. |
| 7 | `voteOnPoll_SameOptionTwice_IsIdempotent` | Re-voting for the same option returns the existing `Vote` without calling `saveVote`/`deleteVote`. |

## Pre-existing coverage that must stay green

- All `PollServiceImplTest` non-TIME tests (LOCATION/DESCRIPTION/plain) pass unchanged —
  confirms the polymorphic `CreatePollRequest.options` deserializer and the new
  `setOptionsFromStrings(List<String>)` helper preserve the legacy shape.
- `HangoutServiceCreationTest` polls coverage: hangout-creation poll options still accept
  plain-string shape through `PollOptionInput.getText()`.
- `PollControllerTest` compiles and runs against the new DTO.

## Deferred to Slice 2

These rows from `TIME_POLL_MIGRATION_PLAN.md` §Stream E are Slice 2 work because they
depend on `TimePollService` or `TimePollScheduler` that don't exist yet:

- Evaluator matrix unit test
- Sliding window test
- `canAddOptions` live-version gating end-to-end test (needs wired config + controller)
- Direct-edit supersession test
- Round-trip integration test
- Handler re-evaluation test
- Feed/detail response test

A stub version of the `canAddOptions` gate is covered implicitly by the default-open path
until `time-polls.min-suggestion-version` is pinned to a real client version.

## Manual verification steps (post-merge local)

1.  `./gradlew test` → BUILD SUCCESSFUL.
2.  `./gradlew bootRun`, then `curl -X POST .../hangouts/{id}/polls` with
    `attributeType=TIME` and `options: [{timeInput: {...}}]` → 201.
3.  Repeat the same request → 400 with `"Hangout already has an active time poll"`.
4.  `POST .../polls/{pollId}/options` with only `text` on the TIME poll → 400 with the
    update-app-version message.
5.  Cast a vote twice for the same option → both requests 200, vote count remains 1.
