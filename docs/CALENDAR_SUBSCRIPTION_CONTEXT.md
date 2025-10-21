# Calendar Subscription Feature - Implementation Plan

## Overview

Enable users to subscribe to a group's calendar feed via standard calendar applications (iOS Calendar, Google Calendar, etc.) using the ICS/iCalendar format. Calendar apps will automatically poll for updates and display new/updated hangouts without manual intervention.

**Key Benefits:**
- Auto-updates when new hangouts are created (no manual exports)
- Works with any calendar app supporting ICS subscriptions
- Zero manual intervention for hosts or members
- Survives app deletion (subscription persists in calendar app)

## Architecture Decision: Token on GroupMembership

**Design:** Store calendar subscription token directly on the `GroupMembership` record rather than creating separate subscription entities.

**Rationale:**
1. **Automatic cleanup:** When user leaves group, their subscription is automatically invalidated (membership record deleted)
2. **Simpler data model:** No separate entities or pointer records to maintain
3. **Cost savings:** Fewer DynamoDB operations per request
4. **Correct semantics:** Subscription is inherently tied to group membership

**Trade-off:** Requires new GSI (`CalendarTokenIndex`), but cost is negligible (<$0.01/month at 50K groups).

## Database Schema Changes

### 1. Modified Entity: GroupMembership

Add calendar subscription fields to existing `GroupMembership` model:

```java
@DynamoDbBean
public class GroupMembership extends BaseItem {
    // Existing fields
    private String groupId;
    private String userId;
    private String displayName;
    private String groupName;

    // NEW FIELDS
    private String calendarToken;        // UUID for calendar subscription (null if not subscribed)

    // Keys remain the same:
    // PK = GROUP#{groupId}
    // SK = USER#{userId}
    // gsi1pk = USER#{userId} (UserGroupIndex)
    // gsi1sk = GROUP#{groupId} (UserGroupIndex)

    // NEW: For token lookup
    // gsi2pk = TOKEN#{calendarToken} (CalendarTokenIndex)

    @DynamoDbSecondaryPartitionKey(indexNames = "CalendarTokenIndex")
    public String getGsi2pk() {
        return super.getGsi2pk();
    }

    public String getCalendarToken() {
        return calendarToken;
    }

    public void setCalendarToken(String calendarToken) {
        this.calendarToken = calendarToken;
        if (calendarToken != null) {
            setGsi2pk("TOKEN#" + calendarToken);
        } else {
            setGsi2pk(null);
        }
        touch();
    }
}
```

### 2. New GSI: CalendarTokenIndex

**Index Configuration:**
- Name: `CalendarTokenIndex`
- Partition Key: `gsi2pk` (String)
- Sort Key: None (token is unique)
- Projection: `ALL` (include all attributes)
- Billing: On-demand (scales automatically)

**Purpose:** Enable fast lookup of membership by subscription token.

**Cost Impact at 50K groups, 15% subscription rate:**
- 150K memberships with tokens
- Storage: ~150KB (negligible)
- Monthly cost: <$0.01

### 3. Modified Entity: Group

Add field to track when hangouts were last modified (for ETag calculation):

```java
@DynamoDbBean
public class Group extends BaseItem {
    // Existing fields
    private String groupId;
    private String groupName;
    private boolean isPublic;
    private String mainImagePath;
    private String backgroundImagePath;

    // NEW FIELD
    private Instant lastHangoutModified;  // Updated when any hangout created/updated/deleted

    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getLastHangoutModified() {
        return lastHangoutModified;
    }

    public void setLastHangoutModified(Instant lastHangoutModified) {
        this.lastHangoutModified = lastHangoutModified;
        touch();
    }
}
```

**Update Triggers:**
- `HangoutService.createHangout()` → Update all associated groups
- `HangoutService.updateHangout()` → Update all associated groups
- `HangoutService.deleteHangout()` → Update all associated groups

## API Specifications

### API 1: Create Calendar Subscription

**Endpoint:** `POST /v1/calendar/subscriptions/{groupId}`

**Authentication:** JWT required (Bearer token)

**Purpose:** Generate unique subscription URL for user to subscribe to group's calendar

**Request:**
```http
POST /v1/calendar/subscriptions/group-123
Authorization: Bearer eyJhbGc...
```

**Response (201 Created):**
```json
{
  "subscriptionId": "group-123",
  "groupId": "group-123",
  "groupName": "Seattle Hikers",
  "subscriptionUrl": "https://api.inviter.app/v1/calendar/subscribe/group-123/abc123-token-xyz",
  "webcalUrl": "webcal://api.inviter.app/v1/calendar/subscribe/group-123/abc123-token-xyz",
  "createdAt": "2025-10-17T10:30:00Z"
}
```

**Response (403 Forbidden):**
```json
{
  "error": "FORBIDDEN",
  "message": "You must be a member of this group to subscribe to its calendar"
}
```

**Database Operations:**
1. Read: `PK=GROUP#{groupId}, SK=USER#{userId}` (verify membership)
2. Write: Update membership record with token

**Implementation:**
```java
public CalendarSubscriptionResponse createSubscription(String groupId, String userId) {
    // 1. Get membership (verifies membership exists)
    GroupMembership membership = table.getItem(Key.builder()
        .partitionValue("GROUP#" + groupId)
        .sortValue("USER#" + userId)
        .build());

    if (membership == null) {
        throw new ForbiddenException("You must be a member of this group");
    }

    // 2. If already has token, return existing (idempotent)
    if (membership.getCalendarToken() != null) {
        return toResponse(membership);
    }

    // 3. Generate and set token
    membership.setCalendarToken(UUID.randomUUID().toString());

    // 4. Update membership record
    table.putItem(membership);

    return toResponse(membership);
}
```

---

### API 2: List User's Subscriptions

**Endpoint:** `GET /v1/calendar/subscriptions`

**Authentication:** JWT required

**Purpose:** Show user all their active calendar subscriptions

**Request:**
```http
GET /v1/calendar/subscriptions
Authorization: Bearer eyJhbGc...
```

**Response (200 OK):**
```json
{
  "subscriptions": [
    {
      "subscriptionId": "group-123",
      "groupId": "group-123",
      "groupName": "Seattle Hikers",
      "subscriptionUrl": "https://api.inviter.app/v1/calendar/subscribe/group-123/abc123-token-xyz",
      "webcalUrl": "webcal://api.inviter.app/v1/calendar/subscribe/group-123/abc123-token-xyz",
      "createdAt": "2025-10-13T10:30:00Z"
    },
    {
      "subscriptionId": "group-456",
      "groupId": "group-456",
      "groupName": "Mountain Bikers",
      "subscriptionUrl": "https://api.inviter.app/v1/calendar/subscribe/group-456/def456-token-uvw",
      "webcalUrl": "webcal://api.inviter.app/v1/calendar/subscribe/group-456/def456-token-uvw",
      "createdAt": "2025-10-12T15:20:00Z"
    }
  ]
}
```

**Database Operations:**
1. Query UserGroupIndex: `gsi1pk=USER#{userId}` (get all memberships)
2. Filter in-memory for memberships with `calendarToken != null`

**Implementation:**
```java
public List<CalendarSubscriptionResponse> getUserSubscriptions(String userId) {
    // Query all user's memberships
    QueryEnhancedRequest request = QueryEnhancedRequest.builder()
        .queryConditional(QueryConditional.keyEqualTo(Key.builder()
            .partitionValue("USER#" + userId)
            .build()))
        .indexName("UserGroupIndex")
        .build();

    return userGroupIndex.query(request, GroupMembership.class)
        .items()
        .stream()
        .filter(m -> m.getCalendarToken() != null)  // Only subscribed groups
        .map(this::toResponse)
        .collect(Collectors.toList());
}
```

**Note:** Reads all memberships and filters client-side. Acceptable because:
- Membership records are small (~1KB)
- This query is already used for "get my groups"
- Even 50 groups = 50KB (negligible)

---

### API 3: Delete Subscription

**Endpoint:** `DELETE /v1/calendar/subscriptions/{groupId}`

**Authentication:** JWT required

**Purpose:** Revoke calendar subscription (user unsubscribes from group calendar)

**Request:**
```http
DELETE /v1/calendar/subscriptions/group-123
Authorization: Bearer eyJhbGc...
```

**Response (204 No Content):** Empty body

**Response (404 Not Found):**
```json
{
  "error": "NOT_FOUND",
  "message": "Subscription not found"
}
```

**Database Operations:**
1. Read: `PK=GROUP#{groupId}, SK=USER#{userId}` (verify exists)
2. Write: Update membership record, clear token

**Implementation:**
```java
public void deleteSubscription(String groupId, String userId) {
    // 1. Get membership record
    GroupMembership membership = table.getItem(Key.builder()
        .partitionValue("GROUP#" + groupId)
        .sortValue("USER#" + userId)
        .build());

    if (membership == null || membership.getCalendarToken() == null) {
        throw new NotFoundException("Subscription not found");
    }

    // 2. Clear token (removes from CalendarTokenIndex)
    membership.setCalendarToken(null);

    // 3. Update membership
    table.putItem(membership);
}
```

---

### API 4: Get Calendar Feed (ICS)

**Endpoint:** `GET /v1/calendar/subscribe/{groupId}/{token}`

**Authentication:** None (token-based authorization)

**Purpose:** Return ICS calendar feed for consumption by calendar applications

**Request:**
```http
GET /v1/calendar/subscribe/group-123/abc123-token-xyz
User-Agent: iOS/16.0 CalendarAgent/1.0
If-None-Match: "group-123-1728892800000"
```

**Response (200 OK - Full Content):**
```http
200 OK
Content-Type: text/calendar; charset=utf-8
Cache-Control: public, max-age=7200, must-revalidate
ETag: "group-123-1728896400000"
Content-Length: 1523

BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//Inviter//HangOut Calendar//EN
CALSCALE:GREGORIAN
METHOD:PUBLISH
X-WR-CALNAME:Seattle Hikers
X-WR-TIMEZONE:America/Los_Angeles
X-WR-CALDESC:Hangouts for Seattle Hikers group

BEGIN:VEVENT
UID:hangout-abc123@inviter.app
DTSTAMP:20251017T103000Z
DTSTART:20251020T140000Z
DTEND:20251020T170000Z
SUMMARY:Mount Rainier Hike
DESCRIPTION:Join us for a challenging hike!\n\nRSVP: https://app.inviter.app/hangouts/hangout-abc123
LOCATION:Mount Rainier National Park
STATUS:CONFIRMED
SEQUENCE:0
END:VEVENT

END:VCALENDAR
```

**Response (304 Not Modified):**
```http
304 Not Modified
Cache-Control: public, max-age=7200, must-revalidate
ETag: "group-123-1728892800000"
```

**Response (401 Unauthorized):**
```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid subscription token"
}
```

**Response (403 Forbidden):**
```json
{
  "error": "FORBIDDEN",
  "message": "You are no longer a member of this group"
}
```

**Database Operations:**
1. Query CalendarTokenIndex: `gsi2pk=TOKEN#{token}` (find membership)
2. Read: `PK=GROUP#{groupId}, SK=METADATA` (get lastHangoutModified for ETag)
3. If ETag mismatch: Query EntityTimeIndex: `gsi1pk=GROUP#{groupId}, startTimestamp >= now()`

**Implementation:**
```java
@GetMapping(value = "/v1/calendar/subscribe/{groupId}/{token}",
            produces = "text/calendar; charset=utf-8")
public ResponseEntity<String> getCalendarFeed(
    @PathVariable String groupId,
    @PathVariable String token,
    @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

    // 1. Validate token and membership (single query!)
    GroupMembership membership = validateTokenAndMembership(token, groupId);

    // 2. Get group metadata for ETag
    Group group = table.getItem(Key.builder()
        .partitionValue("GROUP#" + groupId)
        .sortValue("METADATA")
        .build());

    Instant lastModified = group.getLastHangoutModified();
    String etag = String.format("\"%s-%d\"",
        groupId,
        lastModified != null ? lastModified.toEpochMilli() : 0);

    // 3. Return 304 if client has current version
    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
            .eTag(etag)
            .cacheControl(CacheControl
                .maxAge(2, TimeUnit.HOURS)
                .cachePublic()
                .mustRevalidate())
            .build();
    }

    // 4. Query future hangouts
    List<HangoutPointer> hangouts = queryFutureHangouts(groupId);

    // 5. Generate ICS content
    String icsContent = iCalendarService.generateICS(group, hangouts);

    // 6. Return with caching headers
    return ResponseEntity.ok()
        .eTag(etag)
        .cacheControl(CacheControl
            .maxAge(2, TimeUnit.HOURS)
            .cachePublic()
            .mustRevalidate())
        .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
        .body(icsContent);
}

private GroupMembership validateTokenAndMembership(String token, String groupId) {
    // Query CalendarTokenIndex
    QueryEnhancedRequest tokenQuery = QueryEnhancedRequest.builder()
        .queryConditional(QueryConditional.keyEqualTo(Key.builder()
            .partitionValue("TOKEN#" + token)
            .build()))
        .indexName("CalendarTokenIndex")
        .build();

    GroupMembership membership = calendarTokenIndex.query(tokenQuery)
        .items()
        .stream()
        .findFirst()
        .orElseThrow(() -> new UnauthorizedException("Invalid token"));

    // Verify group ID matches (prevent token reuse across groups)
    if (!membership.getGroupId().equals(groupId)) {
        throw new UnauthorizedException("Token does not match group");
    }

    return membership;
}

private List<HangoutPointer> queryFutureHangouts(String groupId) {
    QueryEnhancedRequest request = QueryEnhancedRequest.builder()
        .queryConditional(QueryConditional.sortGreaterThanOrEqualTo(
            Key.builder()
                .partitionValue("GROUP#" + groupId)
                .sortValue(AttributeValue.builder()
                    .n(String.valueOf(Instant.now().getEpochSecond()))
                    .build())
                .build()))
        .indexName("EntityTimeIndex")
        .build();

    return entityTimeIndex.query(request, HangoutPointer.class)
        .items()
        .stream()
        .collect(Collectors.toList());
}
```

---

## ICS Calendar Generation

Use Biweekly library for ICS format generation:

**Dependency:**
```gradle
implementation 'net.sf.biweekly:biweekly:0.6.8'
```

**Service Implementation:**
```java
@Service
public class ICalendarService {

    public String generateICS(Group group, List<HangoutPointer> hangouts) {
        ICalendar ical = new ICalendar();

        // Calendar metadata
        ical.setProductId("-//Inviter//HangOut Calendar//EN");
        ical.setName(group.getGroupName());
        ical.setMethod(Method.PUBLISH);

        // Add calendar properties
        ical.addExperimentalProperty("X-WR-CALNAME", group.getGroupName());
        ical.addExperimentalProperty("X-WR-TIMEZONE", "America/Los_Angeles");
        ical.addExperimentalProperty("X-WR-CALDESC",
            "Hangouts for " + group.getGroupName() + " group");

        // Add events
        for (HangoutPointer hangout : hangouts) {
            VEvent event = new VEvent();

            // Required fields
            event.setUid(new Uid(hangout.getHangoutId() + "@inviter.app"));
            event.setDateTimeStamp(Date.from(Instant.now()));
            event.setSummary(hangout.getTitle());

            // Time
            event.setDateStart(Date.from(Instant.ofEpochSecond(hangout.getStartTimestamp())));
            if (hangout.getEndTimestamp() != null) {
                event.setDateEnd(Date.from(Instant.ofEpochSecond(hangout.getEndTimestamp())));
            }

            // Description with link
            String description = hangout.getDescription();
            if (description == null) {
                description = "";
            }
            description += "\n\nRSVP: https://app.inviter.app/hangouts/" + hangout.getHangoutId();
            event.setDescription(description);

            // Location
            if (hangout.getLocation() != null && hangout.getLocation().getName() != null) {
                event.setLocation(hangout.getLocation().getName());
            }

            // Status
            event.setStatus(Status.confirmed());
            event.setSequence(0);

            ical.addEvent(event);
        }

        return Biweekly.write(ical).go();
    }
}
```

---

## Caching Strategy

### Client-Side Caching (HTTP Headers)

**Cache-Control:** `public, max-age=7200, must-revalidate`
- `public`: CloudFront can cache (safe because token authorizes)
- `max-age=7200`: Cache for 2 hours (balances freshness vs load)
- `must-revalidate`: Check with origin after expiry

**Effect:**
- iOS Calendar won't request feed more than once per 2 hours
- Reduces load by 66% (from 3 requests/hour to 1 request/2 hours)

### Server-Side Caching (ETag)

**ETag Format:** `"{groupId}-{lastHangoutModified.toEpochMilli()}"`

**Flow:**
1. Client sends `If-None-Match: "group-123-1728892800000"`
2. Server queries `lastHangoutModified` from Group record (1 read)
3. If unchanged, return `304 Not Modified` (skip hangout query)
4. If changed, generate fresh ICS and return `200 OK`

**Cache Hit Rate:**
- Assuming 1 hangout created/week per group
- 99.8% of requests return 304 (no ICS generation needed)

**Benefits:**
- Reduces DynamoDB reads by 50% (skip hangout query on cache hit)
- Reduces CPU by 99% (skip ICS generation on cache hit)
- Reduces bandwidth by 99% (304 response is tiny)

---

## Load Analysis at 50K Groups

### Assumptions
- 50,000 active groups
- 20 members per group average
- 15% subscription rate (3 members per group subscribe)
- iOS Calendar polls every 20 minutes (Apple controlled)

### Request Volume
```
50,000 groups × 3 subscribed members = 150,000 active subscriptions
150,000 subscriptions × 3 requests/hour = 450,000 requests/hour
450,000 requests/hour ÷ 3600 = 125 requests/second average
```

### With 2-Hour Cache Window
```
150,000 subscriptions × 0.5 requests/hour = 75,000 requests/hour
75,000 requests/hour ÷ 3600 = 21 requests/second average
```

### DynamoDB Read Units (with 99.8% ETag hit rate)

**Per Request:**
- Cache hit (304): 2 reads (token lookup + group metadata)
- Cache miss (200): 3 reads (token lookup + group metadata + hangouts query)

**Monthly Totals:**
```
Requests per month: 75,000 req/hr × 730 hrs = 54.75M requests

Cache hits (99.8%): 54.75M × 0.998 = 54.64M requests
  Cost: 54.64M × 2 reads × $0.00000125 = $136.60/month

Cache misses (0.2%): 54.75M × 0.002 = 109,500 requests
  Cost: 109,500 × 3 reads × $0.00000125 = $0.41/month

Total DynamoDB: $137/month for 50,000 groups
```

### Cost Breakdown at 50K Groups
- **DynamoDB reads:** $137/month
- **Lambda/EC2 processing:** ~$20/month (mostly 304 responses)
- **Data transfer:** ~$5/month (ICS files are small)
- **Total:** ~$162/month for 150,000 active subscriptions

**Per-group cost:** $0.003/month (less than a penny per group)

---

## CloudFront Preparation

The API is designed to add CloudFront later with zero code changes:

### Current Implementation (CloudFront-Ready)
- ✅ Token in URL path (not in headers)
- ✅ `Cache-Control: public` (allows CDN caching)
- ✅ ETag support (CloudFront respects ETags)
- ✅ Stable URL structure (`/v1/calendar/subscribe/{groupId}/{token}`)
- ✅ No Vary headers (doesn't fragment cache)
- ✅ No query parameters (predictable cache keys)

### Future CloudFront Addition (Configuration Only)
1. Create CloudFront distribution pointing to API origin
2. Configure cache behavior for `/v1/calendar/*` path
3. Set TTL to match `max-age` header (2 hours)
4. Forward `If-None-Match` header to origin

**Expected Impact:**
- 95% cache hit rate at edge locations
- Origin load reduction: 20x (from 21 req/sec to ~1 req/sec)
- Latency improvement: 50-200ms faster worldwide
- Cost: +$6/month CloudFront fees, -$130/month DynamoDB savings = net -$124/month

**When to Add:** When origin load exceeds 50 requests/second sustained.

---

## Implementation Steps

### Phase 1: Data Model Changes
1. Add `calendarToken` field to `GroupMembership` entity
2. Add `gsi2pk` getter with `@DynamoDbSecondaryPartitionKey` annotation
3. Update `setCalendarToken()` to populate `gsi2pk` automatically
4. Add `lastHangoutModified` field to `Group` entity
5. Add Biweekly dependency to `build.gradle`

### Phase 2: Infrastructure
1. Create `CalendarTokenIndex` GSI via DynamoDB console or CloudFormation
   - Partition key: `gsi2pk`
   - Projection: ALL
   - Billing: On-demand
2. Update Group records when hangouts change:
   - Modify `HangoutService.createHangout()` to update `lastHangoutModified`
   - Modify `HangoutService.updateHangout()` to update `lastHangoutModified`
   - Modify `HangoutService.deleteHangout()` to update `lastHangoutModified`

### Phase 3: Backend Services
1. Create `ICalendarService` for ICS generation
2. Create `CalendarSubscriptionService` for subscription management
3. Create `CalendarFeedController` for ICS endpoint
4. Add DTOs: `CalendarSubscriptionResponse`

### Phase 4: API Endpoints
1. Implement `POST /v1/calendar/subscriptions/{groupId}`
2. Implement `GET /v1/calendar/subscriptions`
3. Implement `DELETE /v1/calendar/subscriptions/{groupId}`
4. Implement `GET /v1/calendar/subscribe/{groupId}/{token}`

### Phase 5: Testing
1. Unit tests for `ICalendarService.generateICS()`
2. Unit tests for `CalendarSubscriptionService` methods
3. Integration tests for subscription lifecycle
4. Manual testing with iOS Calendar app
5. Load testing calendar feed endpoint

### Phase 6: Documentation
1. Update API documentation (Swagger annotations)
2. Create user guide for subscribing to calendars
3. Update iOS app to expose subscription feature
4. Update web app to expose subscription feature

---

## Security Considerations

### Token Security
- **Token format:** UUID v4 (128-bit random, cryptographically secure)
- **Token storage:** Stored as plain text (acts as bearer token, not secret)
- **Token scope:** Single group only (validated on every request)
- **Token rotation:** Not implemented (can be added later if needed)

### Authorization
- **Membership verification:** Every feed request validates user still in group
- **Group ID validation:** Token must match group in URL (prevent cross-group reuse)
- **Automatic revocation:** Token deleted when user leaves group

### Rate Limiting
Consider adding per-token rate limiting if abuse is detected:
```java
@RateLimiter(name = "calendar-subscription",
             limitForPeriod = 10,
             limitRefreshPeriod = Duration.ofMinutes(1))
public ResponseEntity<String> getCalendarFeed(...) { ... }
```

**Limits:** 10 requests/minute per token (iOS Calendar typically does 3/hour)

---

## Future Enhancements

### Possible Additions (Not in Scope)
1. **Webhook updates:** Push to calendar instead of polling (requires CalDAV server)
2. **Custom refresh intervals:** Allow users to set polling frequency
3. **Filtered feeds:** Subscribe to specific types of hangouts only
4. **Multiple tokens:** Allow user to have different tokens per group (revocation granularity)
5. **Analytics:** Track which subscriptions are actively used
6. **Expiring tokens:** Auto-expire tokens after 1 year for security

---

## Success Metrics

### Technical Metrics
- **Availability:** 99.9% uptime for calendar feed endpoint
- **Latency:** p95 < 200ms for feed generation
- **Cache hit rate:** > 95% ETag hits
- **Error rate:** < 0.1% 4xx/5xx responses

### Business Metrics
- **Adoption:** Track % of group members who subscribe
- **Engagement:** Track hangout attendance from subscribed vs non-subscribed users
- **Retention:** Track if subscribed users remain in groups longer

### Cost Metrics
- **Target:** < $0.01 per active subscription per month
- **Alert threshold:** > $0.05 per active subscription (investigate optimization)
