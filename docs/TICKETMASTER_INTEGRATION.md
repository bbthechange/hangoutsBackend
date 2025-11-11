# Ticketmaster Integration: Implementation & Limitations

## Overview

This document describes the Ticketmaster Discovery API integration for parsing event URLs. This implementation addresses the bot detection issues identified in `EVENT_PARSING_STRATEGY.md` by using Ticketmaster's official API instead of HTML scraping.  Unfortunately there is no way to convert from the event ID in the ticketmaster URL into the ID used by their API (seriously!). This means all parsing will be best effort.

## Architecture

### Components

1. **TicketmasterUrlParser** - Extracts search parameters from Ticketmaster URLs
2. **TicketmasterApiService** - Calls Discovery API to fetch event details
3. **ExternalEventService** - Routes Ticketmaster URLs to API service

### Flow

```
User provides Ticketmaster URL
         ↓
ExternalEventService.parseUrl()
         ↓
TicketmasterUrlParser.parse()
    (extracts keyword, city, state, date)
         ↓
TicketmasterApiService.searchEvent()
    (calls Discovery API with search parameters)
         ↓
Returns ParsedEventDetailsDto
```

## Setup

### 1. Get Ticketmaster API Key

Register at https://developer.ticketmaster.com/ to get a free API key.

**Rate Limits (Free Tier):**
- 5,000 API calls per day
- 5 requests per second

### 2. Configure API Key

Set the API key via environment variable:

```bash
export TICKETMASTER_API_KEY=your_api_key_here
```

Or add to `application.properties`:
```properties
ticketmaster.api.key=your_api_key_here
```

## URL Format Analysis

### Supported Formats

Ticketmaster URLs follow these patterns:

**Format 1: Full Details**
```
https://www.ticketmaster.com/{event-name}-{city}-{state}-{MM-DD-YYYY}/event/{ID}
```
Example:
```
https://www.ticketmaster.com/new-orleans-saints-v-seattle-seahawks-seattle-washington-09-21-2025/event/0F006278E6DF3E1A
```

**Format 2: Partial Details**
```
https://www.ticketmaster.com/{event-name}/event/{ID}
```
Example:
```
https://www1.ticketmaster.com/zpl-jingle-jam-featuring-lizzo-why-dont-we/event/05005726960D2E82
```

**Format 3: Simple (NOT SUPPORTED)**
```
http://www.ticketmaster.com/event/{ID}
```
❌ No extractable information

### Parsing Strategy

The URL parser uses heuristics to extract components:

1. **Date** (most reliable): Regex pattern `\d{2}-\d{2}-\d{4}` at end of slug
2. **State**: Lookup in state name → code map (handles multi-word states like "north-carolina")
3. **City**: Word immediately before state name
4. **Keyword**: Everything else becomes the search keyword

### Real-World URL Pattern Analysis

Analysis of 210 real Ticketmaster URLs (Nov 2025) revealed:
- **100% have dates** (when slugs exist)
- **100% have states** (when location info exists)
- **90.5% have 6-7 words before state** (sports: team-vs-team-city-state-date format)
- **9.5% have 3-5 words before state** (concerts/shows: artist-city-state-date format)
- **0% have exactly 2 words before state** (the "state-fair" ambiguity doesn't occur in practice)

The theoretical 2-word ambiguity problem (e.g., `state-fair-california` vs `artist-city-state`) does not appear in real Ticketmaster URLs.

## Known Limitations & Fragility

### ⚠️ Critical Limitations

#### 1. Simple URLs Are Unsupported

**Problem:**
```
http://www.ticketmaster.com/event/3B00533D15B0171F
```
No slug = no extractable information

**Impact:** Parser returns `null`, user gets error message

**Workaround:** User must provide a URL with event details in the path

---

#### 2. Ambiguous City/Event Name Parsing

**Status:** Not a problem in practice based on real data analysis.

**Theoretical scenario (doesn't occur):**
```
new-orleans-saints-v-seattle-seahawks-seattle-washington-09-21-2025
                                        ^^^^^^^
              Is "seattle" part of team name or the city?
```

**What actually happens:** Real URLs always have enough context (6-7 words) that the parser correctly extracts:
- keyword: `"new orleans saints v seattle seahawks"`
- city: `"Seattle"` (second occurrence)
- Search accuracy is high because keyword, city, state, and date all filter correctly

---

#### 3. Multi-Word Locations

**Problem:** Cities and states with hyphens are indistinguishable from event names:
- Cities: `new-york`, `salt-lake-city`, `san-francisco`
- States: `north-carolina`, `south-dakota`, `new-mexico`

**Impact:** Parser may incorrectly classify parts of event name as location

**Example:**
```
broadway-show-new-york-new-york-03-10-2025
              ^^^^^^^^ ^^^^^^^^
         Event name?  City? State?
```

**Actual parsing:**
- Keyword: `"broadway show new"` (loses "york")
- City: `"York"` (incorrect)
- State: `"NY"` (correct)

---

#### 4. Search Result Accuracy

**Problem:** Discovery API search returns multiple events matching keywords

**Impact:** We return the **first result**, which may not be the exact event from the URL

**Example Scenario:**
- URL for "Coldplay Concert" on Sept 15 in Seattle
- Search returns 3 Coldplay concerts in September
- We return the first one (maybe Sept 12 in Portland)

**Mitigation:** We filter by:
- State code
- City name (if available)
- Exact date (if available)

This improves accuracy but doesn't guarantee correctness.

---

#### 5. Legacy ID vs Discovery API ID

**Problem:** The event ID in the URL is **not** the Discovery API ID

**Example:**
- URL ID: `0F006278E6DF3E1A` (legacy)
- Discovery API ID: `vv16AZAjJPOZACd2ad` (different)

**Impact:** Cannot use URL ID to directly fetch event from API - must search instead

**Why This Matters:** Search-based approach is inherently less precise than direct ID lookup

---

### ⚠️ Edge Cases

#### Date Format Variations

**Assumption:** Dates are always `MM-DD-YYYY` at end of slug

**Risk:** If Ticketmaster uses different formats (e.g., international sites), parser fails silently

**Example:**
```
concert-london-england-15-09-2025  # DD-MM-YYYY format
```
Parser treats `15-09-2025` as valid `MM-DD-YYYY` → invalid date → ignores date

---

#### International Domains

**Tested:** `www.ticketmaster.com`, `www1.ticketmaster.com`

**Unknown:** International sites like `ticketmaster.co.uk`, `ticketmaster.de`
- May use different URL structures
- May use different state/region naming
- May use different date formats

**Recommendation:** Test with international URLs before claiming support

---

#### Venue URLs

**Not Supported:**
```
https://www.ticketmaster.com/venue-name-city/venue/{ID}
```

**Detection:** Parser checks for `/event/` in URL - venue URLs have `/venue/` instead

**Impact:** Venue URLs throw exception

---

## API Response Mapping

### Discovery API → ParsedEventDetailsDto

| Discovery API Field | DTO Field | Notes |
|---------------------|-----------|-------|
| `name` | `title` | Event name |
| `info` | `description` | Event description (often null) |
| `dates.start.dateTime` | `startTime` | ISO 8601 format |
| `dates.end.dateTime` | `endTime` | Often null for single-time events |
| `images[0].url` | `imageUrl` | First image in array |
| `url` | `url` | Official event page URL |
| `_embedded.venues[0]` | `location` | Venue address details |
| `_embedded.venues[0].name` | `location.name` | Venue name |
| `_embedded.venues[0].address.line1` | `location.street` | Street address |
| `_embedded.venues[0].city.name` | `location.city` | City name |
| `_embedded.venues[0].state.stateCode` | `location.state` | State code (e.g., "WA") |
| `_embedded.venues[0].postalCode` | `location.zipCode` | Postal code |
| `_embedded.venues[0].country.countryCode` | `location.country` | Country code (e.g., "US") |

## Testing

### Unit Tests

Location: `src/test/java/com/bbthechange/inviter/service/ticketmaster/TicketmasterUrlParserTest.java`

**Coverage:**
- ✅ Full slug format (event-city-state-date)
- ✅ Partial slug formats
- ✅ Multi-word states (north-carolina, new-york)
- ✅ Date extraction
- ✅ Simple URLs (returns null)
- ✅ Invalid dates (ignores gracefully)
- ✅ Ambiguous city names

### Manual Testing

To test the integration:

1. **Get test URLs from Ticketmaster:**
   - Search for any event at https://www.ticketmaster.com
   - Copy event URL from browser

2. **Call parse endpoint:**
   ```bash
   curl -X POST http://localhost:8080/api/external/parse \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer YOUR_JWT" \
     -d '{
       "url": "https://www.ticketmaster.com/event-name-city-state-date/event/ID"
     }'
   ```

3. **Verify response:**
   - Check that event title matches
   - Check that date/time is correct
   - Check that venue/location is correct
   - **Critical:** Verify it's the EXACT event from URL (not a similar event)

## Production Considerations

### Rate Limiting

**Free Tier Limits:**
- 5,000 calls/day
- 5 requests/second

**Monitoring:**
- Track API usage via CloudWatch metrics
- Alert when approaching daily limit
- Implement graceful degradation (return error, don't crash)

### Error Handling

**API Key Missing:**
```
EventParseException: "Ticketmaster API key is not configured"
```

**No Events Found:**
```
EventParseException: "No events found matching URL: {slug}"
```

**Simple URL Format:**
```
EventParseException: "Ticketmaster URL does not contain event details in the path..."
```

### Metrics & Logging

**Recommended Metrics:**
```java
// In TicketmasterApiService
micrometer.counter("ticketmaster.api.calls.total", "status", "success");
micrometer.counter("ticketmaster.api.calls.total", "status", "error");
micrometer.counter("ticketmaster.api.calls.total", "status", "not_found");
micrometer.timer("ticketmaster.api.latency");
```

**Log Important Events:**
- URL parsing failures
- Search result count (to detect ambiguous matches)
- API errors
- Rate limit warnings

## Future Improvements

### 1. Confidence Score

Add confidence scoring to search results:
```java
public class EventSearchResult {
    ParsedEventDetailsDto event;
    double confidence; // 0.0 - 1.0
}
```

**Scoring Factors:**
- Exact date match: +0.5
- City match: +0.2
- State match: +0.2
- Keyword similarity: +0.1

Return error if confidence < 0.7

### 2. Return Multiple Results

Instead of returning first result, return top N matches:
```java
public List<ParsedEventDetailsDto> searchEvents(ParsedTicketmasterUrl parsedUrl) {
    // Return top 3 matches with confidence scores
}
```

Let user pick the correct event.

### 3. Enhanced URL Parsing

Improve parser with:
- Machine learning to classify event name vs. location
- City name database (handle multi-word cities better)
- International support (country-specific state/region lists)

### 4. Caching

Cache Discovery API responses to reduce API calls:
```java
@Cacheable(value = "ticketmaster-events", key = "#parsedUrl.keyword")
```

TTL: 1 hour (events don't change frequently)

### 5. Fallback to Playwright

If Discovery API search returns no results or confidence is low:
1. Try Playwright scraping as fallback
2. Extract `disco_event_id` from rendered HTML
3. Fetch from Discovery API by exact ID

**Trade-off:** Adds Playwright overhead, but improves accuracy

## Comparison: API vs. Scraping

| Aspect | Discovery API (Current) | HTML Scraping (Phase 1) | Playwright (Phase 2) |
|--------|------------------------|--------------------------|----------------------|
| **Bot Detection** | ✅ No issues | ❌ 401 Unauthorized | ✅ Bypasses detection |
| **Accuracy** | ⚠️ Search-based (fuzzy) | ✅ Exact event data | ✅ Exact event data |
| **Speed** | ✅ Fast (API call) | ✅ Fast (HTTP) | ❌ Slow (2-5s) |
| **Memory** | ✅ Low | ✅ Low | ❌ High (50-150MB) |
| **Deployment** | ✅ Simple | ✅ Simple | ❌ Docker required |
| **Maintenance** | ⚠️ API changes | ⚠️ HTML changes | ⚠️ HTML changes |
| **Rate Limits** | ⚠️ 5,000/day | ✅ Unlimited | ✅ Unlimited |

## Conclusion

The Discovery API approach provides a **lightweight, bot-resistant solution** for Ticketmaster URLs, but with **accuracy trade-offs** due to search-based matching.

**When to Use:**
- ✅ User provides full URL with event details
- ✅ Event is upcoming (searchable via API)
- ✅ Within rate limit budget

**When to Avoid:**
- ❌ Simple `/event/ID` URLs
- ❌ High-precision requirements (must match exact event)
- ❌ Past events (may not be searchable)

**Recommendation:** Monitor search result accuracy in production. If accuracy is poor, consider adding Playwright fallback or returning multiple results for user selection.
