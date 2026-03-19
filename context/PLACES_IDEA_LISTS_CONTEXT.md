# Context: Places Idea Lists (Place-Enriched Ideas)

**AUDIENCE:** This document is for developers and AI agents working on the Places Idea Lists feature. It assumes familiarity with the Idea Lists feature (`IDEA_LISTS_CONTEXT.md`) and Saved Places (`PLACES_CONTEXT.md`).

## 1. Overview

Places Idea Lists add location-aware features to existing idea lists: autocomplete place search, map view, rich venue data (photos, hours, ratings), and filtering. This builds on the existing idea list infrastructure — idea lists already have `isLocation` and `category` fields that identify them as place-oriented.

**Key architecture decision:** Synchronous enrichment on autocomplete select via `POST /places/enrich` (blocks ~1-3s, races user dwell time on confirmation screen). Async read-path safety net catches anything missed. All enrichment flows through a **PlaceEnrichmentCache DynamoDB table** before writing to idea records.

**Design doc:** `/hangouts/ios/tmp/docs/PLACES_ENRICHMENT_REDESIGN.md`

## 2. Architecture Summary

| Concern | Where | Provider | Cost |
|---------|-------|----------|------|
| Autocomplete (iOS) | Client | Apple MKLocalSearch | Free |
| Autocomplete (Android) | Client | Google Places Autocomplete | 10K free/mo |
| Place enrichment (photos, hours, rating) | Backend | Google Places API (New) — Find Place + Details + Photos | 10K free/mo per SKU |
| Photo storage | Backend | S3 + CloudFront | ~$0.10/mo for 10K places |
| Map rendering (iOS) | Client | Apple MapKit | Free |
| Map rendering (Android) | Client | Google Maps SDK | Free (28K loads/day) |
| Open/Closed computation | Client | Client-side from cached hours | Free |
| Enrichment cache | Backend | PlaceEnrichmentCache DynamoDB table | Standard DynamoDB pricing |

## 3. Data Model

### IdeaListMember Place Fields

All fields are optional for backward compatibility. Added to the existing `IdeaListMember` DynamoDB model in InviterTable.

| Attribute | Type | Description | Source |
|-----------|------|-------------|--------|
| `address` | String | Human-readable address | Client (autocomplete) or user input |
| `latitude` | Double | For map pin placement | Client-provided |
| `longitude` | Double | For map pin placement | Client-provided |
| `placeCategory` | String | "restaurant", "bar", "home", "event_space", "park", "trail", "other" | Client selection |
| `googlePlaceId` | String | Google Places ID | Client (Android directly, or resolved by backend) |
| `applePlaceId` | String | Apple Maps place ID | Client (iOS only, stored for future use) |
| `cachedPhotoUrl` | String | S3 key for Google-sourced photo | Backend enrichment (via cache) |
| `cachedRating` | Double | e.g., 4.3 | Backend enrichment (via cache) |
| `cachedPriceLevel` | Integer | 1-4 ($-$$$$) | Backend enrichment (via cache) |
| `phoneNumber` | String | Tappable on detail page | Backend enrichment (via cache) |
| `websiteUrl` | String | Tappable on detail page | Backend enrichment (via cache) |
| `menuUrl` | String | For restaurants | Backend enrichment (via cache) |
| `cachedHoursJson` | String | Weekly hours JSON (Google weekday_text format) | Backend enrichment (via cache) |
| `lastEnrichedAt` | Instant | Timestamp of last enrichment (stored as epoch millis in DynamoDB) | Backend |
| `enrichmentStatus` | String | "PENDING", "ENRICHED", "FAILED", "PERMANENTLY_FAILED", or null | Backend |

**enrichmentStatus values:**
- `null` — non-place idea, or legacy data (never attempted)
- `"PENDING"` — place idea, enrichment not yet completed
- `"ENRICHED"` — successfully enriched with Google data
- `"FAILED"` — enrichment attempted but failed (retryable, failureCount < 3)
- `"PERMANENTLY_FAILED"` — 3 failures exhausted; only user-initiated `/places/enrich` retries

**Note:** The old `"NOT_APPLICABLE"` status has been replaced by `null`. Existing records with NOT_APPLICABLE are handled in backward-compat code (read-path skips them).

### PlaceEnrichmentCache Table (NEW — Standalone)

**Table name:** `PlaceEnrichmentCache` (NOT in InviterTable — separate DynamoDB table)

| Attribute | Type | Key | Description |
|-----------|------|-----|-------------|
| `cacheKey` | String | PK | Normalized: `lowercase(name)_round(lat,4)_round(lng,4)` |
| `googlePlaceId` | String | GSI1-PK (GooglePlaceIdIndex) | For fast lookup when Android provides it |
| `applePlaceId` | String | | iOS identifier (stored, not used for lookup) |
| `status` | String | | ENRICHED, FAILED, PERMANENTLY_FAILED |
| `cachedPhotoUrl` | String | | S3 key: `places/{cacheKey}/photo.jpg` |
| `cachedRating` | Double | | |
| `cachedPriceLevel` | Integer | | 1-4 |
| `cachedHoursJson` | String | | JSON array of 7 day strings |
| `phoneNumber` | String | | |
| `websiteUrl` | String | | |
| `menuUrl` | String | | |
| `lastEnrichedAt` | String | | ISO 8601 |
| `failureCount` | Integer | | 0-3, tracks retries |
| `createdAt` | String | | ISO 8601 |
| `ttl` | Long | | Epoch seconds, 90 days auto-cleanup |

**Cache lookup order:** googlePlaceId GSI first (if provided), then cacheKey PK fallback.

**No `ENRICHING` status** — the sync endpoint blocks, entries are only written as ENRICHED or FAILED.

## 4. Enrichment Pipeline

### Sync Enrichment (`POST /places/enrich`)

Client fires this when user selects an autocomplete result. Blocks until complete (~100ms cache hit, ~1.5-3s cache miss, 8s timeout).

```
Request received (name, lat, lng, optional googlePlaceId, optional applePlaceId)
  ↓
Step 1: Cache lookup
  → By googlePlaceId (GSI) if provided, then by cacheKey (PK)
  → Hit + ENRICHED: return immediately (status: CACHED)
  → PERMANENTLY_FAILED: reset failureCount, retry pipeline
  → Miss/FAILED: continue
  ↓
Step 2: In-memory dedup check
  → If another request for same cacheKey is inflight, wait for it
  ↓
Step 3: Find Google Place (skip if googlePlaceId provided)
  → Google Places API (New): searchText with locationBias
  → No match: write FAILED to cache, return FAILED
  ↓
Step 4: Get Place Details
  → Google Places API (New): Place Details
  → Fields: rating, priceLevel, weekdayDescriptions, nationalPhoneNumber, websiteUri, photos
  ↓
Step 5: Get Place Photo (optional — no photo is valid)
  → Google Places API (New): Place Photos (400×400px)
  → Upload to S3: places/{cacheKey}/photo.jpg
  ↓
Step 6: Write to PlaceEnrichmentCache table
  ↓
Step 7: Return enriched data (status: ENRICHED)
```

### Idea Creation (`POST /ideas`)

1. Backend checks enrichment cache by googlePlaceId or cacheKey
2. **Cache hit (ENRICHED):** copies all enriched fields onto new idea → status: ENRICHED immediately
3. **Cache miss:** creates idea with status: PENDING, triggers async enrichment in background
4. Non-place ideas: enrichmentStatus = null

### Read-Path Safety Net (`GET /idea-lists/{id}`)

On every list fetch, backend scans ideas and triggers async enrichment for those needing it:
- `enrichmentStatus == null` (legacy data)
- `enrichmentStatus == "PENDING"` (write-time enrichment not complete)
- `enrichmentStatus == "FAILED"` AND cache failureCount < 3
- `enrichmentStatus == "ENRICHED"` AND lastEnrichedAt > 30 days (stale per Google ToS)

**Max 5 triggers per list fetch.** Async, non-blocking — current data returned immediately.

## 5. Retry & Failure Handling

| Failure Count | Behavior |
|--------------|----------|
| 0-2 | Retry on next read-path trigger or `/places/enrich` call |
| 3 | Mark PERMANENTLY_FAILED. No more read-path retries. |
| User calls `/places/enrich` for PERMANENTLY_FAILED | Reset failureCount to 0, fresh retry (3 new attempts) |

### Circuit Breaker (resilience4j)

Wraps all Google Places API calls in `GooglePlacesClientImpl`:
```properties
resilience4j.circuitbreaker.instances.googlePlaces.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.googlePlaces.sliding-window-size=10
resilience4j.circuitbreaker.instances.googlePlaces.wait-duration-in-open-state=5m
resilience4j.circuitbreaker.instances.googlePlaces.permitted-number-of-calls-in-half-open-state=3
```

Circuit open → all enrichment calls return FAILED immediately (no Google API calls).

## 6. API Endpoints

### NEW: `POST /places/enrich`

Synchronous enrichment. Requires JWT authentication.

**Request:**
```json
{
  "name": "Sushi Nakazawa",
  "latitude": 40.7295,
  "longitude": -74.0028,
  "googlePlaceId": "ChIJN1t_tDeuEmsRUsoyG83frY4",
  "applePlaceId": "I63LYKU7G9BCPA"
}
```
- `name`: required
- `latitude`, `longitude`: required
- `googlePlaceId`: optional (Android provides, saves 1 API call)
- `applePlaceId`: optional (iOS 18+ only)

**Response:**
```json
{
  "status": "CACHED",
  "data": {
    "cachedPhotoUrl": "places/sushi-nakazawa_40.7295_-74.0028/photo.jpg",
    "cachedRating": 4.6,
    "cachedPriceLevel": 4,
    "cachedHoursJson": "[\"Monday: 5:00 – 10:00 PM\", ...]",
    "phoneNumber": "+12125240500",
    "websiteUrl": "https://sushinakazawa.com",
    "menuUrl": null,
    "googlePlaceId": "ChIJN1t_tDeuEmsRUsoyG83frY4"
  }
}
```
Status values: `"CACHED"` (cache hit), `"ENRICHED"` (pipeline ran), `"FAILED"` (data is null).

### MODIFIED: `POST /groups/{groupId}/idea-lists/{listId}/ideas`

No new request fields. On create, backend looks up enrichment cache:
- Cache hit → copies enriched data, returns with `enrichmentStatus: "ENRICHED"`
- Cache miss → returns with `enrichmentStatus: "PENDING"`, async enrichment fires

### UNCHANGED: `GET /groups/{groupId}/idea-lists/{listId}`

No contract changes. Read-path safety net triggers async enrichment for qualifying ideas.

## 7. S3 Photo Storage

- **Bucket**: `inviter-event-images-871070087012`
- **Key pattern**: `places/{cacheKey}/photo.jpg` (shared per-place, NOT per-idea)
- **Photo size**: 400×400px max
- **Served via**: existing CloudFront distribution
- **Re-fetch**: Every 30 days per Google ToS (triggered by read-path safety net)

**Note:** Old enrichment used `places/photos/{ideaId}.jpg` (per-idea). New enrichment uses `places/{cacheKey}/photo.jpg` (per-place, shared). Existing per-idea photos continue to work.

## 8. Key Files

| Component | Path | Description |
|-----------|------|-------------|
| **PlaceEnrichmentController** | `controller/PlaceEnrichmentController.java` | NEW: POST /places/enrich endpoint |
| **PlaceEnrichmentService** | `service/PlaceEnrichmentService.java` | Interface: sync pipeline, cache lookup, async enrichment, read-path safety net |
| **PlaceEnrichmentServiceImpl** | `service/impl/PlaceEnrichmentServiceImpl.java` | Full implementation with cache, dedup, pipeline |
| **GooglePlacesClient** | `service/GooglePlacesClient.java` | Interface for Google Places API (New) |
| **GooglePlacesClientImpl** | `service/impl/GooglePlacesClientImpl.java` | HTTP client with circuit breaker + metrics |
| **PlaceDetailsResult** | `service/PlaceDetailsResult.java` | POJO for extracted place details |
| **PlaceEnrichmentCacheEntry** | `model/PlaceEnrichmentCacheEntry.java` | DynamoDB model for cache table |
| **PlaceEnrichmentCacheRepository** | `repository/PlaceEnrichmentCacheRepository.java` | Cache table repository interface |
| **PlaceEnrichmentCacheRepositoryImpl** | `repository/impl/PlaceEnrichmentCacheRepositoryImpl.java` | Cache table repository implementation |
| **CacheKeyUtils** | `util/CacheKeyUtils.java` | Cache key normalization utility |
| **EnrichmentResult** | `dto/EnrichmentResult.java` | Pipeline result (CACHED/ENRICHED/FAILED + data) |
| **EnrichmentData** | `dto/EnrichmentData.java` | Enriched data fields DTO |
| **PlaceEnrichRequest** | `dto/PlaceEnrichRequest.java` | Request DTO for /places/enrich |
| **PlaceEnrichResponse** | `dto/PlaceEnrichResponse.java` | Response DTO for /places/enrich |
| **IdeaListMember** | `model/IdeaListMember.java` | Extended with place fields |
| **IdeaDTO** | `dto/IdeaDTO.java` | Extended with place fields in response |
| **IdeaListServiceImpl** | `service/impl/IdeaListServiceImpl.java` | Cache lookup on create, read-path safety net triggers |
| **GooglePlacesConfig** | `config/GooglePlacesConfig.java` | API key from SSM Parameter Store or properties |
| **DynamoDBTableInitializer** | `config/DynamoDBTableInitializer.java` | Creates PlaceEnrichmentCache table + TTL |

## 9. Configuration

### Application Properties
```properties
# Google Places API key (local dev — production uses SSM Parameter Store)
google.places.api-key=${GOOGLE_PLACES_API_KEY:}

# Resilience4j circuit breaker
resilience4j.circuitbreaker.instances.googlePlaces.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.googlePlaces.sliding-window-size=10
resilience4j.circuitbreaker.instances.googlePlaces.wait-duration-in-open-state=5m
resilience4j.circuitbreaker.instances.googlePlaces.permitted-number-of-calls-in-half-open-state=3
```

### Environment Variables (Production)
- Google Places API key: fetched from AWS SSM Parameter Store (see `GooglePlacesConfig.java`)

## 10. Attribution Requirements

- "Powered by Google" must appear wherever Google-sourced data is displayed (client responsibility)
- Show attribution when any of: `cachedPhotoUrl != null || cachedRating != null || cachedHoursJson != null`
- Apple Maps attribution handled automatically by MapKit (client responsibility)
- Google ToS requires autocomplete results displayed on a Google Map — avoided on iOS by using Apple for autocomplete

## 11. Monitoring

### Metrics (via Micrometer/Prometheus)
- `place_enrichment_api_calls{sku=find_place}` — Find Place calls
- `place_enrichment_api_calls{sku=place_details}` — Place Details calls
- `place_enrichment_api_calls{sku=place_photos}` — Place Photos calls
- Circuit breaker state available via resilience4j actuator endpoints

### Alarms (recommended)
- 8,000 calls/month per SKU (80% of 10K free tier)
- Circuit breaker open events

## 12. Testing

### Unit Tests
| Test Class | What it Tests |
|-----------|-------------|
| `PlaceEnrichmentServiceImplTest` | Cache lookup, full pipeline, dedup, read-path safety net, failure counting, PERMANENTLY_FAILED reset |
| `GooglePlacesClientImplTest` | API calls, response parsing, circuit breaker fallbacks, metrics |
| `IdeaListServiceImplTest` | Cache hit on create, cache miss + async, enrichmentStatus logic, read-path triggers |
| `PlaceEnrichmentControllerTest` | MockMvc: endpoint validation, auth, response shapes |
| `CacheKeyUtilsTest` | Normalization: case, spaces, coordinate rounding |
| `PlaceEnrichmentCacheRepositoryImplTest` | PK lookup, GSI lookup, save |

### Key Test Scenarios
1. **iOS flow**: name + coords only → Find Place + Details + Photos → cache → ENRICHED
2. **Android flow**: googlePlaceId provided → skip Find Place → Details + Photos → cache → ENRICHED
3. **Cache hit**: second request for same place → CACHED, no Google API calls
4. **Speed-tapper**: idea created before enrichment returns → PENDING → read-path catches it
5. **Failure escalation**: 3 failures → PERMANENTLY_FAILED → user retry resets count
6. **Stale re-enrichment**: >30 days old → read-path triggers refresh
7. **Circuit breaker open**: all calls return FAILED immediately
8. **Graceful degradation**: enrichment service disabled (no API key) → no crashes, ideas still created

## 13. Backward Compatibility

- All new fields are optional/nullable — existing ideas without place data continue to work
- New IdeaDTO fields are additive — old clients ignore unknown JSON fields
- Existing `NOT_APPLICABLE` status in DB is handled (read-path skips them, same as PERMANENTLY_FAILED)
- Old S3 photo paths (`places/photos/{ideaId}.jpg`) continue to work — new photos use `places/{cacheKey}/photo.jpg`
- `lastEnrichedAt` serialized as ISO 8601 string in IdeaDTO (stored as epoch millis in DynamoDB)

## 14. Related Context

- **Idea Lists**: `context/IDEA_LISTS_CONTEXT.md` — base feature this extends
- **Saved Places**: `context/PLACES_CONTEXT.md` — separate feature (address book), not directly related
- **Design Doc**: `/hangouts/ios/tmp/docs/PLACES_ENRICHMENT_REDESIGN.md` — full spec (supersedes PLACE_API_DECISION.md)
