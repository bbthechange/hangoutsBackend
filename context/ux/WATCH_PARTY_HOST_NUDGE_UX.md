# UX Design: Watch Party Host Nudge

**Status:** Design — greenfield build. `WatchPartyHostCheckService.java` exists but is non-functional dead code (see "Existing code status" below).
**Related context:** `context/TV_WATCH_PARTY_CONTEXT.md`, `context/NOTIFICATIONS_CONTEXT.md`

## Problem

Watch party episodes are scheduled automatically from TVMaze, but in the in-person model a host (whose house everyone goes to) needs to be claimed each week. Today nothing prompts the group when an episode is approaching without a host, so episodes silently drift toward air time with no plan.

## Product framing

For a watch party series, the **host functions as the location** — claiming host means "we're watching at my place." Virtual series have no host concept; everyone watches from home and chats over Discord/stream link. The nudge feature applies only to the in-person model.

## User flows

### Flow 1 — Series creation: in-person vs. virtual

Series creation must capture which model the group is using. This is the field that decides whether the host-nudge system applies at all.

**Affordance:** Segmented control near the existing "Default host" field, two options:

- **In Person** — someone hosts each week. Reveals the existing "Default host (optional)" picker below. Enables host nudges for every episode that ends up without a host.
- **Virtual** — no host needed. Hides the host picker. Repurposes the "location" field on each episode into a "Streaming link / chat" field (Discord invite, Plex link, Zoom). Disables host nudges for the entire series.

**No default selection.** Force a deliberate tap. A sticky default ("In Person") will silently mis-classify always-virtual groups, and they'll wonder why they're being pinged about hosting; the cost of one extra tap at create time is cheap compared to fixing that mismatch later.

**Editability:** the existing PUT series endpoint with `changeExistingUpcomingHangouts: true` should accept the in-person/virtual flag so a user can flip the model later (e.g., received their first nudge and realized the series is actually virtual). One-tap fix from series settings — not from the notification itself; the mixing of concerns isn't worth the complexity.

### Flow 2 — The host-needed notification

**Trigger condition (per hangout):**

- Series is **In Person** (Virtual series are skipped entirely).
- Hangout has no host (`hostAtPlaceUserId` is empty).
- Hangout has no `defaultHostId` inherited from the series.
- Hangout is between ~36 and ~60 hours from air time (target ≈48h, with EventBridge providing precision; window only matters as a safety net).
- Hangout has not previously been nudged (idempotency flag — see "Mechanical concerns" in `TV_WATCH_PARTY_CONTEXT.md` notes).

**Note on `location` field:** explicitly NOT part of the trigger. An episode with a host but no street address is fine — the host's address is implicit and often added later. Coupling these would suppress the nudge in the exact case it's needed (host claimed but address not yet entered) and fire incorrectly when the situation doesn't need it.

**Scheduling mechanism:** EventBridge per-hangout, matching how `HangoutSchedulerService` schedules per-hangout reminders. When a watch-party hangout is created (and the series is In Person), schedule a one-shot EventBridge rule for ~48h before air time that invokes the host-check for that specific hangout. When a host is claimed, cancel the scheduled rule. When a hangout is deleted, cancel the rule. Do NOT use Spring `@Scheduled` — this project doesn't have `@EnableScheduling` configured and the pattern is incompatible with the rest of the scheduling code.

**Timing within the day:** clamp to a reasonable local-time window in each recipient's timezone — suggest **9am–8pm**. A literal 48-hour rule will sometimes fire at 6am local, which is bad. EventBridge fires the host-check; the check itself decides per recipient whether to send now or defer to the next in-window slot. Acceptable to slide the actual send between 36–60h to hit the polite window.

**Recipient set:**

- Users marked **GOING on the episode** OR **GOING on the series**.
- Users marked **INTERESTED** on either.
- Users who have **hosted at least one prior episode of this same series** (a series is a single season — naturally bounded, no extra lookback rule needed).
- The **series creator**, if they aren't already captured by the above.
- **Exclusions** (applied last):
  - NOT_GOING on this specific episode → exclude regardless of series-level status.
  - NOT_GOING on the series, unless explicitly GOING on this episode (episode-level wins).

No explicit inactive-user filter is needed. A series covers a single season; users who've churned won't have RSVP'd on the series, won't have hosted any episode in this series, and aren't the series creator — so they fall out of the recipient set naturally.

**Notification copy:**

- Format: `"{Show name} — {Episode title} airs Friday and still needs a host!"`
- Long-name handling falls back to the abbreviation lookup table being built in the parallel naming workstream.
- TBA titles: `"Friday's {Show name} episode still needs a host!"`
- Combined episodes (Double/Triple): `"Friday's double {Show name} episode still needs a host!"` — collapses to show name; don't try to list both episode titles in a push.

**Tap target:** deep-link directly to the hangout detail with the "I'll host" affordance visible without scrolling.

### Flow 3 — Claiming the host

The episode detail already has an "I'll host" affordance. The nudge funnels users to it; no new claim UI is needed. Behaviors that must be true at claim time (some already in place):

- **One tap to claim.** Do not gate the claim behind a sheet asking for address, notes, or anything else. Capture intent immediately.
- **Auto-set the claimer's RSVP to GOING** as a side effect. Hosting implies attending; don't make the user do both.
- **Non-blocking follow-on prompt: "Add your address?"** Inline on the hangout after the claim succeeds. Optional — don't force it. Many claimers will do this at their desk later; some won't bother and that's fine. The prompt should not be a modal sheet.
- **Race condition copy.** Two users tap "I'll host" within seconds. The second tapper sees: *"Sarah just claimed this one — you're all set as going."* Their RSVP still flips to GOING (they tried to commit). Never show a raw error.

### Flow 4 — Cascading notification after a successful claim

This is where the **host-claim flow intersects the existing location-change notification path**, and where today's code has a gap.

**Existing behavior** (`HangoutServiceImpl.updateHangout`, lines 666–692): when `location` changes on a hangout, `notifyHangoutUpdated` fires to GOING/INTERESTED users with the new location name in the message body.

**Gap:** when `hostAtPlaceUserId` changes, no such notification fires today. For a watch party, the host *is* the location, so a host claim should fire the equivalent of the location-change notification.

**Required UX behavior:**

- When `hostAtPlaceUserId` transitions from empty to set on a watch-party hangout (regardless of whether `location` is also set), send a notification to the GOING/INTERESTED set on the hangout: *"{Claimer name} is hosting Friday's {show name} episode."*
- This is a **separate code path from the generic location-change notification** — that flow keys off `location` mutation, not host mutation. The watch-party host-claim flow needs its own trigger that mirrors the same recipient logic and message shape, but is fired by the host-change path.
- **Suppress the pending host-needed nudge** for anyone who hasn't received it yet. The hangout's idempotency flag should also be marked sent so the cron doesn't re-fire.
- **Don't double-notify.** Anyone who already received the "needs a host" push should still get the "hosting" follow-up — they're meaningfully different ("there's a problem" → "the problem is solved"). But a user shouldn't get the host-needed push AND a location-change push if the claimer also fills in an address shortly after. Coalesce: if a location-change notification would fire within a short window after the host-claim notification for the same hangout, skip the second one.

This also produces a small social reward for the claimer (the group sees their name), which is worth preserving as a behavior.

### Flow 5 — Per-series mute

Recipients who are GOING on every episode shouldn't have to NOT_GOING the series just to stop host-nudge pushes. Add a **per-series notification submenu** in series detail with a single toggle: "Notify me when an episode needs a host." Default on. This is the one settings affordance worth building with v1; the rest of notification preferences can stay as they are.

## Deferred to later versions

- **Per-episode virtual override.** The 95% case is consistent across a series. If one specific week is virtual when the rest are in-person, the user can manually claim host and put the link in the description. Add an explicit per-hangout override only if usage data shows it's needed.
- **"Add address" reminder for hosts who never followed through.** Possible v2: gentle nudge to the host (only the host, no one else) 24h before air if their address still isn't set. Defer until we see whether this is actually a problem.
- **Notification action buttons** ("I'll host" / "Not me" inline on the push). Nice but not load-bearing — the deep-link to a one-tap claim screen is sufficient.

## Existing code status

**`WatchPartyHostCheckService.java` exists but is dead code.** It cannot fire in production. Three independent reasons:

1. **Feature flag defaults to `false`.** `application.properties:107` sets `watchparty.host-check.enabled=${WATCHPARTY_HOST_CHECK_ENABLED:false}` and the class is gated by `@ConditionalOnProperty(... havingValue="true")` — the bean isn't instantiated at all. No env var override exists anywhere in the project.
2. **No `@EnableScheduling` in the codebase.** Spring silently ignores `@Scheduled` without that annotation on a `@Configuration` class. Even if the flag were flipped, the cron would never tick.
3. **Nothing else calls it.** No controller endpoint to manually trigger, no listener, no EventBridge wiring. The class is unreachable.

The original design (`docs/design/TV_WATCH_PARTY_DESIGN.md:733`) actually specified EventBridge (`watch-party-host-check-rule | rate(1 day) | Host Check Lambda`). The implementation diverged and reached for Spring `@Scheduled`, which is incompatible with how the rest of this project schedules things (`HangoutSchedulerService`, `TimePollScheduler`, `IdeaNotificationBatchService`, `ScheduledEventListener` all use EventBridge).

**Treat this feature as greenfield.** A handful of pieces of the existing class are still useful as reference — the `isHostless` check (`hostAtPlaceUserId` null/empty), the GOING/INTERESTED filter shape on `SeriesPointer.interestLevels`, the Micrometer counter names — but the scheduling mechanism, the daily-batch model, the recipient logic, and the assumption of a fixed UTC window all need to be replaced rather than evolved.

### Build checklist (in rough order)

- ⏭ Replace daily Spring cron with per-hangout EventBridge scheduling, following the `HangoutSchedulerService` pattern (schedule on hangout creation, cancel on host-claim, cancel on hangout deletion).
- ⏭ Add the In Person / Virtual flag on `EventSeries`. Skip scheduling entirely for Virtual series.
- ⏭ Implement the expanded recipient set (episode-level RSVPs, past hosters in this series, series creator, NOT_GOING exclusions). No inactive-user filter needed — the single-season scope of a series naturally excludes churned users.
- ⏭ Add per-hangout idempotency flag (analogous to `titleNotificationSent`) so the EventBridge fire is exactly-once even if delivery retries.
- ⏭ Improve copy with show-name prefix and TBA / combined-episode fallbacks (coordinate with the parallel naming workstream).
- ⏭ Add the host-claim → notification path (Flow 4 — new code, parallels but doesn't share the existing `location`-change trigger in `HangoutServiceImpl.updateHangout`).
- ⏭ Add per-series "Notify me when an episode needs a host" mute toggle.
- ⏭ Decide whether to delete the dead `WatchPartyHostCheckService` and its tests, or repurpose the class as the per-hangout handler invoked by EventBridge. Probably the latter, since the test scaffolding around hostless detection is reusable.

## Open questions

- **"Hosted past episodes" lookup** — does `Hangout.hostAtPlaceUserId` history need to be queried, or can we rely on the current value across all hangouts in the series? Single-season scope makes this cheap; confirm during implementation.
- **Coalesce window for host-claim + location-change** (Flow 4) — suggest ~10 minutes; product to confirm.
