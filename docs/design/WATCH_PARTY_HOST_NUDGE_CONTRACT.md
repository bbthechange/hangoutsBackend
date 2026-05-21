# Watch Party Host Nudge — Interface Contract

**Status:** Frozen. This is the source of truth for cross-phase coordination.

**Scope:** Names, signatures, schemas, and constants that downstream phases
depend on. Implementation details (logic, branching, tests) live in the
plan and in each phase's beads issue — not here.

**Change discipline:** Any modification to anything in this file requires
updating downstream beads issues in lockstep. Don't quietly rename.

**Reference docs:**
- Plan: `~/.claude/plans/read-context-ux-watch-party-host-nudge-u-starry-spring.md`
- UX: `context/ux/WATCH_PARTY_HOST_NUDGE_UX.md`
- Related: `context/TV_WATCH_PARTY_CONTEXT.md`, `context/NOTIFICATIONS_CONTEXT.md`

---

## 1. Frozen names & strings

### Model field names

| Class | Field | Type | Notes |
|-------|-------|------|-------|
| `EventSeries` | `watchPartyModel` | `String` | Values: `"IN_PERSON"`, `"VIRTUAL"`. Nullable in DynamoDB for legacy rows; helper treats null as IN_PERSON. |
| `Hangout` | `hostNudgeScheduleName` | `String` | EventBridge schedule name, persisted for idempotent update/delete. |
| `Hangout` | `hostNudgeSentAt` | `Long` | Epoch ms. Idempotency flag for the nudge. Set on successful send OR on host claim (so any in-flight fire becomes a no-op). |
| `Hangout` | `lastHostNotificationAt` | `Long` | Epoch ms. Used by location-change coalesce check. |

### EventSeries helper

```java
public boolean isVirtualWatchParty() {
    return "VIRTUAL".equals(watchPartyModel);
}
```

(Null and `"IN_PERSON"` both return false. Legacy series default to IN_PERSON behavior.)

### DTO field names

| DTO | Field | Type | Required |
|-----|-------|------|----------|
| `CreateWatchPartyRequest` | `watchPartyModel` | `String` | Yes (no default per UX) |
| `UpdateWatchPartyRequest` | `watchPartyModel` | `String` | Optional (omit = leave unchanged) |

### EventBridge schedule name format

```
hostnudge-{hangoutId}
```

### SQS payload schema

```json
{"type":"WATCH_PARTY_HOST_NUDGE","hangoutId":"<hangoutId>"}
```

The `type` field is the dispatch discriminator in `ScheduledEventListener`.

### SQS type constant

```java
private static final String TYPE_WATCH_PARTY_HOST_NUDGE = "WATCH_PARTY_HOST_NUDGE";
```

### Schedule timing

```java
private static final long NUDGE_OFFSET_SECONDS = 48 * 60 * 60; // 48h pre-air
private static final long MIN_SCHEDULE_ADVANCE_SECONDS = 60;
private static final long MIN_MINUTES_BEFORE_AIR = 36 * 60; // handler safety window
private static final long MAX_MINUTES_BEFORE_AIR = 60 * 60;
```

### Coalesce window for host-claim + location-change

```java
private static final long HOST_CLAIM_COALESCE_WINDOW_MS = 600_000L; // 10 minutes
```

### Nudge-type constants

```java
public final class NudgeTypes {
    public static final String HOST_NUDGE = "HOST_NUDGE";
    // Future: LOCATION_NUDGE, TIME_NUDGE, etc.
    private NudgeTypes() {}
}
```

Used as keys in `SeriesNotificationPreference.mutedNudgeTypes` and (eventually) in the future per-hangout preference entity. Mute endpoint request bodies reference these strings.

### Micrometer counter names

| Counter | Tags |
|---------|------|
| `watchparty_host_nudge_schedule_created` | `status` ∈ {`success`, `error`, `skipped_virtual`, `skipped_has_host`, `skipped_past`} |
| `watchparty_host_nudge_schedule_deleted` | `status` ∈ {`success`, `error`} |
| `watchparty_host_nudge_total` | `status` ∈ {`sent`, `already_sent`, `host_claimed`, `not_found`, `not_in_series`, `wrong_series_type`, `outside_window`, `lost_race`, `error`} |
| `watchparty_host_claim_notification` | `status` ∈ {`sent`, `error`} |
| `notification_coalesced` | `reason` ∈ {`host_claim_recent`} |

### Property keys to REMOVE

```
watchparty.host-check.enabled
watchparty.host-check.cron
```

(Both are dead. No replacement properties — the new feature is unconditionally on when `scheduler.enabled=true`.)

---

## 2. New entity — `SeriesNotificationPreference`

### Key pattern

```
PK = USER#{userId}
SK = SERIESPREF#{seriesId}
```

### Fields

| Field | Type | Notes |
|-------|------|-------|
| `userId` | `String` | UUID |
| `seriesId` | `String` | UUID |
| `mutedNudgeTypes` | `Map<String,Boolean>` | Keys are nudge-type constants (see §1 nudge-type constants). Value `true` = muted, absent or `false` = not muted. |

Sparse — record is only created on first mute. Absence of the record OR absence of a key in the map == not muted.

**Why a map instead of per-type booleans:** future nudge types (location-nudge, time-poll-pending, etc.) can be added by registering a new key constant — no schema change, no DTO change, no endpoint change. See §8.

---

## 3. Frozen Java signatures

### `HangoutRepository` — additions

```java
/**
 * Atomically set hostNudgeSentAt iff currently null/absent.
 * Returns true if this call claimed the nudge, false if another caller already did.
 * Mirrors setReminderSentAtIfNull.
 */
boolean setHostNudgeSentAtIfNull(String hangoutId, long timestamp);

/**
 * Persist the EventBridge schedule name on the hangout for later cancel/update.
 * Mirrors updateReminderScheduleName.
 */
void updateHostNudgeScheduleName(String hangoutId, String name);

/**
 * Persist the last host-claim notification timestamp for coalesce checks.
 */
void updateLastHostNotificationAt(String hangoutId, long timestamp);
```

### `SeriesNotificationPreferenceRepository` — new interface

```java
public interface SeriesNotificationPreferenceRepository {
    Optional<SeriesNotificationPreference> find(String userId, String seriesId);

    /**
     * Bulk-lookup for the recipient resolver. Returns the subset of
     * candidateUserIds who have mutedNudgeTypes[nudgeType] == true
     * for this series.
     */
    Set<String> findMutedUsersForSeries(
        String seriesId,
        String nudgeType,
        Collection<String> candidateUserIds);

    /**
     * Set or clear a single nudge-type mute for one user/series.
     * Upserts the row if absent; removes the row when the map becomes empty.
     */
    void setMuted(String userId, String seriesId, String nudgeType, boolean muted);

    void delete(String userId, String seriesId);
}
```

Note: `save(pref)` is replaced by `setMuted(...)` to keep the per-key update atomic and avoid clobbering concurrent writes to other keys in the map.

### `WatchPartyHostNudgeScheduler` — new service

```java
@Service
public class WatchPartyHostNudgeScheduler {
    /**
     * Idempotently create-or-update the EventBridge schedule.
     * No-op (with counter) when: scheduler disabled, series is virtual,
     * hangout already has a host, hangout already nudged, or fire time has passed.
     */
    public void scheduleHostNudge(Hangout hangout, EventSeries series);

    /**
     * Delete the EventBridge schedule. Safe to call when no schedule exists.
     */
    public void cancelHostNudge(Hangout hangout);
}
```

### `WatchPartyHostNudgeService` — new service

```java
@Service
public class WatchPartyHostNudgeService {
    /**
     * Invoked by ScheduledEventListener for WATCH_PARTY_HOST_NUDGE messages.
     * Handles all early-return guards, atomic idempotency claim,
     * recipient assembly, and notification dispatch.
     */
    public void processHostNudge(String hangoutId);
}
```

### `WatchPartyHostNudgeRecipientResolver` — new helper

```java
class WatchPartyHostNudgeRecipientResolver {
    /**
     * Build the recipient set per the UX rules:
     *   include: GOING/INTERESTED on hangout, GOING/INTERESTED on series,
     *            past hosters in this series, series creator
     *   exclude: NOT_GOING on hangout (overrides series-level GOING),
     *            NOT_GOING on series (unless GOING on hangout),
     *            users muted for NudgeTypes.HOST_NUDGE on this series
     */
    Set<String> resolve(EventSeries series, Hangout hangout);
}
```

Internally composes the primitives below — does NOT re-implement the filter logic inline.

### `InterestLevelQueries` — shared util (new)

Extract the GOING/INTERESTED/NOT_GOING filter pattern that currently lives inline at `HangoutServiceImpl.java:670-674` and `WatchPartyHostCheckService.java:151-160`. Phase 2 builds this util and the resolver composes from it; future nudge resolvers (location nudge, etc.) reuse the same primitives.

```java
public final class InterestLevelQueries {
    public static Set<String> goingOrInterestedOnHangout(HangoutDetailData detailData);
    public static Set<String> notGoingOnHangout(HangoutDetailData detailData);
    public static Set<String> goingOnHangout(HangoutDetailData detailData);
    public static Set<String> goingOrInterestedOnSeries(SeriesPointer pointer);
    public static Set<String> notGoingOnSeries(SeriesPointer pointer);
    private InterestLevelQueries() {}
}
```

All return empty (not null) when input is null or has no interest levels.

**Refactor scope in phase 2:** replace the inline filter at `HangoutServiceImpl.java:670-674` with `InterestLevelQueries.goingOrInterestedOnHangout(detailData)`. The dead `WatchPartyHostCheckService` site at 151-160 is deleted in phase 4, so no migration there.

### `NotificationService` — additions

```java
/**
 * "{Show} — {Episode} airs {Day} and still needs a host!"
 * Caller supplies the fully-built body to keep message-formatting logic
 * out of NotificationService.
 */
void notifyWatchPartyHostNeeded(
    Set<String> userIds,
    EventSeries series,
    Hangout hangout,
    String body);

/**
 * "{ClaimerName} is hosting {Day}'s {Show} episode."
 * Excludes claimerUserId from recipients. After sending,
 * persists lastHostNotificationAt on the hangout for coalesce.
 */
void notifyWatchPartyHostClaimed(
    EventSeries series,
    Hangout hangout,
    String claimerUserId,
    Set<String> recipients);
```

### `ScheduledEventListener` — dispatch branch

```java
// Added at line 37 area:
private static final String TYPE_WATCH_PARTY_HOST_NUDGE = "WATCH_PARTY_HOST_NUDGE";

// Added inside handleMessage() before the default branch:
} else if (TYPE_WATCH_PARTY_HOST_NUDGE.equals(type)) {
    handleWatchPartyHostNudge(node, messageBody);
}

// New private method:
private void handleWatchPartyHostNudge(JsonNode node, String messageBody) {
    // Extract hangoutId, validate, delegate to watchPartyHostNudgeService.processHostNudge(hangoutId).
    // Emit watchparty_host_nudge_total{status=...} counters.
}
```

Inject `WatchPartyHostNudgeService` via constructor. If cyclic, use the
`IdeaAddBatchHandler`-style setter-injected interface (see
`ScheduledEventListener:51` for the existing pattern).

---

## 4. New REST endpoint

### `PUT /watch-parties/{seriesId}/notification-preferences`

**Auth:** JWT required. User must be member of at least one group that
owns the series (reuse the auth check from
`WatchPartyServiceImpl.setUserInterest`).

**Request body:**
```json
{ "nudgeType": "HOST_NUDGE", "muted": true }
```

- `nudgeType`: one of the constants in `NudgeTypes` (§1). Unknown values → `400`.
- `muted`: `true` to mute, `false` to unmute.

Endpoint sets a single key in `mutedNudgeTypes` via
`SeriesNotificationPreferenceRepository.setMuted(...)`. Single-key writes
keep concurrent updates to other nudge types safe.

**Response:** `204 No Content`

**Errors:**
- `404` if series doesn't exist or user has no access.
- `400` if `nudgeType` is not a known constant.

---

## 5. Phase ownership

| Owner | What it adds/changes | What it must NOT touch |
|-------|----------------------|------------------------|
| Phase 1: Foundation | All §1, §2, §3 (HangoutRepository methods, SeriesNotificationPreferenceRepository, model/DTO fields), stub classes for §3 services with `throw new UnsupportedOperationException()` bodies, stub `ScheduledEventListener` branch routing to the stub service. | Existing lifecycle code paths. No behavior changes. |
| Phase 2: Scheduler + handler | Implements WatchPartyHostNudgeScheduler, WatchPartyHostNudgeService, WatchPartyHostNudgeRecipientResolver, NotificationService.notifyWatchPartyHostNeeded, ScheduledEventListener handler body. | WatchPartyServiceImpl, HangoutServiceImpl, WatchPartyBackgroundServiceImpl. |
| Phase 3: Lifecycle + claim | Wires scheduleHostNudge/cancelHostNudge into WatchPartyServiceImpl (create/update/delete), WatchPartyBackgroundServiceImpl.processNewEpisode, HangoutServiceImpl (delete + host-claim cancel + notifyWatchPartyHostClaimed + auto-RSVP + coalesce). | Recipient resolver internals, scheduler internals, mute toggle. |
| Phase 4: Mute + cleanup | New PUT endpoint, recipient resolver mute filter call, delete WatchPartyHostCheckService + tests, remove dead properties, update context docs. | None of the host-needed / host-claim wiring. |

---

## 6. Stub commits Phase 1 produces

Phase 1 lands these stubs so phases 2-4 fill in bodies without renaming
anything:

```java
// service/WatchPartyHostNudgeScheduler.java
@Service
public class WatchPartyHostNudgeScheduler {
    public void scheduleHostNudge(Hangout hangout, EventSeries series) {
        throw new UnsupportedOperationException("Phase 2");
    }
    public void cancelHostNudge(Hangout hangout) {
        throw new UnsupportedOperationException("Phase 2");
    }
}

// service/impl/WatchPartyHostNudgeService.java
@Service
public class WatchPartyHostNudgeService {
    public void processHostNudge(String hangoutId) {
        throw new UnsupportedOperationException("Phase 2");
    }
}
```

Plus the `ScheduledEventListener` dispatch branch (routes to the stub —
phase 2 fills the body of `handleWatchPartyHostNudge`).

The recipient resolver and mute repository are NOT stubbed in phase 1
beyond the interface — phase 2 (resolver) and phase 1 (mute repo
implementation) own those bodies respectively, since mute repo is part
of the foundation.

---

## 7. Test fixtures phase 1 publishes

A small `WatchPartyTestFixtures` helper with builders that produce valid
`EventSeries` (in-person and virtual variants) and `Hangout` instances
with the new fields populated. Downstream phases use these to avoid
divergent test-data construction.

---

## 8. Future nudge type extension recipe

This design covers exactly one nudge type — the watch-party host nudge.
The shape is deliberately set up so additional nudge types (e.g.
"hangout has no location and air time is soon", "time poll still open
24h before start") can be added by following the same recipe, without
restructuring this code.

### Recipe for a new nudge type

1. **Add a constant** to `NudgeTypes` (e.g. `LOCATION_NUDGE = "LOCATION_NUDGE"`).
2. **Add an idempotency field on the relevant entity.** For
   hangout-attached nudges: a new `Long {nudgeName}NudgeSentAt` and
   `String {nudgeName}NudgeScheduleName` pair on `Hangout`, plus
   atomic `set{Field}IfNull` and `update{Field}ScheduleName` methods on
   `HangoutRepository`. Do not generalize via map — atomic conditional
   updates are much cleaner against named scalar fields.
3. **Add a new SQS type discriminator** (e.g. `TYPE_HANGOUT_LOCATION_NUDGE`)
   and a dispatch branch in `ScheduledEventListener.handleMessage`.
4. **Create a new scheduler service** with its own gate logic
   (`scheduleLocationNudge` / `cancelLocationNudge`). Mirror
   `WatchPartyHostNudgeScheduler` but with the gates that matter to the
   new nudge type (e.g. "hangout has no location and isn't a virtual
   event"). The shared `EventBridgeSchedulerClient` is already generic.
5. **Create a new handler service** (e.g. `HangoutLocationNudgeService`)
   with its own `processNudge` that does the gate-check / idempotency
   claim / recipient resolution / dispatch flow.
6. **Create a new recipient resolver** that composes from
   `InterestLevelQueries` (§3). Different nudge types have different
   recipient rules — do NOT try to share resolvers across types.
7. **Add a notification method** to `NotificationService`
   (e.g. `notifyHangoutLocationNeeded`) that delegates to the existing
   platform-specific dispatch.
8. **Wire lifecycle** in the appropriate service: schedule the nudge on
   hangout creation, cancel on the resolving mutation (e.g. location
   added), cancel on delete.
9. **Pick a schedule-name prefix** that doesn't collide
   (`locationnudge-{hangoutId}`, etc.). Each nudge type owns its own
   EventBridge schedule per hangout — they coexist on the same hangout.

### Per-hangout (non-series) preferences — explicitly future work

`SeriesNotificationPreference` is series-scoped. Future nudges on
one-off hangouts (no series) cannot use it. When that need arises,
add a parallel entity `HangoutNotificationPreference` with
PK=`USER#{userId}`, SK=`HANGOUTPREF#{hangoutId}`, mirroring the same
`mutedNudgeTypes` map shape. Recipient resolvers consult whichever
preference store is relevant for their nudge.

Do NOT stuff hangout-scoped preferences into
`SeriesNotificationPreference` later — keep them in their own entity.

### What stays generic (don't re-invent)

- `EventBridgeSchedulerClient` — handles all AWS SDK interaction,
  expected-to-exist races, DLQ wiring, retry policy.
- `ScheduledEventListener` — already infinitely extensible via the
  `type` discriminator dispatch.
- `InterestLevelQueries` — recipient primitives, reusable across all
  hangout-based nudges.
- `Hangout.lastHostNotificationAt`-style coalesce fields — add new
  per-nudge-type fields when coalesce is needed; don't rename existing
  ones to be "generic" (loses semantic precision).

### What stays per-nudge-type (don't try to share)

- The scheduler service, handler service, recipient resolver,
  notification method, and SQS type constant. Each nudge has its own
  gates, recipient logic, and message — sharing creates leaky
  abstractions.
