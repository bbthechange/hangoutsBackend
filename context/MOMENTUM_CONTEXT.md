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

**No FADING state stored** — fading is client-side presentation based on recency/engagement.

**Never demote from CONFIRMED** — once confirmed, always confirmed.

## 4. Scoring Algorithm

### Signal Weights

| Signal | Weight |
|--------|--------|
| Going RSVP | +3 each |
| Interested RSVP | +1 each |
| Time added (startTimestamp != null) | +1 |
| Location added (location != null) | +1 |
| Concrete action (tickets + link, or carpool riders) | Instant CONFIRMED |

### Multipliers (compound — both can apply)

| Multiplier | Condition | Factor |
|------------|-----------|--------|
| Recency | Any InterestLevel updated in last 48h | ×1.5 |
| Time proximity (within 48h) | startTimestamp within 48 hours | ×1.5 |
| Time proximity (within 7d) | startTimestamp within 7 days | ×1.2 |

Multipliers stack: a hangout 2 days away with recent engagement gets ×1.5 × ×1.2 = ×1.8.

### Dynamic Threshold

```
threshold = ceil(activeMembers × engagementMultiplier × 0.4)
```

- **activeMembers** = total group members (TODO: refine to members active in last 8 weeks)
- **engagementMultiplier** = default 0.6, clamped [0.3, 1.0] (TODO: compute from rolling 8-week confirmation rate)
- Minimum threshold = 1

Example: 8 members × 0.6 × 0.4 = threshold of 2.

### Promotion Rules

- **BUILDING → GAINING_MOMENTUM**: score ≥ threshold
- **GAINING_MOMENTUM → CONFIRMED**: score ≥ threshold × 2 AND hangout has a date
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
3. **Concrete action** — tickets purchased (ticketsRequired + ticketLink) or carpool rider added → auto-confirms via `recomputeMomentum`
4. **Score-based** — crossing upper threshold (score ≥ threshold × 2) auto-confirms (requires date)

## 10. Feed Filter

The feed endpoint (`GET /groups/{groupId}/feed`) accepts a `filter` query parameter:

| Filter | Behavior |
|--------|----------|
| `ALL` (default) | Returns all hangouts |
| `CONFIRMED` | Returns only hangouts where `momentumCategory == CONFIRMED` |
| `EVERYTHING` | Same as ALL for now (fading is client-side) |

Filtering is post-query on both `withDay` and `needsDay` lists.

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
| DISTANT | > 21 days | No suppression |
| needsDay | No timestamp | Sorted by momentum category only |

### Sort Order Within Each Horizon

`CONFIRMED → GAINING_MOMENTUM → BUILDING`, then chronological within each group. Series items are treated as CONFIRMED.

### Smart Surfacing Rules for BUILDING Items

- **Busy week** (confirmed item in same horizon): BUILDING items are suppressed unless they have a recent support surge (2+ interest signals in last 24h). Imminent horizon is always exempt.
- **Empty week** (no confirmed items): Best BUILDING candidate is auto-surfaced (most recent support signal).
- **Zero-support cap**: Max 2 zero-support BUILDING items across the entire feed (shared between `withDay` and `needsDay`). A zero-support item has 0 or 1 interest levels (creator only = zero support).

### Constants

```java
IMMINENT_SECONDS  = 48 * 3600   // 48h
NEAR_TERM_SECONDS = 7 * 86400   // 7 days
MID_TERM_SECONDS  = 21 * 86400  // 21 days
MAX_ZERO_SUPPORT_BUILDING = 2
RECENT_SUPPORT_WINDOW_SECONDS = 24 * 3600
```

## 13. Idea List Feed Surfacing (Feature 2)

**Status: Implemented.** When the group's feed is requested, `IdeaFeedSurfacingServiceImpl` injects idea suggestions into the feed.

### Key Files

| File | Purpose |
|------|---------|
| `service/IdeaFeedSurfacingService.java` | Interface |
| `service/impl/IdeaFeedSurfacingServiceImpl.java` | Suppression check + idea filtering |
| `dto/IdeaFeedItemDTO.java` | Feed item DTO for an idea suggestion |
| `service/impl/GroupServiceImpl.java` | Calls `getSurfacedIdeas()` and appends to feed |

### Logic

1. **Suppression check** (`allUpcomingWeeksCovered`): Queries `getFutureEventsPage` for the next 3 ISO weeks. If every week has ≥1 CONFIRMED hangout, no ideas are surfaced.
2. **Idea filtering**: Fetches all idea lists via `IdeaListService.getIdeaListsForGroup()`. Returns ideas with `interestCount >= 3`, sorted by `interestCount` descending.
3. **Graceful degradation**: Repository or ideaListService failures return an empty list — feed still works.

### Constants

```java
MIN_INTEREST_COUNT = 3
WEEKS_TO_CHECK = 3
```

### IdeaFeedItemDTO fields

`type="idea_suggestion"`, `ideaId`, `listId`, `groupId`, `ideaName`, `listName`, `imageUrl`, `note`, `interestCount`, `googlePlaceId`, `address`, `latitude`, `longitude`, `placeCategory`

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
| `controller/TimeSuggestionController.java` | REST endpoints |
| `task/TimeSuggestionAutoAdoptionTask.java` | Hourly scheduled task (disabled by default) |

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

**Config** (disabled by default):
```properties
time-suggestion.auto-adoption.enabled=true          # enable task
time-suggestion.auto-adoption.short-window-hours=24
time-suggestion.auto-adoption.long-window-hours=48
time-suggestion.auto-adoption.interval-ms=3600000   # hourly
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

## 15. Attribute Promotion / Silence=Consent (Feature 4)

**Status: Implemented.** When a non-creator edits a hangout's location or description, a proposal is created instead of applying the change immediately.

### Key Files

| File | Purpose |
|------|---------|
| `model/AttributeProposal.java` | DynamoDB entity. PK=EVENT#{hangoutId}, SK=PROPOSAL#{proposalId} |
| `model/AttributeProposalType.java` | Enum: LOCATION, DESCRIPTION |
| `model/AttributeProposalStatus.java` | Enum: PENDING, ADOPTED, REJECTED, SUPERSEDED |
| `service/AttributeProposalService.java` | Interface |
| `service/impl/AttributeProposalServiceImpl.java` | Create, vote, auto-adopt logic |
| `repository/AttributeProposalRepository.java` | DynamoDB access |
| `service/impl/HangoutServiceImpl.java` | Intercepts non-creator edits in `updateHangout()` |

### Update Hangout Interception

In `HangoutServiceImpl.updateHangout()`, when a non-creator submits a location or description change:
1. The change is **not** applied directly.
2. `AttributeProposalServiceImpl.createProposal()` is called instead.
3. Any existing PENDING proposal for the same attribute type is SUPERSEDED first.
4. Group members are notified of the proposal (fire-and-forget).

Creator's own edits always apply directly — this flow is bypassed.

### Auto-Adoption Rules

After 24 hours:
- **No alternatives submitted** → proposal is ADOPTED (silence = consent); hangout field is updated and pointers are synced
- **Alternatives submitted** → lightweight vote determines outcome; proposal with most votes is adopted

### DynamoDB Key Pattern

```
PK = EVENT#{hangoutId}
SK = PROPOSAL#{proposalId}
itemType = PROPOSAL
```

## 16. Action-Oriented Nudges (Feature 5)

**Status: Implemented.** Nudges are computed fresh on each `getHangoutDetail()` request — never stored.

### Key Files

| File | Purpose |
|------|---------|
| `model/NudgeType.java` | Enum: SUGGEST_TIME, ADD_LOCATION, MAKE_RESERVATION, CONSIDER_TICKETS |
| `dto/NudgeDTO.java` | type, message, actionUrl |
| `service/NudgeService.java` | Interface |
| `service/impl/NudgeServiceImpl.java` | Computes nudges from hangout state + interest levels |
| `dto/HangoutDetailDTO.java` | `List<NudgeDTO> nudges` field (computed at read time) |
| `service/impl/HangoutServiceImpl.java` | Calls `nudgeService.computeNudges()` in `getHangoutDetail()` |

### Nudge Rules

| Nudge Type | Condition |
|------------|-----------|
| SUGGEST_TIME | `startTimestamp == null` AND ≥1 non-creator interest signal |
| ADD_LOCATION | `location == null` AND ≥1 non-creator interest signal |
| MAKE_RESERVATION | `placeCategory` is restaurant/bar/food AND momentum is GAINING_MOMENTUM or CONFIRMED |
| CONSIDER_TICKETS | `placeCategory` is event/entertainment/concert/theater/sports AND momentum is GAINING_MOMENTUM or CONFIRMED |

Multiple nudges can be active simultaneously. Nudges are in `HangoutDetailDTO.nudges` — not in the feed summary.

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
- **TimeSuggestion GSI** — auto-adoption task uses DynamoDB scan (acceptable at small scale). A GSI on (itemType, status) would enable efficient querying without full-table scan.
- **AttributeProposal vote tie-breaking** — not yet defined; current implementation adopts first proposal by creation time on tie.
