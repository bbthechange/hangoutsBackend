# Context: TVMaze API Integration for Viewing Parties

**AUDIENCE:** This document is for developers and AI agents implementing the TV viewing party feature. It provides comprehensive details about the TVMaze API, its limitations, and recommended implementation patterns.

## 1. Overview

The viewing party feature allows users to schedule group viewing events for a TV season. Users search for a show, select a season, and the system creates a series of hangouts based on episode air dates. The feature uses the [TVMaze API](https://www.tvmaze.com/api) as its data source.

### Key Capabilities
- Search for TV shows by name
- Retrieve episode lists with air dates for a season
- Periodic refresh to detect episode additions, removals, or changes
- Handle incomplete data for upcoming/unannounced seasons

### Limitations to Communicate to Users
- Episode data may be incomplete for upcoming seasons (titles show as "TBA")
- Episode counts (`episodeOrder`) are often null for unannounced seasons
- Reality shows have less reliable data than scripted shows
- Data updates can take up to 1 hour to propagate through TVMaze's cache

## 2. API Fundamentals

| Aspect | Details |
|--------|---------|
| **Base URL** | `https://api.tvmaze.com` |
| **Authentication** | None required (completely free, no API key) |
| **Response Format** | JSON with HAL/HATEOAS conventions |
| **CORS** | Enabled (works in browsers) |
| **License** | CC BY-SA 4.0 (must credit TVMaze) |

### Rate Limiting

| Limit | Details |
|-------|---------|
| **Rate** | Minimum 20 requests per 10 seconds per IP |
| **Error Code** | HTTP 429 when exceeded |
| **Edge Cache** | Popular endpoints often served from cache, won't count against limit |
| **Ban Threshold** | 300,000+ requests/hour can trigger IP ban |

**Recommended handling:**
```java
// Retry logic for 429 errors
if (response.statusCode() == 429) {
    Thread.sleep(2000); // Back off 2 seconds
    // Retry up to 3 times
}
```

### Caching Behavior

| Cache Type | Duration |
|------------|----------|
| Standard endpoints (shows, episodes) | 60 minutes |
| Show index (`/shows?page=`) | 24 hours |
| Full schedule (`/schedule/full`) | 24 hours |

**Important:** After updates on TVMaze website, allow up to 1 hour for API propagation.

### Connection Management
- Do NOT leave more than 1 idle HTTP connection open to TVMaze servers
- Either close connections after each request or reuse connections properly
- Violating this can result in IP blocks

### Best Practice: User-Agent Header
Set a custom User-Agent header identifying your application:
```java
"User-Agent: HangoutsApp/1.0 (contact@example.com)"
```
This allows TVMaze to contact you if there are issues.

## 3. API Endpoints

### Step 1: Show Search

**Endpoint:** `GET /search/shows?q={query}`

**Example:** `GET /search/shows?q=drag%20race`

**Response Structure:**
```json
[
  {
    "score": 0.9,
    "show": {
      "id": 1526,
      "name": "RuPaul's Drag Race",
      "type": "Reality",
      "status": "Running",
      "premiered": "2009-02-02",
      "network": { "name": "MTV", "country": { "code": "US" } },
      "webChannel": null,
      "image": { "medium": "https://...", "original": "https://..." },
      "summary": "<p>RuPaul searches for...</p>",
      "externals": { "tvrage": null, "thetvdb": 85002, "imdb": "tt1353056" },
      "_links": {
        "self": { "href": "https://api.tvmaze.com/shows/1526" },
        "previousepisode": { "href": "https://api.tvmaze.com/episodes/..." }
      }
    }
  }
]
```

**Key Fields:**
- `score` - Relevance score (higher = better match)
- `show.id` - TVMaze show ID (use for subsequent queries)
- `show.status` - "Running", "Ended", "To Be Determined", "In Development"
- `show.image` - May be null for some shows
- `show.url` - Use for attribution link

**Alternative:** `GET /singlesearch/shows?q={query}` returns single best match (less forgiving of typos).

### Step 2: Get Seasons for a Show

**Endpoint:** `GET /shows/{showId}/seasons`

**Example:** `GET /shows/1526/seasons`

**Response Structure:**
```json
[
  {
    "id": 5997,
    "url": "https://www.tvmaze.com/seasons/5997/rupauls-drag-race-season-1",
    "number": 1,
    "name": "",
    "episodeOrder": 9,
    "premiereDate": "2009-02-02",
    "endDate": "2009-03-23",
    "network": { "id": 34, "name": "Logo", "country": { "code": "US" } },
    "webChannel": null,
    "image": { "medium": "https://...", "original": "https://..." },
    "summary": null
  }
]
```

**Key Fields:**
- `id` - Season ID (use for episode queries)
- `number` - Season number (1, 2, 3...)
- `episodeOrder` - Expected episode count (**OFTEN NULL for upcoming seasons**)
- `premiereDate` / `endDate` - Season date range (may be null)

### Step 3: Get Episodes for a Season

**Endpoint:** `GET /seasons/{seasonId}/episodes`

**Example:** `GET /seasons/176432/episodes`

**Response Structure:**
```json
[
  {
    "id": 112456,
    "url": "https://www.tvmaze.com/episodes/112456/...",
    "name": "You Can't Keep A Good Drag Queen Down!",
    "season": 18,
    "number": 1,
    "type": "regular",
    "airdate": "2026-01-02",
    "airtime": "20:00",
    "airstamp": "2026-01-03T01:00:00+00:00",
    "runtime": 90,
    "rating": { "average": null },
    "image": { "medium": "https://...", "original": "https://..." },
    "summary": "<p>The queens return...</p>",
    "_links": {
      "self": { "href": "https://api.tvmaze.com/episodes/112456" },
      "show": { "href": "https://api.tvmaze.com/shows/1526", "name": "RuPaul's Drag Race" }
    }
  }
]
```

**Key Fields:**
- `id` - Episode ID (store for change detection)
- `name` - Episode title (often "TBA" or "Episode X" for future episodes)
- `season` / `number` - Season and episode numbers
- `airdate` - Date in YYYY-MM-DD format
- `airstamp` - Full ISO 8601 timestamp with timezone
- `runtime` - Duration in minutes (may vary, may be null)
- `image` - May be null

**Alternative:** `GET /shows/{showId}/episodes?specials=1` includes special episodes.

### Step 4: Detecting Updates (for Periodic Refresh)

**Endpoint:** `GET /updates/shows?since={period}`

**Periods:** `day`, `week`, `month`

**Example:** `GET /updates/shows?since=day`

**Response Structure:**
```json
{
  "1526": 1766280973,
  "1527": 1766234179,
  "2345": 1766198432
}
```

- Key = Show ID
- Value = Unix timestamp of last update

**Important:** This returns ALL updated shows (1000+ for `?since=week`). You must filter client-side to shows you care about.

**Update Behavior:** The `updated` timestamp is recursive - any change to episodes, cast, or crew updates the show's timestamp.

## 4. Data Completeness by Show Type

Based on testing across genres, here's what to expect:

| Show Type | Episode Data | episodeOrder | Air Dates | Reliability |
|-----------|--------------|--------------|-----------|-------------|
| Scripted streaming (Netflix, Prime) | Full list | Usually set | All same day | Excellent |
| Scripted cable/broadcast | Full list | Usually set | Weekly dates | Good |
| Reality competition | Partial list | Often null | Partial | Poor |
| "In Development" shows | None | null | None | None |
| Brand new series | Minimal | null | Premiere only | Poor |

### Specific Examples (as of Dec 2025)

| Show | Type | Season | Episodes Listed | episodeOrder | Notes |
|------|------|--------|-----------------|--------------|-------|
| Bridgerton | Drama/Netflix | S4 | 8 | 8 | Full data, split release dates |
| The Boys | Sci-Fi/Prime | S5 | 8 | 8 | Weekly dates, titles TBA |
| The Night Agent | Thriller/Netflix | S3 | 10 | ? | Full list, same-day release |
| Survivor | Reality/CBS | S50 | 4 | null | Partial, only first few eps |
| RuPaul's Drag Race | Reality/MTV | S18 | 2 | null | Only special + premiere |
| The Voice | Reality/NBC | S29 | ? | 13 | episodeOrder set, incomplete eps |
| One Piece | Action/Netflix | S2 | 8 | null | Episodes listed, episodeOrder null |

## 5. Handling Missing Data

### When `episodeOrder` is Null

1. Check if episodes are listed via `/seasons/{id}/episodes`
2. If episodes exist, count them
3. If no episodes, fall back to previous season's `episodeOrder`
4. Inform user: "Episode count for this season hasn't been announced. Based on last season, we expect approximately X episodes."

### When Episodes Are Missing

For seasons with incomplete episode lists:
1. Show confirmed episodes with their air dates
2. Display message: "Additional episodes will be added as they're announced by the network."
3. Set up periodic refresh to detect new episodes

### Recommended User Messaging

```
"Season 18 of RuPaul's Drag Race"

Episode 1: Jan 2, 2026 - "You Can't Keep A Good Drag Queen Down!"
Episode 2: (not yet announced)
...

Note: Only 2 of an expected ~16 episodes have been announced.
Last season had 16 episodes. Episodes typically get added
as the season progresses. We'll update automatically.
```

## 6. Periodic Refresh Strategy

### Recommended Approach

1. **Daily poll of `/updates/shows?since=day`** (single API call)
2. Compare timestamps to your stored `lastUpdated` per show
3. If changed, re-fetch `/seasons/{seasonId}/episodes`
4. Detect changes:
   - New episodes (IDs not in your database)
   - Removed episodes (your IDs return 404)
   - Updated episodes (compare name, airdate, runtime)

### Change Detection

```java
// For each episode you have stored
GET /episodes/{episodeId}

// If 404 → Episode was removed
// If different ID at same season/number → Episode was reordered
// If name changed from "TBA" → Title was announced
```

### Refresh Timing Recommendations

| Scenario | Frequency |
|----------|-----------|
| Shows airing this week | Daily |
| Shows airing next month | Every 3 days |
| Shows airing in 2+ months | Weekly |
| 2 weeks before premiere | Daily (to catch late additions) |

## 7. Attribution Requirements

**License:** CC BY-SA 4.0

### Required
- Link back to TVMaze from where the data is displayed
- Credit TVMaze as the data source

### Acceptable Approaches
1. **Make show/episode names clickable** linking to `show.url` or `episode.url` from API
2. **Add footer text:** "Show data from [TVMaze](https://tvmaze.com)"
3. **About/Credits screen:** "TV show information provided by TVMaze.com"

### Not Sufficient
- Only mentioning TVMaze in App Store description
- Only having attribution on a hidden settings page with no visible link near the data

### ShareAlike Note
If you redistribute the raw data via your own API, you must license it CC BY-SA. Displaying data in your app UI does not trigger this requirement.

## 8. Error Handling

### HTTP Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response |
| 404 | Not found (show, episode, season) | Episode may have been removed |
| 429 | Rate limited | Wait 2 seconds, retry up to 3 times |
| 5xx | Server error | Retry with exponential backoff |

### Common Edge Cases

1. **Show search returns empty array** - No matches found
2. **Episode `image` is null** - Use show image or placeholder
3. **Episode `name` is "TBA" or "Episode X"** - Display as-is, will update later
4. **Episode `runtime` is null** - Use show's default runtime or omit
5. **Season `episodeOrder` is null** - Use previous season's count as estimate
6. **`airdate` is null but `airstamp` exists** - Parse date from airstamp
7. **Both `network` and `webChannel` are null** - Show may be in development

## 9. Data Model Recommendations

### TVMaze Show Cache

```java
public class TvMazeShow {
    private Integer tvMazeId;           // TVMaze show ID
    private String name;
    private String status;              // Running, Ended, etc.
    private String imageUrl;
    private String tvMazeUrl;           // For attribution
    private Long lastUpdated;           // Unix timestamp from /updates
    private Instant lastFetched;        // When we last queried
}
```

### TVMaze Season Cache

```java
public class TvMazeSeason {
    private Integer tvMazeSeasonId;     // TVMaze season ID
    private Integer tvMazeShowId;
    private Integer seasonNumber;
    private Integer episodeOrder;       // May be null
    private LocalDate premiereDate;
    private LocalDate endDate;
}
```

### TVMaze Episode Cache

```java
public class TvMazeEpisode {
    private Integer tvMazeEpisodeId;    // TVMaze episode ID
    private Integer tvMazeSeasonId;
    private Integer seasonNumber;
    private Integer episodeNumber;
    private String name;                // May be "TBA"
    private LocalDate airDate;
    private String airTime;             // "20:00" format
    private ZonedDateTime airStamp;     // Full timestamp
    private Integer runtime;            // Minutes, may be null
    private String imageUrl;            // May be null
    private String tvMazeUrl;           // For attribution
}
```

## 10. Implementation Checklist

- [ ] Set custom User-Agent header
- [ ] Implement retry logic for HTTP 429 with exponential backoff
- [ ] Handle null `episodeOrder` with previous season fallback
- [ ] Handle null/missing episode images gracefully
- [ ] Display "TBA" episode names appropriately
- [ ] Store TVMaze episode IDs for change detection
- [ ] Implement periodic refresh job
- [ ] Add visible TVMaze attribution link near episode data
- [ ] Communicate data limitations to users for upcoming seasons
- [ ] Handle show "In Development" status (no episode data available)

## 11. Alternative Data Sources

If TVMaze data is insufficient, these alternatives exist but have tradeoffs:

| Source | Pros | Cons |
|--------|------|------|
| **TMDB** | Good movie data, has `episode_count` | Requires API key, TV data less complete |
| **TheTVDB** | Most complete TV data | Requires subscription, has had downtime issues |
| **IMDb** | Authoritative | No public API, scraping prohibited |

**Recommendation:** Stick with TVMaze for this feature. Its limitations (incomplete upcoming season data) are shared by all sources since the data simply hasn't been announced by networks yet.

## 12. References

- [TVMaze API Documentation](https://www.tvmaze.com/api)
- [API Rate Limiting Discussion](https://www.tvmaze.com/threads/5428/api-throttling)
- [Episode Updates Best Practices](https://www.tvmaze.com/threads/1233/get-new-episodes-and-other-changes-to-show)
- [API Changelog](https://www.tvmaze.com/threads/4/api-changelog)
- [CC BY-SA 4.0 License](https://creativecommons.org/licenses/by-sa/4.0/)
