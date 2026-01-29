# Host At Place API

**AUDIENCE:** Mobile and web clients integrating the "Host At Place" feature for hangouts.

**LAST UPDATED:** 2026-01-20

## Overview

The Host At Place feature allows a hangout to indicate that a specific user is hosting the event at their place (e.g., "Game night at Sarah's house"). When set, the API returns the host's display name and profile image for UI rendering.

## Request Fields

### CreateHangoutRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `hostAtPlaceUserId` | string (UUID) | No | User ID of the person hosting at their place |

### UpdateHangoutRequest

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `hostAtPlaceUserId` | string (UUID) | No | User ID to set, or `null` to clear |

## Response Fields

### HangoutDetailDTO (GET /hangouts/{hangoutId})

| Field | Type | Description |
|-------|------|-------------|
| `hostAtPlaceDisplayName` | string | Display name of the hosting user (resolved from user cache) |
| `hostAtPlaceImagePath` | string | Profile image path of the hosting user |

### HangoutSummaryDTO (Group Feed)

| Field | Type | Description |
|-------|------|-------------|
| `hostAtPlaceUserId` | string | Raw user ID of the host |
| `hostAtPlaceDisplayName` | string | Display name (enriched at read time) |
| `hostAtPlaceImagePath` | string | Profile image path (enriched at read time) |

## Examples

### Create Hangout with Host At Place

**Request:**
```bash
POST /hangouts
Authorization: Bearer {jwt-token}
Content-Type: application/json

{
  "title": "Game Night",
  "visibility": "INVITE_ONLY",
  "associatedGroups": ["group-uuid-here"],
  "hostAtPlaceUserId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (201 Created):**
```json
{
  "hangoutId": "123e4567-e89b-12d3-a456-426614174000",
  "title": "Game Night",
  "visibility": "INVITE_ONLY",
  ...
}
```

### Get Hangout Detail with Host Info

**Request:**
```bash
GET /hangouts/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer {jwt-token}
```

**Response (200 OK):**
```json
{
  "hangout": {
    "hangoutId": "123e4567-e89b-12d3-a456-426614174000",
    "title": "Game Night",
    ...
  },
  "hostAtPlaceDisplayName": "Sarah",
  "hostAtPlaceImagePath": "users/550e8400.../profile.jpg",
  ...
}
```

### Update Host At Place

**Set a new host:**
```bash
PATCH /hangouts/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer {jwt-token}
Content-Type: application/json

{
  "hostAtPlaceUserId": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Clear the host (no longer at someone's place):**
```bash
PATCH /hangouts/123e4567-e89b-12d3-a456-426614174000
Authorization: Bearer {jwt-token}
Content-Type: application/json

{
  "hostAtPlaceUserId": null
}
```

### Group Feed Response

When fetching group feed, hangouts include host at place info:

```json
{
  "feedItems": [
    {
      "itemType": "HANGOUT",
      "hangoutId": "123e4567-e89b-12d3-a456-426614174000",
      "title": "Game Night",
      "hostAtPlaceUserId": "550e8400-e29b-41d4-a716-446655440000",
      "hostAtPlaceDisplayName": "Sarah",
      "hostAtPlaceImagePath": "users/550e8400.../profile.jpg",
      ...
    }
  ]
}
```

## Validation & Error Handling

### Invalid User ID

If `hostAtPlaceUserId` references a non-existent user:

**Response (400 Bad Request):**
```json
{
  "error": "Invalid hostAtPlaceUserId: user not found"
}
```

### User Not Found at Read Time

If the host user is deleted after being set, the display name and image path fields will be `null` in responses. The `hostAtPlaceUserId` will still contain the original value.

## Behavior Notes

1. **Validation on Write**: The user ID is validated when creating or updating a hangout. The user must exist.

2. **Enrichment on Read**: Display name and image path are resolved from the user cache at read time, not stored directly on the hangout.

3. **Pointer Denormalization**: When `hostAtPlaceUserId` changes, all associated group pointers are updated to reflect the new value.

4. **Clearing the Field**: Send `null` to remove the host at place designation.

5. **No Authorization Check**: Any valid user ID can be set as the host - there's no requirement that the host be a group member or hangout participant.

## Client UI Recommendations

- Display the host's name and avatar when `hostAtPlaceUserId` is set
- Show location context like "at Sarah's place" or "Hosted by Sarah"
- Handle `null` display name gracefully (user may have been deleted)
- Consider showing a house/home icon to indicate "at someone's place"

## Data Flow

```
Create/Update Request
        │
        ▼
   Validate user exists
        │
        ▼
   Save to Hangout (canonical)
        │
        ▼
   Update HangoutPointers (denormalized)
        │
        ▼
   On Read: Enrich with user display name & image
```

## Related Documentation

- [Hangout API Deep Dive](./HANGOUT_API_DEEP_DIVE.md) - Complete hangout API reference
- [Groups API Deep Dive](./GROUPS_API_DEEP_DIVE.md) - Group feed endpoints
