# Context: TV Watch Party

**AUDIENCE:** This document is for developers and AI agents working on the TV watch party feature. It assumes familiarity with `EVENT_SERIES_CONTEXT.md`, `HANGOUT_CRUD_CONTEXT.md`, and `NOTIFICATIONS_CONTEXT.md`.

## 1. Overview

TV Watch Party allows users to schedule a series of hangouts for a TV season. The system automatically creates hangouts for announced episodes and adds new episodes as TVMaze announces them. Background polling detects TVMaze changes and updates hangouts accordingly.

**Key Behaviors:**
- Episodes <20 hours apart are combined into "Double Episode" / "Triple Episode" hangouts
- Episode titles auto-update when TVMaze updates them (unless user manually edited)
- Only `regular` and `significant_special` episode types are tracked
- User's IANA timezone is stored for DST-aware scheduling
- Old app versions (< 2.0.0) don't see watch parties in feed

## 2. Key Files & Classes

### Controllers

| File | Purpose |
|------|---------|
| `WatchPartyController.java` | CRUD endpoints for `/groups/{groupId}/watch-parties` |
| `WatchPartyInterestController.java` | Interest endpoint `/watch-parties/{seriesId}/interest` |
| `InternalWatchPartyController.java` | Internal endpoints for SQS testing and poll triggers |

### Services

| File | Purpose |
|------|---------|
| `WatchPartyServiceImpl.java` | Core CRUD operations, episode combination, time calculation |
| `WatchPartyBackgroundServiceImpl.java` | Processes SQS messages (NEW_EPISODE, UPDATE_TITLE, REMOVE_EPISODE) |
| `WatchPartySqsServiceImpl.java` | Sends messages to SQS queues |
| `TvMazePollingServiceImpl.java` | Polls TVMaze API for show updates |
| `WatchPartyHostCheckService.java` | Validates host user IDs |
| `TvMazeClient.java` | HTTP client for TVMaze API with retry logic |

### Models

| File | Purpose |
|------|---------|
| `Season.java` | TVMaze season cache (shared across groups) |
| `Episode.java` | Embedded episode data in Season |
| `EventSeries.java` | Extended with watch party fields |
| `SeriesPointer.java` | Extended with interest levels |
| `Hangout.java` | Extended with titleNotificationSent, combinedExternalIds |

### SQS Listeners

| File | Purpose |
|------|---------|
| `TvMazeUpdateListener.java` | Processes SHOW_UPDATED messages (1 concurrent) |
| `EpisodeActionListener.java` | Processes NEW_EPISODE, UPDATE_TITLE, REMOVE_EPISODE (3 concurrent) |

### DTOs

| File | Purpose |
|------|---------|
| `CreateWatchPartyRequest.java` | Request for creating watch party |
| `UpdateWatchPartyRequest.java` | Request for updating settings |
| `WatchPartyResponse.java` | Response with series and hangout list |
| `WatchPartyDetailResponse.java` | Full detail with interest levels |
| `NewEpisodeMessage.java`, `UpdateTitleMessage.java`, `RemoveEpisodeMessage.java` | SQS messages |

## 3. Data Model

### Season (Shared TVMaze Cache)

One record per season, shared across all groups watching it.

**Key Pattern:**
```
PK: TVMAZE#SHOW#{showId}
SK: SEASON#{seasonNumber}
GSI: ExternalIdIndex (externalId=showId, externalSource="TVMAZE")
```

**Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `showId` | Integer | TVMaze show ID |
| `seasonNumber` | Integer | Season number |
| `showName` | String | Denormalized show name |
| `tvmazeSeasonId` | Integer | TVMaze's internal season ID (for API calls) |
| `episodes` | List<Episode> | Embedded episode list |
| `lastCheckedTimestamp` | Long | When episodes were last fetched (epoch millis) |

**Episode Fields (embedded):**
| Field | Type | Description |
|-------|------|-------------|
| `episodeId` | Integer | TVMaze episode ID |
| `episodeNumber` | Integer | Episode number in season |
| `title` | String | Episode title (may be "TBA") |
| `airTimestamp` | Long | Air time (epoch seconds) |
| `runtime` | Integer | Minutes |
| `type` | String | "regular" or "significant_special" |

### EventSeries (Watch Party Fields)

Extends EventSeries with watch party-specific fields:

| Field | Type | Description |
|-------|------|-------------|
| `eventSeriesType` | String | "WATCH_PARTY" for version filtering |
| `seasonId` | String | "TVMAZE#SHOW#{showId}\|SEASON#{seasonNumber}" |
| `defaultHostId` | String | Optional default host for all episodes |
| `defaultTime` | String | **Required.** "HH:mm" format |
| `dayOverride` | Integer | Optional. 0=Sun, 1=Mon, ... 6=Sat |
| `timezone` | String | **Required.** IANA timezone (e.g., "America/New_York") |
| `deletedEpisodeIds` | Set<String> | User-deleted episodes (prevents re-creation) |

**isWatchParty() method:** Returns `true` if `eventSeriesType == "WATCH_PARTY"`

### Hangout (Watch Party Fields)

| Field | Type | Description |
|-------|------|-------------|
| `titleNotificationSent` | Boolean | True after first title update notification |
| `combinedExternalIds` | List<String> | All TVMaze episode IDs if combined hangout |

### SeriesPointer (Watch Party Fields)

| Field | Type | Description |
|-------|------|-------------|
| `interestLevels` | List<InterestLevel> | Series-level user interest (GOING/INTERESTED) |

## 4. API Endpoints

### POST /groups/{groupId}/watch-parties

Creates a watch party series.

**Request:**
```json
{
  "showId": 1526,
  "seasonNumber": 18,
  "tvmazeSeasonId": 176432,
  "showName": "RuPaul's Drag Race",
  "defaultTime": "20:00",
  "timezone": "America/Los_Angeles",
  "dayOverride": 4,
  "defaultHostId": "user-uuid"
}
```

**Required:** `showId`, `seasonNumber`, `tvmazeSeasonId`, `showName`, `defaultTime`, `timezone`
**Optional:** `dayOverride`, `defaultHostId`

**Response:** `WatchPartyResponse` with `seriesId` and list of created hangouts

### PUT /groups/{groupId}/watch-parties/{seriesId}

Updates watch party settings.

**Request:**
```json
{
  "defaultTime": "19:30",
  "dayOverride": 5,
  "defaultHostId": "new-user-uuid",
  "changeExistingUpcomingHangouts": true
}
```

**`changeExistingUpcomingHangouts`** (default: true): If true, cascades changes to all future hangouts.

### DELETE /groups/{groupId}/watch-parties/{seriesId}

Deletes watch party and all hangouts. **Does NOT delete Season record** (other groups may use it).

### POST /watch-parties/{seriesId}/interest

Sets user's interest level on the series.

**Request:**
```json
{ "level": "GOING" }
```

**Levels:** `GOING`, `INTERESTED`, `NOT_GOING`

**Note:** Series interest is for notification targeting only. No inheritance to individual hangouts.

## 5. Episode Combination Logic

Episodes airing <20 hours apart are combined into a single hangout.

**Algorithm:**
1. Sort episodes by airTimestamp
2. Group consecutive episodes with <20 hours between them
3. Create single hangout per group

**Naming Rules:**
| Count | Title Format |
|-------|--------------|
| 1 | Episode title |
| 2 | "Double Episode: {title1}, {title2}" |
| 3 | "Triple Episode" |
| 4 | "Quadruple Episode" |
| 5+ | "Multi-Episode ({count} episodes)" |

**Combined Hangout Data:**
- `externalId` = First episode's ID
- `combinedExternalIds` = All episode IDs
- Runtime = Sum of all runtimes

## 6. Time Calculation

### Start Time (DST-Aware)

```java
ZoneId zone = ZoneId.of(series.getTimezone());
LocalTime time = LocalTime.parse(series.getDefaultTime());
LocalDate date = calculateDateWithDayOverride(episode.airDate, series.getDayOverride());

ZonedDateTime zdt = ZonedDateTime.of(date, time, zone);
long startTimestamp = zdt.toEpochSecond();
```

IANA timezone handles DST automatically.

### Day Override

When `dayOverride` is set, find next occurrence of that day on/after air date:
- Episode airs: Tuesday Jan 7
- dayOverride: 4 (Thursday)
- Result: Thursday Jan 9

### Runtime Rounding

```
roundedMinutes = ceil(runtimeMinutes / 30) * 30
endTimestamp = startTimestamp + (roundedMinutes * 60)
```

Examples: 45 min → 60 min, 61 min → 90 min

## 7. Background Processing

### Architecture

```
EventBridge (2hr) ──▶ trigger-poll endpoint ──▶ TvMazePollingService
                                                        │
                                                        ▼
                                              SQS: tvmaze-updates
                                              (1 worker per host)
                                                        │
                                                        ▼
                                              TvMazeUpdateListener
                                                        │
                     ┌──────────────────────────────────┼──────────────────────┐
                     ▼                                  ▼                      ▼
               NEW_EPISODE                        UPDATE_TITLE           REMOVE_EPISODE
                     │                                  │                      │
                     └──────────────────────────────────┼──────────────────────┘
                                                        ▼
                                              SQS: episode-actions
                                              (3 workers per host)
                                                        │
                                                        ▼
                                              EpisodeActionListener
                                                        │
                                                        ▼
                                              WatchPartyBackgroundService
```

### Message Types

**SHOW_UPDATED:** Emitted by polling when TVMaze reports a tracked show changed. Triggers fetch of latest episodes and comparison.

**NEW_EPISODE:** Emitted when new episode detected. Creates hangouts for all groups watching the season.

**UPDATE_TITLE:** Emitted when episode title changes. Updates hangouts where `isGeneratedTitle=true` and `titleNotificationSent=false`.

**REMOVE_EPISODE:** Emitted when episode removed from TVMaze. Deletes hangouts and notifies users.

### Processing Guards

**NEW_EPISODE:**
- Skip if episodeId in `deletedEpisodeIds` (user deleted it)

**UPDATE_TITLE:**
- Skip if `isGeneratedTitle=false` (user customized title)
- Skip if `titleNotificationSent=true` (already notified)
- Skip if hangout is in the past

## 8. Notifications

### Watch Party Notification Types

| Event | Recipients | Message |
|-------|------------|---------|
| New episode added | GOING/INTERESTED on series | "New episode added: {title}" |
| Title updated | GOING/INTERESTED on series | "Episode renamed: {newTitle}" |
| Episode removed | GOING/INTERESTED on series | "{title} has been removed" |

### Methods

```java
// NotificationServiceImpl
void notifyWatchPartyNewEpisode(String seriesId, String groupId, Hangout hangout);
void notifyWatchPartyTitleUpdated(String seriesId, String groupId, Hangout hangout);
void notifyWatchPartyEpisodeRemoved(String seriesId, String groupId, String title);
```

### Title Notification Guard

The `titleNotificationSent` flag ensures only one title update notification is sent per hangout, even if TVMaze updates the title multiple times.

## 9. Version Filtering

Old app versions (< 2.0.0) don't see watch parties in group feed.

```java
// GroupServiceImpl
private static final String WATCH_PARTY_MIN_VERSION = "2.0.0";

if (seriesPointer.isWatchParty() && !clientInfo.isVersionAtLeast(WATCH_PARTY_MIN_VERSION)) {
    continue; // Filter out
}
```

## 10. TVMaze Client

### Configuration

```properties
tvmaze.base-url=https://api.tvmaze.com
```

### Rate Limiting

- TVMaze limit: 20 requests per 10 seconds
- Worker concurrency: 1 per host for tvmaze-updates queue
- Retry: 3 attempts with exponential backoff (2s, 3s, 4.5s)

### Episode Type Filtering

Only these types are tracked:
- `regular` - Standard episodes
- `significant_special` - Important specials (e.g., Christmas special)

Ignored: `insignificant_special` (e.g., behind-the-scenes)

## 11. Authorization

**Watch Party CRUD:**
- User must be member of the group
- Validated via `GroupRepository.isUserMemberOfGroup()`

**Series Interest:**
- User must be member of at least one group that owns the series
- Looks up `groupId` from EventSeries dynamically

## 12. Configuration

```properties
# SQS queues
watchparty.sqs.enabled=true
watchparty.tvmaze-updates-queue-url=<url>
watchparty.episode-actions-queue-url=<url>

# Polling
watchparty.polling.enabled=true
watchparty.polling.since-period=day  # day, week, month
watchparty.polling.cache-ttl-minutes=15
```

## 13. Testing

### Internal Endpoints (Staging Only)

```
POST /internal/watch-party/test-message
POST /internal/watch-party/trigger-show-update
POST /internal/watch-party/trigger-poll
```

Protected by `X-Api-Key` header.

### Staging Integration Tests

| Test Class | Purpose |
|------------|---------|
| `WatchPartyCrudTests` | Basic CRUD operations |
| `WatchPartyTvMazeTests` | TVMaze integration |
| `WatchPartyUpdateTests` | Settings update with cascade |
| `WatchPartyInterestTests` | Interest and version filtering |
| `WatchPartySqsTests` | SQS message processing |
| `WatchPartyPollingTests` | Polling flow |

## 14. Common Issues

### "Timezone not valid IANA timezone"
Ensure timezone is a valid IANA identifier like "America/New_York", not "EST" or "Eastern".

### Episodes not combining
Episodes must be <20 hours apart based on `airTimestamp`. Check that TVMaze provides valid air timestamps.

### Title not updating
Check:
1. `isGeneratedTitle` must be `true`
2. `titleNotificationSent` must be `false`
3. Hangout must be in the future

### Old version seeing watch parties
Check `X-App-Version` header is being set correctly by client.

## 15. Design Documents

For full design details, see:
- `docs/design/TV_WATCH_PARTY_DESIGN.md` - Complete technical design
- `docs/design/TV_WATCH_PARTY_IMPLEMENTATION_PHASES.md` - Implementation breakdown
- `context/TVMAZE_API_CONTEXT.md` - TVMaze API reference
