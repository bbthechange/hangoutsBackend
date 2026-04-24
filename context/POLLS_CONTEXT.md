# Context: Polls

**AUDIENCE:** This document is for developers and AI agents working on the polling feature. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md` and `HANGOUT_CRUD_CONTEXT.md`.

## 1. Overview

Polls are a feature within a Hangout that allow group members to vote on options. The entire data model for polls is self-contained within a Hangout's item collection, making it a prime example of the single-table design pattern.

All poll-related entities share the same partition key as their parent hangout (`PK=EVENT#{hangoutId}`) but have distinct sort keys, allowing for efficient, targeted queries.

## 2. Data Model & Key Structure

| Entity | Sort Key (SK) Structure | Purpose |
| :--- | :--- | :--- |
| `Poll` | `POLL#{pollId}` | The canonical record for the poll itself, containing the title and settings. |
| `PollOption` | `POLL#{pollId}#OPTION#{optionId}` | A single option that users can vote for within a poll. |
| `Vote` | `POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}` | Records a single user's vote for a specific option. The composite SK ensures each user can only vote once per option. |

This hierarchical key structure is powerful because it allows fetching all data for a single poll with one query using a `begins_with` condition on the sort key.

## 3. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `PollController.java` | Exposes REST endpoints for poll operations, nested under `/hangouts/{eventId}/polls`. |
| `PollServiceImpl.java` | Implements the business logic for creating polls, voting, and calculating results. |
| `HangoutRepositoryImpl.java` | Contains the methods (`savePoll`, `savePollOption`, `saveVote`, `getSpecificPollData`) that interact with DynamoDB for poll entities. |
| `Poll.java` | The `@DynamoDbBean` for the poll record. |
| `PollOption.java` | The `@DynamoDbBean` for a poll option record. |
| `Vote.java` | The `@DynamoDbBean` for a user's vote record. |
| `PollWithOptionsDTO.java` | A DTO used to return poll data with calculated vote counts. Each `PollOptionDTO` includes a `votes` list with `userId`, `voteType`, and `displayName`. Also carries `isActive`, `viewable`, `canAddOptions` (computed per-request from `ClientInfo`). |
| `PollOptionInput.java` / `PollOptionInputDeserializer.java` | Wire shape for `CreatePollRequest.options` — polymorphic: accepts legacy `List<String>` OR `List<{text?, timeInput?}>`. |
| `TimePollOptionTextGenerator.java` | Server-generates `PollOption.text` for TIME options so clients without TIME awareness render a generic poll. Uses timezone embedded in the TimeInfo ISO string (creator's TZ). Never UTC. |
| `TimePollConfig.java` | Holds `MIN_TIME_SUGGESTION_VERSION`. Defaults to `"UNKNOWN"`; `canAddOptions` stays `true` until the config is pinned to a real client version. |
| `TimePollService.java` | Lifecycle hooks for TIME polls: `onPollCreated`/`onOptionAdded` (EventBridge scheduling), `evaluateAndAdopt` (5-way matrix handler entry), `onSupersede`/`onPollDeleted`/`onHangoutDeleted` (cancellation paths — all route through `cancelSchedulesFor`). |
| `TimePollScheduler.java` | EventBridge wrapper for TIME polls. Creates `poll-adopt-{pollId}-24h` (fixed) and `poll-adopt-{pollId}-48h` (sliding). Payload: `{type: "POLL_ADOPTION", hangoutId, pollId}`. |
| `ScheduledEventListener.java` | Routes `POLL_ADOPTION` messages from SQS to `TimePollService.evaluateAndAdopt(hangoutId, pollId)`. |
| `VoteDTO.java` | Individual vote with `userId`, `voteType`, and `displayName` (enriched from username cache). |

## 4. Core Flows

### Create a Poll

1.  **Endpoint:** `POST /hangouts/{eventId}/polls`
2.  **Controller:** `PollController.createPoll()`
3.  **Service:** `PollServiceImpl.createPoll()`:
    *   Creates a `Poll` entity.
    *   Iterates through the list of option strings in the request and creates a `PollOption` entity for each one.
4.  **Repository:** `HangoutRepositoryImpl` is called multiple times to save the `Poll` and each `PollOption` individually.

### Vote on a Poll

1.  **Endpoint:** `POST /hangouts/{eventId}/polls/{pollId}/vote`
2.  **Controller:** `PollController.voteOnPoll()`
3.  **Service:** `PollServiceImpl.voteOnPoll()`:
    *   First, it fetches all data for the poll to check its settings (e.g., `multipleChoice`).
    *   If the poll is single-choice, it removes the user's previous vote (if any) before creating the new one.
    *   It then creates a `Vote` entity with the composite sort key.
4.  **Repository:** `HangoutRepositoryImpl.saveVote()` saves the new `Vote` record.

### Get Poll Results

This is the most critical flow to understand.

1.  **Endpoint:** `GET /hangouts/{eventId}/polls/{pollId}`
2.  **Controller:** `PollController.getPoll()`
3.  **Service:** `PollServiceImpl.getPollDetail()`
4.  **Repository:** `HangoutRepositoryImpl.getSpecificPollData()`:
    *   Executes a single DynamoDB **Query**.
    *   `PK` = `EVENT#{eventId}`
    *   `SK` = `begins_with(POLL#{pollId})`
    *   This one query efficiently retrieves the `Poll` record, all of its `PollOption` records, and all of its `Vote` records.
5.  **Runtime Calculation:** Back in `PollServiceImpl`, the `transformToPollDetailDTO` method receives this list of items. It then iterates through the `Vote` records in application memory to calculate the total votes for each option before building the final DTO to send to the client. **Vote counts are not stored or updated in DynamoDB.**
6.  **Display Name Enrichment:** Each `VoteDTO` is enriched with the voter's `displayName` via `UserService.getUserSummary()` (Caffeine-cached). This happens in `PollServiceImpl.transformToPollDetailDTO()` for the poll detail endpoint, and in `HangoutServiceImpl.getHangoutDetailInternal()` for the hangout detail endpoint.

## 5. TIME Polls (Slice 1 substrate)

TIME polls are regular polls with `attributeType == "TIME"` and typed `timeInput: TimeInfo` on each `PollOption`. See `TIME_POLL_MIGRATION_PLAN.md` for the full plan and `TIME_POLL_API_CONTRACT.md` for the iOS/Android-facing contract.

Slice 1 delivers the poll substrate changes only; Slice 2 delivers `TimePollService` (auto-adoption schedules, supersession, promotion).

Key substrate rules enforced in `PollServiceImpl`:

1.  **Single active TIME poll per hangout.** `createPoll` queries existing polls first and rejects with `ValidationException` (→ 400 VALIDATION_ERROR) if an active TIME poll already exists. *Race note*: the check is query-before-save; Slice 2's `TimePollService` will add a sentinel marker + EventBridge coordination for full hardening.
2.  **TIME options must carry `timeInput`.** `createPoll` and `addPollOption` reject TIME options that lack `timeInput`, and reject legacy `List<String>` options on TIME polls, with the exact message `"Adding time options requires an updated app version."`.
3.  **Server-generated text.** For TIME options, `PollOption.text` is generated via `TimePollOptionTextGenerator` so clients without TIME awareness render as a generic poll. Timezone comes from the ISO string embedded in `timeInput` (creator's TZ). Never UTC.
4.  **Shape validation.** Every `timeInput` on create and add-option runs through `FuzzyTimeService.convert()` so bad shapes fail fast.
5.  **Dedupe guard.** `addPollOption` rejects an option whose `timeInput` is equivalent (same fuzzy bucket+start, or same exact start/end) to an existing option. Prevents the fast-path-break described in plan item 9.
6.  **Idempotent vote re-cast.** `voteOnPoll` now returns the existing `Vote` and no-ops when the user votes for an option they already voted for. Applies to all polls, not just TIME. Previous behavior (throwing `IllegalStateException`) was removed; any test asserting that behavior was updated.
7.  **`isActive`, `viewable`, `canAddOptions` on `PollWithOptionsDTO`.** Computed at transformation time. `viewable` is currently a `true` stub. `canAddOptions` is `true` for non-TIME polls; for TIME polls it gates on `ClientInfo.appVersion >= TimePollConfig.minTimeSuggestionVersion`. If ClientInfo is missing or `MIN_TIME_SUGGESTION_VERSION` is `"UNKNOWN"` (the default until clients pin it), defaults to `true`. **This is a client-capability gate, not an authorization decision.**

### Config
- `time-polls.min-suggestion-version` in `application.properties` — the minimum iOS/Android version that ships with TIME poll UI. Must be set to the actual version before Slice 2 deploys. Default `"UNKNOWN"` disables the gate so it never mis-fires in dev.
