# Group Feed Error Reference — Client Integration Guide

**Audience:** iOS / Android client developers. This document covers the exact failure modes for
`GET /groups/{groupId}/feed` and `GET /groups/{groupId}` so clients can decide when to fall back
to the group list silently (e.g., cold-launch group restore).

---

## TL;DR for cold-launch fallback

Treat **any non-200/304** response from `/groups/{groupId}/feed` as "this group is no longer
accessible — fall back to group list silently." The feed endpoint returns 403 (`FORBIDDEN`) for
"user was removed," "user was never a member," and "group was deleted" — there is no way to
distinguish those three from the feed endpoint alone (see §3). The single group endpoint
(`GET /groups/{groupId}`) does distinguish deleted vs. not-a-member.

---

## 1. Status Codes by Scenario

### `GET /groups/{groupId}/feed`

| Scenario | HTTP Status | Notes |
|---|---|---|
| User was removed from the group | **403** | `FORBIDDEN` — see §3 |
| User was never a member | **403** | `FORBIDDEN` — same path |
| Group was deleted | **403** | Membership deleted with group; same path |
| groupId malformed (not UUID format) | **400** | Path variable constraint violation |
| Valid JWT, token expired or tampered | **401** | Handled by Spring Security before controller |

### `GET /groups/{groupId}` (single group fetch)

| Scenario | HTTP Status | Notes |
|---|---|---|
| Group was deleted / not found | **404** | Group check runs first |
| User not a member (removed or never was) | **403** | Group found but no membership record |
| groupId malformed | **400** | Path variable constraint violation |
| Valid JWT, token expired or tampered | **401** | |

---

## 2. Response Body Shape

There are two different response shapes depending on where the error is caught.

### Most errors (from `BaseController`)

```json
{
  "error": "NOT_FOUND",
  "message": "Group not found: <groupId>",
  "timestamp": 1714521600000
}
```

- `error` is a stable, machine-readable string code (list below).
- `message` is human-readable and relatively stable but treat as opaque.
- `timestamp` is epoch millis.

**Known `error` codes for group/feed endpoints:**

| `error` value | HTTP | Scenario |
|---|---|---|
| `NOT_FOUND` | 404 | Group doesn't exist (from `GET /groups/{groupId}`) |
| `FORBIDDEN` | 403 | User not a member (from `GET /groups/{groupId}/feed`) |
| `UNAUTHORIZED` | 403 | User not a member (from `GET /groups/{groupId}`) |
| `VALIDATION_ERROR` | 400 | Malformed path variable |
| `INTERNAL_ERROR` | 500 | Catch-all (unexpected server errors) |

### JWT / auth errors (from `JwtAuthenticationEntryPoint`)

```json
{
  "error": "Token expired or invalid",
  "code": "TOKEN_EXPIRED"
}
```

or, if the `Authorization` header is absent entirely:

```json
{
  "error": "Authentication required",
  "code": "AUTHENTICATION_REQUIRED"
}
```

These responses do **not** include `timestamp`. The shape is distinct from controller-level errors.

---

## 3. Distinguishability of Access-Denied Scenarios

`GroupController.getGroupFeed()` calls `groupService.getGroupForEtagCheck(groupId, userId)` first
(the cheap ETag path). That method throws `ForbiddenException` when the user is not in the group.
`BaseController` handles `ForbiddenException` and returns HTTP 403 with
`{"error": "FORBIDDEN", "message": "..."}`.

The feed endpoint collapses three scenarios into the same 403 response:
"user was removed," "user was never a member," and "group was deleted" (group deletion cascades
to membership records, so the membership check fails identically).

**`GET /groups/{groupId}` distinguishes deleted vs. not-a-member:**
- Group deleted → 404 (`NOT_FOUND`) — group record check runs first
- User removed → 403 (`UNAUTHORIZED`) — group exists, no membership record

### Recommended approach for cold-launch restore

Treat **any non-200/304** from `GET /groups/{groupId}/feed` as "cached group is inaccessible,
fall back to group list." If you want to show a reason to the user (e.g., "You've been removed
from Surf Club"), you can make a secondary `GET /groups/{groupId}` call after the feed fails,
read the 403 vs 404, and surface the message. But this is optional UX polish, not required for
the fallback to work.

---

## 4. ETag / Caching Headers on the Feed Endpoint

Yes, `GET /groups/{groupId}/feed` returns ETag and Cache-Control headers on every 200 response.

```
ETag: "a1b2c3d4-...-1714521600000-20456"
Cache-Control: no-cache, must-revalidate
```

**ETag format:** `"{groupId}-{lastHangoutModifiedMillis}-{timeBucket}"`
- `lastHangoutModifiedMillis`: epoch ms of the last data write to this group's hangouts
- `timeBucket`: `floor(nowSeconds / bucketSizeSeconds)` — rolls on a configurable cadence
  (default 86400s / 24h) so time-driven feed mutations (momentum state transitions, stale float
  surfacing) become visible even without a data write

**How to use on cold launch:**
- Read the ETag from your last successful feed response and cache it locally.
- On next launch, send `If-None-Match: <cachedETag>` with the feed request.
- 304 means feed is unchanged — reuse cached data, no body to parse.
- 200 means feed changed — parse the new body and update your cache and ETag.
- If you receive a non-200/304 (see §1), discard the cached ETag and fall back to group list.

**Note:** ETag is only returned on 200 responses, not on 304 or error responses. The membership
check runs before ETag calculation, so a 403 from the access-denied path will not include an
ETag.

---

## 5. Other Edge Cases

### No soft-delete or archiving

Groups are hard-deleted. There is no "archived" or "soft-deleted" state. A group either exists
(and the membership record is present) or it doesn't. There is no scenario where you get a 200
with degraded data due to deletion state.

### Suspended users

There is no user suspension mechanism at the API layer. Account status (`accountStatus` field on
the User model) exists in the data model but the group feed endpoint does not check it — a user
with a non-`ACTIVE` status can still fetch the group feed as long as their JWT is valid and they
have a membership record.

### Last member leaves

When the last member of a group calls `POST /groups/{groupId}/leave`, the group is automatically
deleted (not just the membership). From that point on, any cold-launch attempt against that
groupId will hit the 403/404 paths described above.

### 304 is not an error

A 304 Not Modified is a success response for the cold-launch purpose. The cached group data is
still valid. Only treat non-200/304 as a reason to fall back.

### Feed vs. members endpoint on access denial

Both `GET /groups/{groupId}/feed` and `GET /groups/{groupId}/members` go through the same
`isUserInGroup()` membership check via different code paths and now both return 403 on access
denial (feed returns `FORBIDDEN`, direct group endpoints return `UNAUTHORIZED`).
