# Context: Momentum Feature

**AUDIENCE:** This document is for developers and AI agents working on the hangout momentum system. It assumes familiarity with `HANGOUT_CRUD_CONTEXT.md` and `DYNAMODB_DESIGN_GUIDE.md`.

## 1. Overview

Hangout creation transitions from binary (scheduled/not) to a spectrum: **suggestion → gathering momentum → confirmed**. The system reads behavioral signals and adjusts presentation automatically via three momentum states. No one manually sets a status (except optional explicit confirmation).

Two creation modes:
- **"Float it"** — creates a suggestion. Creator is auto-marked as Interested. State = BUILDING.
- **"Lock it in"** — creates a confirmed hangout. Creator is auto-marked as Going. State = CONFIRMED.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `MomentumCategory.java` | Enum: `BUILDING`, `GAINING_MOMENTUM`, `CONFIRMED` |
| `MomentumService.java` | Interface for momentum operations |
| `MomentumServiceImpl.java` | Scoring algorithm, threshold computation, Caffeine cache |
| `MomentumDTO.java` | API response DTO with normalized score (0-100) |
| `Hangout.java` | Canonical record — has 5 momentum fields |
| `HangoutPointer.java` | Pointer record — same 5 momentum fields (denormalized) |
| `HangoutPointerFactory.java` | Copies momentum fields in `applyHangoutFields()` |
| `HangoutServiceImpl.java` | Integrates momentum into create/update/RSVP flows |
| `GroupServiceImpl.java` | Feed filter parameter (`ALL`, `CONFIRMED`, `EVERYTHING`) |

## 3. Momentum States

| State | How you get there | Description |
|-------|-------------------|-------------|
| **BUILDING** | Default for "Float it" hangouts | Suggestion phase, gathering interest |
| **GAINING_MOMENTUM** | Auto-promoted when score crosses dynamic threshold | Active interest, people responding |
| **CONFIRMED** | "Lock it in" at creation, manual "It's on!", concrete action, or auto-promotion at high score (requires date) | Definitely happening |

**BUILDING has two sub-states, computed at read time** (not persisted): `fresh` (`now - createdAt ≤ momentum.tuning.fresh-float-age-days`, default 5) and `stale` (older). Only persisted category remains `BUILDING`. See Section 12.

**No FADING state stored** — fading is the read-time behavior of stale BUILDING items (see Section 12). Clients may use the `surfaceReason` field on each hangout to render distinct treatments.

**Never demote from CONFIRMED** — once confirmed, always confirmed.

## 4. Scoring Algorithm

### Two-Tier Scoring

The system uses two separate scores to prevent low-commitment "Interested" signals from auto-confirming hangouts:

- **momentumScore** (includes Interested) — drives BUILDING → GAINING_MOMENTUM. Appropriate because Interested signals indicate visibility/traction.
- **confirmScore** (excludes Interested) — drives GAINING_MOMENTUM → CONFIRMED. Only strong commitment signals (Going RSVPs, planning actions) can auto-confirm.

### Signal Weights

| Signal | momentumScore | confirmScore |
|--------|--------------|-------------|
| Going RSVP | +3 each | +3 each |
| Interested RSVP | +1 each | **not counted** |
| Time added (startTimestamp != null) | +1 | +1 |
| Location added (location != null) | +1 | +1 |
| Concrete action (TICKET_PURCHASED participation, or carpool riders) | Instant CONFIRMED | Instant CONFIRMED |

### Multipliers (compound — both can apply)

| Multiplier | Condition | Factor |
|------------|-----------|--------|
| Recency | Any InterestLevel updated in last 48h | ×1.5 |
| Time proximity (within 48h) | startTimestamp within 48 hours | ×1.5 |
| Time proximity (within 7d) | startTimestamp within 7 days | ×1.2 |

Multipliers stack: a hangout 2 days away with recent engagement gets ×1.5 × ×1.2 = ×1.8. Both scores receive the same multipliers.

### Dynamic Threshold

```
threshold = ceil(activeMembers × engagementMultiplier × 0.4)
```

- **activeMembers** = total group members (TODO: refine to members active in last 8 weeks)
- **engagementMultiplier** = default 0.6, clamped [0.3, 1.0] (TODO: compute from rolling 8-week confirmation rate)
- Minimum threshold = 1

Example: 8 members × 0.6 × 0.4 = threshold of 2.

### Promotion Rules

- **BUILDING → GAINING_MOMENTUM**: momentumScore ≥ threshold (Interested counts here)
- **GAINING_MOMENTUM → CONFIRMED**: confirmScore ≥ threshold × 2 AND hangout has a date (Interested excluded)
- **Concrete action**: instant CONFIRMED regardless of score
- **Date required for auto-confirm**: dateless hangouts cap at GAINING_MOMENTUM (can still be manually confirmed)

## 5. Data Model

### Fields on Hangout and HangoutPointer

| Field | Type | Purpose |
|-------|------|---------|
| `momentumCategory` | `MomentumCategory` | Current state |
| `momentumScore` | `Integer` | Raw score (internal, not normalized) |
| `confirmedAt` | `Long` | Epoch millis when confirmed |
| `confirmedBy` | `String` | User ID who confirmed, or "SYSTEM" for auto-promotion |
| `suggestedBy` | `String` | Creator user ID for "Float it" hangouts |

All fields are copied via `HangoutPointerFactory.applyHangoutFields()`.

### MomentumDTO (API Response)

```json
{
  "score": 45,
  "category": "GAINING_MOMENTUM",
  "confirmedAt": null,
  "confirmedBy": null,
  "suggestedBy": "user-456"
}
```

- **score**: Normalized 0-100 (detail view uses threshold-based normalization; feed uses raw score from pointer)
- **category**: String enum value
- **confirmedAt/confirmedBy**: Only present when CONFIRMED
- **suggestedBy**: Only present for "Float it" hangouts

## 6. MomentumService Methods

| Method | Purpose | Called From |
|--------|---------|------------|
| `initializeMomentum(hangout, confirmed, userId)` | Set initial state on new hangout | `HangoutServiceImpl.createHangout()` |
| `recomputeMomentum(hangoutId)` | Full score recompute from current signals | After RSVP, time/location/ticket changes |
| `confirmHangout(hangoutId, userId)` | Manual "It's on!" confirmation | Standalone use only (NOT from updateHangout — see note) |
| `buildMomentumDTO(hangout, groupId)` | Build normalized DTO for detail view | `HangoutServiceImpl.getHangoutDetail()` |
| `buildMomentumDTOFromPointer(pointer)` | Build DTO from pointer for feed | `HangoutSummaryDTO` constructor |

**Important:** `confirmHangout()` loads a separate Hangout copy from DB. Do NOT call it within `updateHangout()` — the local hangout save would overwrite the confirmation. Instead, set momentum fields directly on the local hangout object within `updateHangout()`.

## 7. Integration Points (Where recomputeMomentum Is Called)

| Trigger | Location | When |
|---------|----------|------|
| RSVP set/changed | `HangoutServiceImpl.setUserInterest()` | After saving interest level |
| Time/location added | `HangoutServiceImpl.updateHangout()` | After saving canonical if time or location changed |
| Ticket fields changed | `HangoutServiceImpl.updateHangout()` | After saving canonical if ticket fields changed |
| Ticket purchased | `ParticipationServiceImpl.createParticipation()` | After creating TICKET_PURCHASED participation |
| Ticket status changed | `ParticipationServiceImpl.updateParticipation()` | After updating type to TICKET_PURCHASED |
| Offer completed | `ReservationOfferServiceImpl.completeOffer()` | After batch converting TICKET_NEEDED → TICKET_PURCHASED |
| Carpool rider added | Future integration point | After saving rider |

`recomputeMomentum` is idempotent — safe to call multiple times.

## 8. Caching Strategy

Group engagement data uses a Caffeine cache:
- **Key:** groupId
- **Value:** `GroupEngagementData(int activeMembers, double engagementMultiplier)`
- **Max size:** 500 entries
- **TTL:** 1 hour (engagement data doesn't need real-time precision)
- **Fallback on error:** 5 active members, 0.6 multiplier

## 9. Confirmation Paths

1. **At creation** — "Lock it in" button sends `confirmed: true` in `POST /hangouts`
2. **Post-creation by anyone** — "It's on!" sends `confirmed: true` in `PATCH /hangouts/{id}`
3. **Concrete action** — ticket purchased (TICKET_PURCHASED participation exists) or carpool rider added → auto-confirms via `recomputeMomentum`. Note: ticket metadata (ticketsRequired + ticketLink) is informational only and does NOT trigger confirmation.
4. **Score-based** — crossing upper threshold (score ≥ threshold × 2) auto-confirms (requires date)

## 10. Feed Filter

The feed endpoint (`GET /groups/{groupId}/feed`) accepts a `filter` query parameter:

| Filter | Behavior |
|--------|----------|
| `ALL` (default) | Returns all hangouts |
| `CONFIRMED` | Returns hangouts where `momentumCategory == CONFIRMED` or `null` (legacy hangouts treated as confirmed) |
| `EVERYTHING` | Same as ALL for now (fading is expressed via `surfaceReason` — clients render accordingly) |

Filtering is post-query on both `withDay` and `needsDay` lists.

Every hangout in the response carries a `surfaceReason` field: `CONFIRMED | GAINING | FRESH_FLOAT | SUPPORTED_FLOAT | STALE_FILLER`. Ideas surfaced via forward-fill carry `SUPPORTED_IDEA | UNSUPPORTED_IDEA`. Clients use this to render fresh/fade/confirmed distinctions without re-deriving categories.

## 11. Backward Compatibility

- Momentum fields are additive — old clients ignore them
- `confirmed` field defaults to null (treated as false/float)
- Hangouts created by old clients get `momentumCategory=null` — clients should treat null as legacy/CONFIRMED behavior
- Feed sorting uses slot-based interleaving (see Section 12)

## 12. Slot-Based Feed Interleaving (Feature 1)

**Status: Implemented.** `GroupServiceImpl.getGroupFeed()` now routes through `FeedSortingService` to apply slot-based ordering.

### Key Files

| File | Purpose |
|------|---------|
| `service/impl/FeedSortingService.java` | Core sort algorithm — buckets by time horizon, applies smart surfacing |
| `service/impl/GroupServiceImpl.java` | Calls `feedSortingService.sortFeed()` before returning feed |

### Time Horizon Buckets (in order)

| Horizon | Window | Description |
|---------|--------|-------------|
| IMMINENT | ≤ 48h | Exempt from busy-week suppression |
| NEAR_TERM | ≤ 7 days | Busy-week and empty-week rules apply |
| MID_TERM | ≤ 21 days | Same smart-surfacing rules |
| DISTANT | > 21 days | Suppression-exempt (like imminent) |
| needsDay | No timestamp (floating) | Sorted by momentum category only. Floating hangouts are queried from UserGroupIndex via `begins_with(gsi1sk, "FLOATING#")` in `getFloatingHangoutsPage()` |

### Sort Order Within Each Horizon

`CONFIRMED → GAINING_MOMENTUM → BUILDING`, then chronological within each group. Series items are treated as CONFIRMED.

### Fresh vs. Stale Classification (BUILDING only)

Computed at read time from `HangoutPointer.createdAt`:

- **Fresh**: `now - createdAt ≤ freshFloatAgeDays` (default 5d).
- **Stale**: older than the threshold, or `createdAt == null` (conservative).

The two sub-states drive different surfacing behavior — see below.

### Smart Surfacing Rules for BUILDING Items

- **Fresh floats (`FRESH_FLOAT`)**: Always surface. Bypass busy-week suppression. No cap. Appear in the normal category slot (BUILDING after GAINING_MOMENTUM) within their horizon, chronologically within the BUILDING group. In `needsDay` they similarly always surface.
- **Stale-supported floats** (stale + `interestLevels.size() > 1`, labeled `SUPPORTED_FLOAT`):
  - **Busy week** (confirmed item in same horizon): Suppressed unless they have a recent support surge (≥ `recentSupportMinSignals` signals in last `recentSupportWindowHours`).
  - **Empty week / needsDay**: Surface normally.
  - **Imminent and Distant horizons**: Always exempt from busy-week suppression.
- **Stale-unsupported floats** (stale + `interestLevels.size() ≤ 1`): Held back by `FeedSortingService` in `SortResult.heldStaleFloats`. Not surfaced unless the forward-fill service resurfaces them (see Section 13) with `surfaceReason = STALE_FILLER`.
- **Null/legacy momentum**: Items with null momentum category are treated as CONFIRMED for sorting and filtering purposes (`surfaceReason = CONFIRMED`).
- **No zero-support cap**: The old `MAX_ZERO_SUPPORT_BUILDING` cap was removed. Fresh floats always surface (by design); stale-unsupported are already held back. There are no remaining paths where a zero-support item would need a global cap.

### Tuning Knobs

All numeric thresholds live in `MomentumTuningProperties` (`momentum.tuning.*`). Defaults match the values above.

```
momentum.tuning.fresh-float-age-days = 5
momentum.tuning.forward-weeks-to-fill = 8
momentum.tuning.idea-min-interest-count = 3
momentum.tuning.recent-support-window-hours = 24
momentum.tuning.recent-support-min-signals = 2
momentum.tuning.imminent-horizon-hours = 48
momentum.tuning.near-term-horizon-days = 7
momentum.tuning.mid-term-horizon-days = 21
momentum.tuning.etag-time-bucket-seconds = 86400  # see GROUP_FEED_ETAG_CONTEXT.md
```

## 13. Forward-Fill Suggestions (Feature 2)

**Status: Implemented.** When the group's feed is requested, `ForwardFillSuggestionServiceImpl` fills the forward schedule with stale floats and ideas. Replaced the old all-or-nothing "idea suppression over 3 weeks" behavior.

### Key Files

| File | Purpose |
|------|---------|
| `service/ForwardFillSuggestionService.java` | Interface |
| `service/impl/ForwardFillSuggestionServiceImpl.java` | Budget + 3-tier priority fill |
| `service/impl/WeekCoverageCalculator.java` | Shared helper: counts covered/empty ISO weeks |
| `dto/IdeaFeedItemDTO.java` | Feed item DTO for an idea suggestion |
| `service/impl/GroupServiceImpl.java` | Calls `getForwardFill()` and integrates results |

### Logic

1. **Empty-week budget**: `WeekCoverageCalculator.countEmptyWeeks()` scans the next `forwardWeeksToFill` (default 8) ISO weeks. Coverage is computed from the in-memory `List<BaseItem>` already fetched by `GroupServiceImpl` (future + in-progress + floating pages merged together) — no additional DynamoDB query. In-progress events cover the current week naturally (their `startTimestamp` maps to the current ISO week). A week is **covered** if it contains a visible timestamped hangout (CONFIRMED, GAINING, fresh BUILDING, or stale-supported BUILDING). Stale-unsupported BUILDING items are held back and do not count as covering. `SeriesPointer` aggregates are intentionally ignored — their episodes appear as individual `HangoutPointer` rows that already cover their own weeks.
2. **Dateless-suggestion deduction**: `GroupServiceImpl` counts *every* `HangoutSummaryDTO` in `sorted.needsDay` (any category — FRESH_FLOAT, GAINING, CONFIRMED, legacy-null, stale-supported) and passes the count as `needsDaySuggestionCount`. `budget = max(0, emptyWeeks - needsDaySuggestionCount)`. Dated items are intentionally NOT counted here — they already reduce `emptyWeeks` via `WeekCoverageCalculator`, so counting them twice would over-deduct. Dateless items have no week to cover but still occupy a suggestion slot in the user's view; without this deduction, a group with, say, 3 recent dateless GAINING hangouts would still get a full stack of idea suggestions on top.
3. **Priority ladder** (take items in order, stop when budget hits 0):
   1. **Stale floats** (`heldStaleFloats` from `FeedSortingService`), sorted by `interestLevels.size()` desc, `createdAt` desc as tiebreaker. Marked `surfaceReason = STALE_FILLER`, appended to `needsDay`.
   2. **Supported ideas** (`interestCount ≥ ideaMinInterestCount`), sorted by interest desc. Marked `SUPPORTED_IDEA`, appended to `withDay`.
   3. **Unsupported ideas** (`0 < interestCount < threshold`), sorted by interest desc. Marked `UNSUPPORTED_IDEA`, appended to `withDay`. Ideas with `interestCount == 0` are never surfaced.
4. **Graceful degradation**: Week coverage or idea list failures degrade to an empty result — feed still works.

### IdeaFeedItemDTO fields

`type="idea_suggestion"`, `ideaId`, `listId`, `groupId`, `ideaName`, `listName`, `imageUrl`, `note`, `interestCount`, `googlePlaceId`, `address`, `latitude`, `longitude`, `placeCategory`, `surfaceReason`

### Placement in the response

- **Stale filler floats** → appended to `needsDay` (they have no timestamp).
- **Supported / unsupported ideas** → appended to `withDay`, after the sorted hangout entries. This matches prior behavior and lets clients that already render ideas continue working without change. Clients sorting `withDay` by timestamp must treat `IdeaFeedItemDTO` (type `"idea_suggestion"`) as a tail section.

### Known limitations

- **No per-week assignment.** Stale floats land in `needsDay` as a bulk, ideas land in `withDay` as an appended group. Per-week placement is a deferred enhancement (Section 18).
- **No placeId-based de-dupe** between surfaced ideas and existing hangouts. `Address` DTO doesn't carry `googlePlaceId`, so overlap by place is not detected. Name-based de-dupe could be added later if noise becomes a problem.

## 14. Time Suggestions (Feature 3)

**Status: Implemented.** When a hangout has no time set, members can suggest fuzzy or specific times.

### Key Files

| File | Purpose |
|------|---------|
| `model/FuzzyTime.java` | Enum: TONIGHT, THIS_WEEKEND, SATURDAY, etc. |
| `model/TimeSuggestion.java` | DynamoDB entity. PK=EVENT#{hangoutId}, SK=TIME_SUGGESTION#{suggestionId} |
| `model/TimeSuggestionStatus.java` | ACTIVE, ADOPTED, REJECTED |
| `dto/CreateTimeSuggestionRequest.java` | Request body for creating a suggestion |
| `dto/TimeSuggestionDTO.java` | API response DTO |
| `service/TimeSuggestionService.java` | Interface |
| `service/impl/TimeSuggestionServiceImpl.java` | Business logic + auto-adoption |
| `service/TimeSuggestionSchedulerService.java` | Schedules EventBridge one-shot events for adoption checks |
| `controller/TimeSuggestionController.java` | REST endpoints |

### REST Endpoints

```
POST   /groups/{groupId}/hangouts/{hangoutId}/time-suggestions         — create suggestion
POST   /groups/{groupId}/hangouts/{hangoutId}/time-suggestions/{id}/support  — +1 a suggestion
GET    /groups/{groupId}/hangouts/{hangoutId}/time-suggestions         — list active suggestions
```

### Silence = Consent Auto-Adoption Rules

| Scenario | Window |
|----------|--------|
| Single suggestion + ≥1 supporter, no competition | `short-window-hours` (default 24h) |
| Single suggestion + 0 votes, no competition | `long-window-hours` (default 48h) |
| Multiple competing suggestions | Leave as poll; no auto-adopt |

**Triggering**: When a time suggestion is created, `TimeSuggestionSchedulerService` schedules two EventBridge one-shot events — one at `createdAt + shortWindowHours` and one at `createdAt + longWindowHours`. Both fire via SQS → `HangoutReminderListener` → `adoptForHangout()`, which is idempotent. Schedules auto-delete after execution.

```properties
time-suggestion.auto-adoption.short-window-hours=24
time-suggestion.auto-adoption.long-window-hours=48
```

### Adoption Flow

1. Mark suggestion as ADOPTED in DynamoDB.
2. If suggestion has `specificTime`, update `hangout.startTimestamp` and all pointers via `PointerUpdateService`.
3. Call `momentumService.recomputeMomentum()` so the time-added bonus propagates.

### DynamoDB Key Pattern

TimeSuggestion items live in the hangout's item collection:
- `PK = EVENT#{hangoutId}`
- `SK = TIME_SUGGESTION#{suggestionId}`
- `itemType = TIME_SUGGESTION`

They are retrieved with a `begins_with(sk, "TIME_SUGGESTION#")` query.

### Denormalization onto HangoutPointer

Active suggestions are denormalized onto each `HangoutPointer.timeSuggestions` so the group feed can render them without extra queries. Key rules:

- Stored as the lean `TimeSuggestionPointerView` projection (not the full `TimeSuggestion` bean) — nested `BaseItem` keys would bloat the pointer.
- Capped at 5 per pointer in `TimeSuggestionServiceImpl.propagateSuggestionsToPointers`, sorted by `(supportCount desc, createdAt desc)`.
- Propagation fires from `createSuggestion`, `supportSuggestion`, and adoption.
- `HangoutPointerFactory.applyHangoutFields` clears `timeSuggestions` whenever `hangout.startTimestamp != null` — so any pointer update for a dated hangout self-corrects. Adoption and direct host time edits both rely on this rule.
- Direct time edit (PATCH with `timeInfo` that resolves to a concrete `startTimestamp`) calls `TimeSuggestionService.invalidateActiveSuggestions`, which marks canonical rows `REJECTED` and cancels their EventBridge adoption schedules. This prevents a scheduler fire from later overwriting the host's time.

## 15. Attribute Suggestions via Polls (Feature 4)

**Status: Implemented.** Replaced the old "silence=consent" AttributeProposal system. Attribute suggestions are now regular polls tagged with `attributeType`. Non-creator edits apply directly (no more interception).

### Key Files

| File | Purpose |
|------|---------|
| `model/Poll.java` | Added `attributeType` (nullable: "LOCATION", "DESCRIPTION") and `promotedAt` fields |
| `model/PollOption.java` | Added `createdBy` (userId) and `structuredValue` (JSON for location data) fields |
| `service/AttributeSuggestionService.java` | Interface for suggestion computation and supersession |
| `service/impl/AttributeSuggestionServiceImpl.java` | Pure computation + supersession logic |
| `dto/SuggestedAttributeDTO.java` | Computed suggestion state: attributeType, suggestedValue, status, voteCount |
| `dto/HangoutDetailDTO.java` | `Map<String, SuggestedAttributeDTO> suggestedAttributes` field |
| `dto/HangoutSummaryDTO.java` | Same field for feed responses |

### How It Works

1. Users create a poll with `attributeType: "LOCATION"` or `"DESCRIPTION"` via the existing poll endpoints.
2. `PollServiceImpl.createPoll()` forces `multipleChoice = false` for suggestion polls and supersedes any existing active suggestion poll of the same type.
3. `getHangoutDetail()` and group feed call `computeSuggestedAttributes()` to build the `suggestedAttributes` map from active suggestion polls — pure computation over already-fetched data.
4. When a direct edit sets location or description, `supersedeSuggestionPolls()` deactivates the corresponding suggestion polls.
5. Non-creator edits now apply directly — the old interception block in `updateHangout()` has been removed.

### Suggestion Status (Computed at Runtime)

`computeSuggestedAttributes()` derives status from poll data already loaded — no writes or scheduled tasks needed:

| Status | Condition |
|--------|-----------|
| PENDING | < 24h old, or no votes yet |
| CONTESTED | Multiple options with votes |
| READY_TO_PROMOTE | ≥ 24h old, single option or no opposing votes — client treats as effective value |

When a direct edit sets location or description, `supersedeSuggestionPolls()` deactivates the corresponding suggestion polls immediately.

### API Contract (No New Endpoints)

```
POST /hangouts/{eventId}/polls              — create suggestion poll (attributeType in body)
POST /hangouts/{eventId}/polls/{id}/options  — add counter-suggestion (createdBy auto-set)
POST /hangouts/{eventId}/polls/{id}/vote     — vote for a suggestion
GET  /hangouts/{eventId}                     — detail includes suggestedAttributes map
PATCH /hangouts/{eventId}                    — direct edit supersedes suggestions
```

## 16. Action-Oriented Nudges (Feature 5)

**Status: Implemented.** Nudges are computed fresh on each `getHangoutDetail()` request — never stored.

### Key Files

| File | Purpose |
|------|---------|
| `model/NudgeType.java` | Enum: SUGGEST_TIME, ADD_LOCATION, MAKE_RESERVATION, CONSIDER_TICKETS |
| `dto/NudgeDTO.java` | type, message, actionUrl |
| `service/NudgeService.java` | Interface |
| `service/impl/NudgeServiceImpl.java` | Computes nudges from hangout state + interest levels + suggestion polls |
| `dto/HangoutDetailDTO.java` | `List<NudgeDTO> nudges` field (computed at read time) |
| `service/impl/HangoutServiceImpl.java` | Calls `nudgeService.computeNudges()` in `getHangoutDetail()` |

### Nudge Rules

| Nudge Type | Condition |
|------------|-----------|
| SUGGEST_TIME | `startTimestamp == null` AND (CONFIRMED, or ≥1 non-creator interest signal) |
| ADD_LOCATION | `location == null` AND (CONFIRMED, or ≥1 non-creator interest signal). Message changes to "Vote on location suggestions" when a PENDING/CONTESTED suggestion poll exists. Suppressed entirely when a suggestion poll is READY_TO_PROMOTE (location effectively decided). |
| MAKE_RESERVATION | `placeCategory` is restaurant/bar/food AND momentum is GAINING_MOMENTUM or CONFIRMED |
| CONSIDER_TICKETS | `placeCategory` is event/entertainment/concert/theater/sports AND momentum is GAINING_MOMENTUM or CONFIRMED |

**CONFIRMED always gets completion nudges:** A CONFIRMED hangout always shows SUGGEST_TIME/ADD_LOCATION if the corresponding field is missing, regardless of interest count. The hangout is happening — it needs a time and location.

**`suggestedBy` is always set** to the creator's userId for both "Float it" and "Lock it in" modes. It's used by nudge logic to exclude the creator from non-creator interest checks.

Multiple nudges can be active simultaneously. Nudges are in both `HangoutDetailDTO.nudges` and `HangoutSummaryDTO.nudges` (version-gated to 2.0.0+).

## 17. Adaptive Notifications (Feature 6)

**Status: Implemented.** Per-group weekly notification budget prevents notification fatigue.

### Key Files

| File | Purpose |
|------|---------|
| `model/GroupNotificationTracker.java` | DynamoDB entity tracking weekly counts and rolling average |
| `repository/GroupNotificationTrackerRepository.java` | DynamoDB access |
| `service/AdaptiveNotificationService.java` | Budget computation, rollover, signal recording |

### Decision Rules

| Signal Type | Always Notify? |
|-------------|---------------|
| `CONCRETE_ACTION` (tickets, reservation) | Yes |
| `CONFIRMED` (explicit "It's on!") | Yes |
| `BUILDING_TO_GAINING`, `GAINING_TO_CONFIRMED`, `GENERAL` | Subject to weekly budget |

### Weekly Budget

```
weeklyBudget = max(2, ceil(rollingWeeklyAverage × 1.5))
shouldSend   = notificationsSentThisWeek < weeklyBudget
```

New groups with no history default to budget = 2. Rolling average uses 8-week exponential moving average (α = 1/8).

### DynamoDB Key Pattern

```
PK = GROUP#{groupId}
SK = NOTIFICATION_TRACKER
```

### Notification Message Templates

| Type | Template |
|------|----------|
| Gaining traction | `'[title]' is gaining traction — N people are interested` |
| Ticket purchased | `[name] bought tickets for '[title]'` |
| Action nudge | `'[title]' is [dayLabel] — consider buying tickets` |
| Empty week | `Nothing planned next week — check out your group's ideas` |

## 18. Deferred / Known Gaps

- **Active member tracking** — `engagementMultiplier` currently uses total membership count; should track members with InterestLevel records in last 8 weeks.
- **Group confirmation rate** — currently uses default 0.6 multiplier; should compute from rolling 8-week data.
- **Migrate MomentumServiceImpl scoring weights to `MomentumTuningProperties`** — RSVP weights, multiplier factors, threshold coefficient. Currently hardcoded as `static final` in `MomentumServiceImpl`. Deferred from the forward-fill/fading change to keep blast radius small.
- **Per-week stale-float assignment** — today the forward-fill service dumps all chosen stale floats into `needsDay`. A better UX places each filler into a specific empty week (same treatment as dated hangouts). Requires adding a synthetic week slot concept to the feed response.
- **Name-based de-dupe between ideas and hangouts** — current forward-fill doesn't de-dupe. `Address` DTO lacks `googlePlaceId`, so placeId matching isn't possible; a name-similarity check could be added if users report duplicate noise.
- **Migrate to integrated fresh-float creation logic in `FeedSortingService`** — currently classifies at read time from `HangoutPointer.createdAt`. If we ever want to freeze "fresh" at a point in time (e.g., make creation flag explicit), revisit.
- **ETag time bucket uses `System.currentTimeMillis()` directly** in `GroupController.calculateETag`. Inject a `Clock` bean so the bucket boundary can be unit-tested without system-clock manipulation. Low priority — the logic is trivial and covered by the tunable `momentum.tuning.etag-time-bucket-seconds`.
