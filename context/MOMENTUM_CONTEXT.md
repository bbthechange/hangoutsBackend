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
- Feed sorting unchanged (chronological) — slot-based interleaving deferred

## 12. Deferred Features

- **Slot-based feed interleaving** — complex sort by momentum category within time horizons. Client can sort by category for v1.
- **Attribute promotion (silence=consent)** — 24h window for alternatives when someone adds location/description. Distinct feature, no dependency on MomentumService.
- **Active member tracking** — currently uses total membership count; should track members with InterestLevel records in last 8 weeks.
- **Group confirmation rate** — currently uses default 0.6 multiplier; should compute from rolling 8-week data.
