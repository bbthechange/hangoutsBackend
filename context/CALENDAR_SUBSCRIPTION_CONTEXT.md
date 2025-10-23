# Calendar Subscription Feature

## Overview

Calendar subscription allows users to subscribe to a group's hangouts in their calendar app (iOS Calendar, Google Calendar, etc.). The subscription automatically updates when hangouts are added/modified/deleted, without requiring the app to be open.

**Key Design Principle**: Use HTTP caching and CloudFront CDN to minimize DynamoDB costs and handle 50,000+ groups efficiently.

## Architecture

### Token Storage Strategy
Calendar subscription tokens are stored **directly on GroupMembership records** (not a separate entity). This provides:
- **Automatic cleanup**: Token deleted when user leaves group
- **Simpler data model**: One entity instead of three (GroupMembership, Subscription, SubscriptionToken)
- **Better semantics**: Subscription tied to membership, not user-group relationship
- **Cost savings**: Fewer DynamoDB operations

### Caching Strategy (Critical for Cost/Performance)
Two-layer HTTP caching to reduce DynamoDB queries:

1. **Client-side Cache (Cache-Control)**
   - `max-age=7200` (2 hours)
   - `public` (allows CloudFront caching)
   - `must-revalidate` (check ETag after expiry)
   - Expected: Reduces poll frequency from 3/hour to 0.5/hour

2. **ETag Validation**
   - ETag format: `"{groupId}-{lastHangoutModifiedTimestamp}"`
   - Returns 304 Not Modified if ETag matches
   - Expected: 99.8% of requests return 304 (no DynamoDB query)

**Load Characteristics**:
- Without caching: ~2,160 requests/day per group (1 request/min)
- With caching: ~12 requests/day per group (1 request/2 hours)
- 304 responses: ~11 requests/day (no DynamoDB query needed)
- Full feed generation: ~1 request/day

## API Endpoints

### 1. Create Subscription
```
POST /calendar/subscriptions/{groupId}
Authorization: Bearer {JWT}
```

**Purpose**: Generate calendar subscription for a group
**Returns**: Subscription URLs (https:// and webcal://)
**Idempotent**: Returns existing subscription if already subscribed
**Authorization**: User must be group member

### 2. List Subscriptions
```
GET /calendar/subscriptions
Authorization: Bearer {JWT}
```

**Purpose**: List all active subscriptions for authenticated user
**Returns**: Array of subscriptions with URLs
**Authorization**: JWT required

### 3. Delete Subscription
```
DELETE /calendar/subscriptions/{groupId}
Authorization: Bearer {JWT}
```

**Purpose**: Remove calendar subscription
**Effect**: Clears `calendarToken` from GroupMembership
**Authorization**: User must own the subscription

### 4. Get Calendar Feed (PUBLIC)
```
GET /calendar/feed/{groupId}/{token}
If-None-Match: "{etag}" (optional)
```

**Purpose**: Serve ICS/iCalendar feed for calendar apps
**Authorization**: Token-based (no JWT) - public endpoint
**Response**: ICS content with caching headers OR 304 Not Modified
**Security**: Token validation via CalendarTokenIndex GSI

**IMPORTANT**: This endpoint MUST be public (no JWT) for calendar apps to poll it.

## Data Model Changes

### BaseItem (Base class)
```java
private String gsi2pk;  // GSI2 Partition Key (for CalendarTokenIndex)
private String gsi2sk;  // GSI2 Sort Key (reserved for future use)
```

### GroupMembership
```java
private String calendarToken;  // Calendar subscription token (null if not subscribed)

public void setCalendarToken(String calendarToken) {
    this.calendarToken = calendarToken;

    // Automatically manage GSI2PK for CalendarTokenIndex
    if (calendarToken != null) {
        setGsi2pk("TOKEN#" + calendarToken);
    } else {
        setGsi2pk(null);
    }

    touch(); // Update timestamp
}

@Override
@DynamoDbSecondaryPartitionKey(indexNames = "CalendarTokenIndex")
public String getGsi2pk() {
    return super.getGsi2pk();
}
```

**CRITICAL**: The setter automatically manages `gsi2pk` for GSI indexing. Never set `gsi2pk` manually for tokens.

### Group
```java
private Instant lastHangoutModified;  // Timestamp for ETag calculation

@DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
public Instant getLastHangoutModified() {
    return lastHangoutModified;
}
```

**CRITICAL**: This field MUST be updated whenever ANY hangout in the group is created/updated/deleted. See `HangoutServiceImpl.updateGroupLastModified()`.

## DynamoDB GSI: CalendarTokenIndex

**Required for token-based authentication to work efficiently.**

### GSI Configuration
- **Name**: `CalendarTokenIndex`
- **Partition Key**: `gsi2pk` (String)
- **Sort Key**: `gsi2sk` (String)
- **Projection**: ALL
- **Capacity**: On-demand or 1 RCU/1 WCU

### Key Pattern
- **gsi2pk**: `TOKEN#{uuid}` (e.g., `TOKEN#7f3a7bb7-c91e-4df7-93c5-c0c82c41eaa4`)
- **gsi2sk**: Not used (null) - reserved for future use

### Query Example
```java
groupRepository.findMembershipByToken(token)
// Translates to: Query CalendarTokenIndex where gsi2pk = "TOKEN#{token}"
```

### Setup Instructions
See `docs/GSI_CALENDAR_TOKEN_INDEX.md` for AWS Console, CLI, and CloudFormation setup.

## Service Layer Architecture

### CalendarSubscriptionService
Business logic layer for all calendar operations.

**Key Methods**:
- `createSubscription(groupId, userId)` - Create/retrieve subscription
- `getUserSubscriptions(userId)` - List user's subscriptions
- `deleteSubscription(groupId, userId)` - Remove subscription
- `getCalendarFeed(groupId, token, ifNoneMatch)` - Generate ICS feed with caching

**Implementation**: `CalendarSubscriptionServiceImpl`

### ICalendarService
Generates RFC 5545 compliant ICS/iCalendar feeds.

**Library**: Biweekly (`net.sf.biweekly:biweekly:0.6.8`)

**Key Method**:
- `generateICS(Group group, List<HangoutPointer> hangouts)` - Returns ICS string

**Event Fields**:
- UID: `{hangoutId}@inviter.app`
- Summary: Hangout title
- Description: Hangout description + participant count
- Location: Hangout location name
- Start/End: Timestamps from hangout
- Status: CONFIRMED

### Future Hangout Query
Uses **EntityTimeIndex GSI** to efficiently query future hangouts:

```java
hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, 100, null)
```

**Pagination**: Fetches up to 500 events total (cap for safety and calendar app limits).

## Configuration

### Application Properties

**Development** (`application.properties`):
```properties
calendar.base-url=http://localhost:8080
```

**Production** (`application-prod.properties`):
```properties
calendar.base-url=https://d3lm7si4v7xvcj.cloudfront.net
```

**Staging** (`application-staging.properties`):
```properties
calendar.base-url=https://d3e93y6prxzuq0.cloudfront.net
```

**IMPORTANT**: Use CloudFront URLs in production/staging for caching to work. Direct API Gateway URLs bypass caching.

## Security Configuration

### Public Endpoint Requirement
The calendar feed endpoint (`/calendar/feed/**`) MUST be public for calendar apps to access it.

**SecurityConfig.java**:
```java
.requestMatchers("/calendar/feed/**").permitAll()
```

**Why**: Calendar apps don't support JWT authentication - they poll using the webcal URL with embedded token.

### Token Security
- Tokens are UUIDs (128-bit random, cryptographically secure)
- Single-use per group membership (one subscription per user per group)
- Automatically invalidated when user leaves group
- Validated against groupId to prevent token reuse across groups

## Key Implementation Details

### ETag Calculation
```java
String etag = String.format("\"%s-%d\"",
    groupId,
    lastModified != null ? lastModified.toEpochMilli() : 0);
```

**When to update `lastHangoutModified`**:
- Hangout created in group
- Hangout updated in group
- Hangout deleted from group
- Hangout associated with group (via pointer)
- Hangout disassociated from group

**See**: `HangoutServiceImpl.updateGroupLastModified()` for implementation pattern.

### URL Generation
Subscription URLs are generated dynamically based on `calendar.base-url` configuration:

```java
String subscriptionUrl = String.format("%s/calendar/feed/%s/%s",
    baseUrl, groupId, calendarToken);

String webcalUrl = subscriptionUrl
    .replace("https://", "webcal://")
    .replace("http://", "webcal://");
```

**webcal://**: iOS Calendar and other apps use this protocol to subscribe to calendars.

## Testing

### Local Testing with curl
```bash
# 1. Get JWT token
TOKEN=$(curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+19285251044", "password": "mypass2"}' | jq -r '.accessToken')

# 2. Create subscription
curl -X POST http://localhost:8080/calendar/subscriptions/{groupId} \
  -H "Authorization: Bearer $TOKEN" | jq

# 3. Get feed URL from response and test it
curl "http://localhost:8080/calendar/feed/{groupId}/{token}"

# 4. Test ETag caching (should return 304)
curl -v "http://localhost:8080/calendar/feed/{groupId}/{token}" \
  -H 'If-None-Match: "{etag-from-first-response}"' | grep "304"
```

### Testing with Real Calendar Apps
1. Deploy to staging/production (localhost won't work)
2. Create subscription and copy `webcalUrl`
3. iOS: Settings → Calendar → Accounts → Add Account → Other → Add Subscribed Calendar
4. Paste webcal URL
5. Hangouts appear automatically in calendar

**Note**: Calendar apps poll every 15-60 minutes. Changes may not appear immediately.

## Important Constraints

### Event Limit
Calendar feeds are capped at **500 events** for safety and calendar app compatibility.

**Rationale**:
- Most calendar apps have limits on feed size
- Very large feeds can cause timeouts
- Groups with >500 future hangouts are extremely rare

### CloudFront Caching Requirements
For caching to work properly:
- Use CloudFront URL (not direct API Gateway URL)
- ETag must be stable (based on `lastHangoutModified`, not current time)
- Cache-Control header must include `public`
- No `Vary` headers (breaks CloudFront caching)
- Token in URL path (not header or query param)

## Common Issues

### Feed Not Updating
**Symptom**: New hangouts don't appear in calendar
**Cause**: `lastHangoutModified` not updated on Group
**Fix**: Ensure all hangout operations call `updateGroupLastModified()`

### 401 Unauthorized on Feed
**Symptom**: Feed endpoint returns authentication error
**Cause**: SecurityConfig not allowing public access
**Fix**: Verify `/calendar/feed/**` in `.permitAll()` list

### ETag Never Matches (No 304s)
**Symptom**: Every request returns full feed
**Cause**: ETag calculation changing on every request
**Fix**: ETag must only change when hangouts change (use `lastHangoutModified`, not `Instant.now()`)

### Token Not Found
**Symptom**: "Invalid subscription token" error
**Cause**: CalendarTokenIndex GSI not created or token not indexed
**Fix**: Verify GSI exists and `gsi2pk` is set when `calendarToken` is set

## Future Enhancements

### Potential Improvements
- **RSVP Links**: Add web frontend URL to event descriptions (TODO in `ICalendarServiceImpl.buildDescription()`)
- **Past Events**: Currently only shows future events - could optionally include recent past events
- **Custom Timezone**: Currently hardcoded to `America/Los_Angeles` in ICS metadata
- **Event Updates**: Track sequence numbers for better calendar app update handling
- **Subscription Metrics**: Track subscription usage for analytics

### Scaling Considerations
- Current design handles 50,000+ groups efficiently
- Cost ~$13.55/month for 150K subscriptions (GSI + DynamoDB)
- 99.8% cache hit rate expected reduces DynamoDB costs significantly
- If scale increases 10x, consider CloudFront invalidation API for instant updates
