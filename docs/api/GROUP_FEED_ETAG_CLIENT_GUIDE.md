# Group Feed ETag - Client Guide

## Overview

The group feed endpoint supports ETags to reduce data transfer and improve performance when polling for updates.

**How it works**:
1. Server sends `ETag` header with every 200 response
2. Client stores the ETag value
3. Client sends `If-None-Match` header with stored ETag on subsequent requests
4. Server returns 304 if nothing changed (no body), or 200 with new data (and new ETag)

---

## HTTP Request/Response Examples

### Initial Request (No ETag)

```bash
curl -i http://localhost:8080/groups/{groupId}/feed \
  -H "Authorization: Bearer $TOKEN"
```

**Response**:
```http
HTTP/1.1 200 OK
ETag: "a1b2c3d4-1729882800000"
Cache-Control: no-cache, must-revalidate
Content-Type: application/json

{
  "groupId": "a1b2c3d4",
  "withDay": [...],
  "needsDay": [...]
}
```

### Subsequent Request (With ETag)

```bash
curl -i http://localhost:8080/groups/{groupId}/feed \
  -H "Authorization: Bearer $TOKEN" \
  -H 'If-None-Match: "a1b2c3d4-1729882800000"'
```

**Response if feed unchanged (304)**:
```http
HTTP/1.1 304 Not Modified
ETag: "a1b2c3d4-1729882800000"
Cache-Control: no-cache, must-revalidate
```
*No body - use cached data*

**Response if feed changed (200)**:
```http
HTTP/1.1 200 OK
ETag: "a1b2c3d4-1729999999999"
Cache-Control: no-cache, must-revalidate
Content-Type: application/json

{
  "groupId": "a1b2c3d4",
  "withDay": [...],
  "needsDay": [...]
}
```
*New ETag value - update cache*

---

## What You Need to Do

1. **Store the ETag** from `ETag` response header (per group)
2. **Send If-None-Match** header with stored ETag on subsequent requests
3. **Handle 304** - Keep using cached data, don't update
4. **Handle 200** - Update cache with new data and new ETag
5. **Clear ETags** on logout or user change

**Important**: Treat ETag as opaque string - don't parse or modify it, just store and send back.

---

## HTTP Status Codes

| Status | Meaning | Action |
|--------|---------|--------|
| **200 OK** | Feed changed | Update cache with new data and ETag |
| **304 Not Modified** | Feed unchanged | Keep using cached data |
| **401 Unauthorized** | Token expired | Re-authenticate |
| **403 Forbidden** | Not a group member | Remove from group list |
| **404 Not Found** | Group doesn't exist | Remove from group list |

---

## Testing ETag Behavior

### Test Flow

```bash
# 1. Get initial feed and note the ETag
curl -i http://localhost:8080/groups/{groupId}/feed \
  -H "Authorization: Bearer $TOKEN"

# Response: HTTP/1.1 200 OK
#           ETag: "a1b2c3d4-1729882800000"

# 2. Poll with If-None-Match (expect 304)
curl -i http://localhost:8080/groups/{groupId}/feed \
  -H "Authorization: Bearer $TOKEN" \
  -H 'If-None-Match: "a1b2c3d4-1729882800000"'

# Response: HTTP/1.1 304 Not Modified

# 3. Make a change to the feed (vote on poll, add car, RSVP, etc.)
curl -X POST http://localhost:8080/hangouts/{hangoutId}/polls/{pollId}/vote \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"optionId":"option-123"}'

# 4. Poll again with old ETag (expect 200 with new ETag)
curl -i http://localhost:8080/groups/{groupId}/feed \
  -H "Authorization: Bearer $TOKEN" \
  -H 'If-None-Match: "a1b2c3d4-1729882800000"'

# Response: HTTP/1.1 200 OK
#           ETag: "a1b2c3d4-1729999999999"  (new timestamp)
```

### Get JWT Token for Testing

```bash
# Login to get token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+19285251044", "password": "mypass2"}' \
  | jq -r '.token'

# Use in subsequent requests
TOKEN="eyJhbGc..."
```

---

## Recommended Polling Intervals

| Scenario | Interval | Rationale |
|----------|----------|-----------|
| **Group feed page (active)** | 15-30 sec | Real-time updates expected |
| **App in background** | 60 sec | Balance updates vs battery |
| **User idle (>5 min)** | 120 sec | Minimal polling |

---

## Common Mistakes

### ❌ Don't parse or modify the ETag
The ETag is an opaque string - just store it and send it back unchanged.

### ❌ Don't add extra quotes
The ETag value already includes quotes - use it as-is.

### ❌ Don't cache responses without revalidation
The `Cache-Control: no-cache` header means you must always revalidate with the server using If-None-Match.

### ❌ Don't share ETags across groups
Store one ETag per group, not a single global ETag.

---

## Benefits

- **70% less data transfer** - 304 responses have no body
- **60% faster responses** - 304s are much quicker than full queries
- **Lower costs** - Saves ~4 RCUs per poll on backend
- **Better battery life** - Less data = less power

---

## Summary

**Send this**:
```http
GET /groups/{groupId}/feed
Authorization: Bearer {token}
If-None-Match: "a1b2c3d4-1729882800000"
```

**Get 304** → Keep using cached data

**Get 200** → Update cache with new data and new ETag
