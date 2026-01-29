# TV Watch Party API Reference

**For Frontend Developers**

This document describes all endpoints needed to implement TV Watch Party functionality in client applications.

## Overview

TV Watch Party allows users to schedule a series of hangouts for a TV season. The system automatically creates hangouts for announced episodes and can combine consecutive episodes airing within 20 hours into "Double Episode" or "Triple Episode" events.

**Minimum App Version:** 2.0.0 (older versions won't see watch parties in feeds)

---

## Authentication

All endpoints require JWT authentication via the `Authorization` header:

```
Authorization: Bearer <jwt_token>
```

---

## Endpoints

### 1. Create Watch Party

Creates a new watch party series for a group.

**Endpoint:** `POST /groups/{groupId}/watch-parties`

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `groupId` | string | Yes | UUID of the group |

**Request Body:**

```json
{
  "showId": 1526,
  "seasonNumber": 18,
  "showName": "RuPaul's Drag Race",
  "defaultTime": "20:00",
  "timezone": "America/Los_Angeles",
  "tvmazeSeasonId": 176432,
  "dayOverride": 4,
  "defaultHostId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `showId` | integer | Yes | TVMaze show ID |
| `seasonNumber` | integer | Yes | Season number (1, 2, 3, etc.) |
| `showName` | string | Yes | Show name for display |
| `defaultTime` | string | Yes | Time in HH:mm format (e.g., "20:00") |
| `timezone` | string | Yes | IANA timezone (e.g., "America/New_York") |
| `tvmazeSeasonId` | integer | No* | TVMaze season ID for automatic episode fetch |
| `episodes` | array | No* | Manual episode list (see below) |
| `dayOverride` | integer | No | Day of week override: 0=Sunday, 6=Saturday |
| `defaultHostId` | string | No | UUID of default host for all episodes |

*Either `tvmazeSeasonId` or `episodes` must be provided.

**Episode Object (when providing manually):**

```json
{
  "episodeId": 12345,
  "episodeNumber": 1,
  "title": "Pilot",
  "airTimestamp": 1704067200,
  "runtime": 60
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `episodeId` | integer | Yes | TVMaze episode ID |
| `episodeNumber` | integer | No | Episode number (1-based) |
| `title` | string | Yes | Episode title (may be "TBA") |
| `airTimestamp` | long | Yes | Air time as Unix timestamp (seconds) |
| `runtime` | integer | Yes | Runtime in minutes |

**Success Response:** `201 Created`

```json
{
  "seriesId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "seriesTitle": "RuPaul's Drag Race Season 18",
  "hangouts": [
    {
      "hangoutId": "h1234567-89ab-cdef-0123-456789abcdef",
      "title": "Episode 1: The Grand Premiere",
      "startTimestamp": 1704088800,
      "endTimestamp": 1704092400,
      "externalId": "12345",
      "combinedExternalIds": ["12345"]
    },
    {
      "hangoutId": "h2345678-9abc-def0-1234-56789abcdef0",
      "title": "Double Episode: Part 1, Part 2",
      "startTimestamp": 1704693600,
      "endTimestamp": 1704700800,
      "externalId": "12346",
      "combinedExternalIds": ["12346", "12347"]
    }
  ]
}
```

**Error Responses:**

| Status | Error Code | Cause |
|--------|------------|-------|
| `400` | `VALIDATION_ERROR` | Missing required fields, invalid time format, invalid timezone |
| `400` | `TVMAZE_NO_EPISODES` | No episodes with valid air dates found |
| `403` | `UNAUTHORIZED` | User is not a member of the group |
| `404` | `TVMAZE_SEASON_NOT_FOUND` | TVMaze season ID not found |
| `503` | `TVMAZE_SERVICE_UNAVAILABLE` | TVMaze API unavailable |

**Example Error Response:**

```json
{
  "error": "VALIDATION_ERROR",
  "message": "timezone: Invalid timezone",
  "timestamp": 1704067200000
}
```

---

### 2. Get Watch Party Details

Retrieves complete details of a watch party including all hangouts and interest levels.

**Endpoint:** `GET /groups/{groupId}/watch-parties/{seriesId}`

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `groupId` | string | Yes | UUID of the group |
| `seriesId` | string | Yes | UUID of the watch party series |

**Success Response:** `200 OK`

```json
{
  "seriesId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "seriesTitle": "RuPaul's Drag Race Season 18",
  "groupId": "g1234567-89ab-cdef-0123-456789abcdef",
  "eventSeriesType": "WATCH_PARTY",
  "showId": 1526,
  "seasonNumber": 18,
  "defaultTime": "20:00",
  "timezone": "America/Los_Angeles",
  "dayOverride": 4,
  "defaultHostId": "550e8400-e29b-41d4-a716-446655440000",
  "hangouts": [
    {
      "hangoutId": "h1234567-89ab-cdef-0123-456789abcdef",
      "title": "Episode 1: The Grand Premiere",
      "startTimestamp": 1704088800,
      "endTimestamp": 1704092400,
      "externalId": "12345",
      "combinedExternalIds": ["12345"]
    }
  ],
  "interestLevels": [
    {
      "userId": "u1234567-89ab-cdef-0123-456789abcdef",
      "level": "GOING",
      "userName": "jeana",
      "mainImagePath": "users/u1234567.../profile.jpg"
    },
    {
      "userId": "u2345678-9abc-def0-1234-56789abcdef0",
      "level": "INTERESTED",
      "userName": "alex",
      "mainImagePath": null
    }
  ]
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `seriesId` | string | UUID of the watch party series |
| `seriesTitle` | string | Series title (e.g., "Show Name Season X") |
| `groupId` | string | UUID of the owning group |
| `eventSeriesType` | string | Always "WATCH_PARTY" |
| `showId` | integer | TVMaze show ID |
| `seasonNumber` | integer | Season number |
| `defaultTime` | string | Default hangout time (HH:mm) |
| `timezone` | string | IANA timezone |
| `dayOverride` | integer | Day override (0-6) or null |
| `defaultHostId` | string | Default host UUID or null |
| `hangouts` | array | List of hangout summaries |
| `interestLevels` | array | Series-level interest from users |

**Hangout Summary Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `hangoutId` | string | UUID of the hangout |
| `title` | string | Episode title (may be "Double Episode: X, Y") |
| `startTimestamp` | long | Start time (Unix epoch seconds) |
| `endTimestamp` | long | End time (Unix epoch seconds) |
| `externalId` | string | Primary TVMaze episode ID |
| `combinedExternalIds` | array | All TVMaze episode IDs (for combined episodes) |

**Interest Level Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `userId` | string | UUID of the user |
| `level` | string | "GOING", "INTERESTED", or "NOT_GOING" |
| `userName` | string | User's display name |
| `mainImagePath` | string | User's profile image path (may be null) |

**Error Responses:**

| Status | Error Code | Cause |
|--------|------------|-------|
| `403` | `UNAUTHORIZED` | User is not a member of the group |
| `404` | `Resource not found` | Watch party or group not found |

---

### 3. Update Watch Party

Updates watch party settings. Optionally cascades changes to upcoming hangouts.

**Endpoint:** `PUT /groups/{groupId}/watch-parties/{seriesId}`

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `groupId` | string | Yes | UUID of the group |
| `seriesId` | string | Yes | UUID of the watch party series |

**Request Body:**

```json
{
  "defaultTime": "19:30",
  "timezone": "America/New_York",
  "dayOverride": 5,
  "defaultHostId": "new-host-uuid",
  "changeExistingUpcomingHangouts": true
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `defaultTime` | string | No | New default time (HH:mm format) |
| `timezone` | string | No | New IANA timezone |
| `dayOverride` | integer | No | New day override (0-6) or null to clear |
| `defaultHostId` | string | No | New default host UUID or empty string to clear |
| `changeExistingUpcomingHangouts` | boolean | No | Cascade to future hangouts (default: true) |

**Cascade Behavior:**
- When `changeExistingUpcomingHangouts` is `true` (default), time and host changes apply to all future hangouts
- When `false`, only series settings update; existing hangouts remain unchanged

**Success Response:** `200 OK`

Returns the same format as [Get Watch Party Details](#2-get-watch-party-details).

**Error Responses:**

| Status | Error Code | Cause |
|--------|------------|-------|
| `400` | `VALIDATION_ERROR` | Invalid time format, invalid timezone, invalid dayOverride |
| `403` | `UNAUTHORIZED` | User is not a member of the group |
| `404` | `Resource not found` | Watch party or group not found |

---

### 4. Delete Watch Party

Deletes a watch party series and all its hangouts.

**Endpoint:** `DELETE /groups/{groupId}/watch-parties/{seriesId}`

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `groupId` | string | Yes | UUID of the group |
| `seriesId` | string | Yes | UUID of the watch party series |

**Success Response:** `204 No Content`

**Error Responses:**

| Status | Error Code | Cause |
|--------|------------|-------|
| `403` | `UNAUTHORIZED` | User is not a member of the group |
| `404` | `Resource not found` | Watch party or group not found |

---

### 5. Set Series Interest

Sets the user's interest level for the entire watch party series.

**Endpoint:** `POST /watch-parties/{seriesId}/interest`

**Note:** This endpoint uses a different base path (`/watch-parties`) because it doesn't require the group ID.

**Path Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `seriesId` | string | Yes | UUID of the watch party series (36-character UUID format) |

**Request Body:**

```json
{
  "level": "GOING"
}
```

**Request Fields:**

| Field | Type | Required | Values | Description |
|-------|------|----------|--------|-------------|
| `level` | string | Yes | `GOING`, `INTERESTED`, `NOT_GOING` | User's interest level |

**Success Response:** `200 OK`

Empty body.

**Error Responses:**

| Status | Error Code | Cause |
|--------|------------|-------|
| `400` | `VALIDATION_ERROR` | Invalid level value or missing level |
| `403` | `UNAUTHORIZED` | User is not a member of any group owning this watch party |
| `404` | `Resource not found` | Watch party not found or not a watch party type |

**Notes:**
- Series interest is for notification targeting only
- Interest does not inherit to individual hangout RSVPs
- Users receive notifications for new episodes, title updates, and episode removals based on their interest level

---

## Episode Combination Rules

The system automatically combines episodes that air within 20 hours of each other:

| Episode Count | Title Format |
|---------------|--------------|
| 1 | Episode title as-is |
| 2 | "Double Episode: Title1, Title2" |
| 3 | "Triple Episode" |
| 4 | "Quadruple Episode" |
| 5+ | "Multi-Episode (N episodes)" |

For combined episodes:
- `externalId` = First episode's TVMaze ID
- `combinedExternalIds` = All episode IDs in the combination
- Runtime = Sum of all episode runtimes

---

## Time Calculation

### Start Time
The start time is calculated using the user-specified `defaultTime` and `timezone`:
- Respects daylight saving time automatically (IANA timezone)
- If `dayOverride` is set, the hangout is scheduled on that day of the week on or after the air date

### Day Override
| Value | Day |
|-------|-----|
| 0 | Sunday |
| 1 | Monday |
| 2 | Tuesday |
| 3 | Wednesday |
| 4 | Thursday |
| 5 | Friday |
| 6 | Saturday |

**Example:** Episode airs Tuesday Jan 7, `dayOverride: 4` (Thursday) → Hangout scheduled Thursday Jan 9.

### End Time
Runtime is rounded up to the nearest 30 minutes:
- 45 min → 60 min
- 61 min → 90 min
- 120 min → 120 min

---

## Valid Timezones

Use IANA timezone identifiers. Examples:
- `America/New_York`
- `America/Los_Angeles`
- `America/Chicago`
- `America/Denver`
- `Europe/London`
- `Europe/Paris`
- `Asia/Tokyo`
- `Australia/Sydney`

**Invalid formats:** `EST`, `PST`, `Eastern`, `Pacific` (will return 400 error)

---

## Background Updates

The system automatically polls TVMaze for updates every 2 hours:

1. **New Episodes:** When TVMaze announces new episodes, hangouts are automatically created and users with GOING/INTERESTED interest receive push notifications.

2. **Title Updates:** When episode titles change (e.g., from "TBA" to actual title), hangouts update automatically. Users receive one notification per hangout.

3. **Episode Removal:** If TVMaze removes an episode, the associated hangout is deleted and users are notified.

**Note:** If a user manually edits a hangout title, automatic title updates are disabled for that hangout.

---

## Error Response Format

All errors follow this format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": 1704067200000
}
```

For TVMaze-specific errors, additional fields may be present:

```json
{
  "error": "TVMAZE_SEASON_NOT_FOUND",
  "message": "TVMaze season not found: 176432",
  "timestamp": 1704067200000,
  "seasonId": 176432
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Invalid request data |
| `UNAUTHORIZED` | 403 | User lacks permission |
| `Resource not found` | 404 | Resource doesn't exist |
| `TVMAZE_SEASON_NOT_FOUND` | 404 | TVMaze season doesn't exist |
| `TVMAZE_NO_EPISODES` | 400 | No valid episodes in season |
| `TVMAZE_SERVICE_UNAVAILABLE` | 503 | TVMaze API unavailable |

---

## Version Compatibility

Watch parties require app version 2.0.0 or higher. Ensure the `X-App-Version` header is set on all requests:

```
X-App-Version: 2.0.0
```

Older app versions will not see watch parties in the group feed, but direct API calls will still work.
