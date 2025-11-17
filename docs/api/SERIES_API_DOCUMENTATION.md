# Series Controller API Documentation

## Overview
The Series Controller manages event series - multi-part events that are logically grouped together. A series consists of multiple hangouts that share a common theme or purpose.

**Base URL**: `/series`

**Authentication**: All endpoints require JWT token in `Authorization: Bearer <token>` header

---

## Endpoints

### 1. Create Series
**POST** `/series`

Creates a new event series by converting an existing hangout and adding a new hangout to it.

#### Authorization
- Requesting user must be a member of the initial hangout

#### Request Body
```json
{
  "initialHangoutId": "hangout-uuid-123",
  "newMemberRequest": {
    "title": "Part 2: The Sequel",
    "description": "Continuing our adventure",
    "timeInfo": {
      "periodGranularity": "SPECIFIC",
      "periodStart": "2025-11-01T00:00:00Z",
      "startTime": "2025-11-01T18:00:00Z",
      "endTime": "2025-11-01T21:00:00Z"
    },
    "location": {
      "name": "Central Park",
      "streetAddress": "123 Main St",
      "city": "New York",
      "state": "NY",
      "postalCode": "10001",
      "country": "USA"
    },
    "visibility": "INVITE_ONLY",
    "mainImagePath": "https://s3.amazonaws.com/bucket/image.jpg",
    "associatedGroups": ["group-uuid-456"],
    "attributes": [
      {
        "attributeName": "difficulty",
        "stringValue": "moderate"
      }
    ],
    "carpoolEnabled": true
  }
}
```

#### Request Field Details
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `initialHangoutId` | string | Yes | UUID of existing hangout to convert to series |
| `newMemberRequest` | object | Yes | Details for the new hangout to add |
| `newMemberRequest.title` | string | No | Title of new hangout |
| `newMemberRequest.description` | string | No | Description of new hangout |
| `newMemberRequest.timeInfo` | object | No | Fuzzy time information |
| `newMemberRequest.timeInfo.periodGranularity` | string | No | Granularity level (e.g., "SPECIFIC", "DAY", "WEEK") |
| `newMemberRequest.timeInfo.periodStart` | string | No | ISO 8601 timestamp with timezone |
| `newMemberRequest.timeInfo.startTime` | string | No | ISO 8601 timestamp with timezone |
| `newMemberRequest.timeInfo.endTime` | string | No | ISO 8601 timestamp with timezone |
| `newMemberRequest.location` | object | No | Address information |
| `newMemberRequest.visibility` | enum | No | `PUBLIC`, `INVITE_ONLY`, or `ACCEPTED_ONLY` |
| `newMemberRequest.mainImagePath` | string | No | URL to main image |
| `newMemberRequest.associatedGroups` | string[] | No | Array of group UUIDs |
| `newMemberRequest.attributes` | array | No | Custom attributes for hangout |
| `newMemberRequest.carpoolEnabled` | boolean | No | Enable carpool coordination |

#### Success Response (201 Created)
```json
{
  "seriesId": "series-uuid-789",
  "seriesTitle": "Multi-Part Event Series",
  "seriesDescription": "A series of connected events",
  "primaryEventId": "hangout-uuid-123",
  "groupId": "group-uuid-456",
  "startTimestamp": 1730419200000,
  "endTimestamp": 1730505600000,
  "hangoutIds": [
    "hangout-uuid-123",
    "hangout-uuid-124"
  ],
  "version": 1,
  "createdAt": "2025-10-17T10:00:00Z",
  "updatedAt": "2025-10-17T10:00:00Z"
}
```

#### Error Responses
| Status | Description |
|--------|-------------|
| 400 Bad Request | Invalid request body or validation error |
| 403 Forbidden | User not authorized to create series from this hangout |
| 404 Not Found | Initial hangout not found |
| 500 Internal Server Error | Server error or database error |

---

### 2. Get Series Detail
**GET** `/series/{seriesId}`

Retrieves detailed information about a series including all hangout details.

#### Authorization
- Requesting user must be a member of at least one hangout in the series

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `seriesId` | string | UUID of the series |

#### Success Response (200 OK)
```json
{
  "seriesId": "series-uuid-789",
  "seriesTitle": "Multi-Part Event Series",
  "seriesDescription": "A series of connected events",
  "primaryEventId": "hangout-uuid-123",
  "groupId": "group-uuid-456",
  "startTimestamp": 1730419200000,
  "endTimestamp": 1730505600000,
  "hangouts": [
    {
      "hangout": {
        "id": "hangout-uuid-123",
        "title": "Part 1: The Beginning",
        "description": "First event in the series",
        "timeInfo": {
          "periodGranularity": "SPECIFIC",
          "periodStart": "2025-11-01T00:00:00Z",
          "startTime": "2025-11-01T18:00:00Z",
          "endTime": "2025-11-01T21:00:00Z"
        },
        "location": {
          "name": "Central Park",
          "streetAddress": "123 Main St",
          "city": "New York",
          "state": "NY",
          "postalCode": "10001",
          "country": "USA"
        },
        "visibility": "INVITE_ONLY",
        "mainImagePath": "https://s3.amazonaws.com/bucket/image.jpg",
        "groupIds": ["group-uuid-456"],
        "carpoolEnabled": true
      },
      "attributes": [
        {
          "id": "attr-uuid-001",
          "hangoutId": "hangout-uuid-123",
          "attributeName": "difficulty",
          "stringValue": "moderate"
        }
      ],
      "polls": [],
      "cars": [],
      "votes": [],
      "attendance": [],
      "carRiders": [],
      "needsRide": []
    },
    {
      "hangout": {
        "id": "hangout-uuid-124",
        "title": "Part 2: The Sequel",
        "description": "Continuing our adventure"
      },
      "attributes": [],
      "polls": [],
      "cars": [],
      "votes": [],
      "attendance": [],
      "carRiders": [],
      "needsRide": []
    }
  ],
  "version": 1,
  "createdAt": "2025-10-17T10:00:00Z",
  "updatedAt": "2025-10-17T10:00:00Z"
}
```

#### Error Responses
| Status | Description |
|--------|-------------|
| 403 Forbidden | User not authorized to view this series |
| 404 Not Found | Series not found |
| 500 Internal Server Error | Server error or database error |

---

### 3. Add Hangout to Series
**POST** `/series/{seriesId}/hangouts`

Adds a new hangout to an existing series.

#### Authorization
- Requesting user must be a member of the series (member of at least one hangout in series)

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `seriesId` | string | UUID of the series |

#### Request Body
```json
{
  "title": "Part 3: The Finale",
  "description": "The epic conclusion",
  "timeInfo": {
    "periodGranularity": "SPECIFIC",
    "periodStart": "2025-11-03T00:00:00Z",
    "startTime": "2025-11-03T18:00:00Z",
    "endTime": "2025-11-03T21:00:00Z"
  },
  "location": {
    "name": "Times Square",
    "streetAddress": "789 Broadway",
    "city": "New York",
    "state": "NY",
    "postalCode": "10036",
    "country": "USA"
  },
  "visibility": "INVITE_ONLY",
  "mainImagePath": "https://s3.amazonaws.com/bucket/finale.jpg",
  "associatedGroups": ["group-uuid-456"],
  "attributes": [
    {
      "attributeName": "difficulty",
      "stringValue": "hard"
    }
  ],
  "carpoolEnabled": true
}
```

#### Success Response (201 Created)
```json
{
  "seriesId": "series-uuid-789",
  "seriesTitle": "Multi-Part Event Series",
  "seriesDescription": "A series of connected events",
  "primaryEventId": "hangout-uuid-123",
  "groupId": "group-uuid-456",
  "startTimestamp": 1730419200000,
  "endTimestamp": 1730678400000,
  "hangoutIds": [
    "hangout-uuid-123",
    "hangout-uuid-124",
    "hangout-uuid-125"
  ],
  "version": 2,
  "createdAt": "2025-10-17T10:00:00Z",
  "updatedAt": "2025-10-17T11:30:00Z"
}
```

#### Error Responses
| Status | Description |
|--------|-------------|
| 400 Bad Request | Invalid request body or validation error |
| 403 Forbidden | User not authorized to add hangout to this series |
| 404 Not Found | Series not found |
| 500 Internal Server Error | Server error or database error |

---

### 4. Unlink Hangout from Series
**DELETE** `/series/{seriesId}/hangouts/{hangoutId}`

Removes a hangout from a series without deleting the hangout. The hangout remains as a standalone event.

#### Authorization
- Requesting user must be a member of the series

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `seriesId` | string | UUID of the series |
| `hangoutId` | string | UUID of the hangout to unlink |

#### Success Response (204 No Content)
No response body.

#### Error Responses
| Status | Description |
|--------|-------------|
| 403 Forbidden | User not authorized to modify this series |
| 404 Not Found | Series or hangout not found |
| 500 Internal Server Error | Server error or database error |

---

### 5. Update Series
**PUT** `/series/{seriesId}`

Updates the properties of an existing series.

#### Authorization
- Requesting user must be a member of the series

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `seriesId` | string | UUID of the series |

#### Request Body
```json
{
  "version": 2,
  "seriesTitle": "Updated Series Title",
  "seriesDescription": "Updated description of the series",
  "primaryEventId": "hangout-uuid-124"
}
```

#### Request Field Details
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | number | Yes | Current version for optimistic locking |
| `seriesTitle` | string | No | New title for series (null = no change) |
| `seriesDescription` | string | No | New description (null = no change) |
| `primaryEventId` | string | No | New primary event ID - must be in series (null = no change) |

#### Success Response (200 OK)
```json
{
  "seriesId": "series-uuid-789",
  "seriesTitle": "Updated Series Title",
  "seriesDescription": "Updated description of the series",
  "primaryEventId": "hangout-uuid-124",
  "groupId": "group-uuid-456",
  "startTimestamp": 1730419200000,
  "endTimestamp": 1730678400000,
  "hangoutIds": [
    "hangout-uuid-123",
    "hangout-uuid-124",
    "hangout-uuid-125"
  ],
  "version": 3,
  "createdAt": "2025-10-17T10:00:00Z",
  "updatedAt": "2025-10-17T12:15:00Z"
}
```

#### Error Responses
| Status | Description |
|--------|-------------|
| 400 Bad Request | Invalid request body, validation error, or version conflict (optimistic locking) |
| 403 Forbidden | User not authorized to update this series |
| 404 Not Found | Series not found |
| 500 Internal Server Error | Server error or database error |

---

### 6. Delete Entire Series
**DELETE** `/series/{seriesId}`

Deletes an entire series and all of its constituent hangouts. This is a cascading delete operation.

**WARNING**: This permanently deletes all hangouts in the series.

#### Authorization
- Requesting user must be a member of the series

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `seriesId` | string | UUID of the series |

#### Success Response (204 No Content)
No response body.

#### Error Responses
| Status | Description |
|--------|-------------|
| 403 Forbidden | User not authorized to delete this series |
| 404 Not Found | Series not found |
| 500 Internal Server Error | Server error or database error |

---

## Common Data Structures

### TimeInfo Object
```json
{
  "periodGranularity": "SPECIFIC",
  "periodStart": "2025-11-01T00:00:00Z",
  "startTime": "2025-11-01T18:00:00Z",
  "endTime": "2025-11-01T21:00:00Z"
}
```

### Address Object
```json
{
  "name": "Central Park",
  "streetAddress": "123 Main St",
  "city": "New York",
  "state": "NY",
  "postalCode": "10001",
  "country": "USA"
}
```

### Attribute Object
```json
{
  "attributeName": "difficulty",
  "stringValue": "moderate"
}
```

**Attribute Constraints**:
- `attributeName`: 1-100 characters, cannot be reserved names (id, type, system, internal, pk, sk, gsi*, system_*, internal_*)
- `stringValue`: 0-1000 characters

---

## Enumerations

### EventVisibility
- `PUBLIC` - Visible to all users
- `INVITE_ONLY` - Visible only to invited users
- `ACCEPTED_ONLY` - Visible only to users who accepted invite

---

## Authentication

All endpoints require JWT authentication. Include the token in the request header:

```
Authorization: Bearer <your-jwt-token>
```

To obtain a token, use the `/auth/login` endpoint (see Authentication API documentation).

---

## Error Response Format

All error responses follow this general structure:

```json
{
  "timestamp": "2025-10-17T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation error message",
  "path": "/series"
}
```

---

## Notes for GUI Implementation

1. **Optimistic Locking**: When updating a series, always include the current `version` number. If another user updated the series, you'll get a 400 error and should refresh the data.

2. **Timestamps**: All timestamps are in milliseconds since Unix epoch. ISO 8601 strings are used in TimeInfo objects.

3. **Authorization**: The API automatically determines user authorization based on the JWT token. No need to pass user IDs in requests.

4. **Series vs Hangout**: A series is a container for multiple hangouts. Individual hangouts can be accessed via the Hangout API, but series operations affect multiple hangouts at once.

5. **Cascading Deletes**: Deleting a series deletes all its hangouts. If you want to remove a hangout without deleting it, use the unlink endpoint instead.

6. **Primary Event**: The `primaryEventId` designates which hangout represents the "main" event in the series. This can be useful for display purposes.

7. **Associated Groups**: Hangouts in a series can be associated with groups. All hangouts in a series typically share the same group association.

---

## Example Workflows

### Creating a Multi-Part Event Series

1. Create initial hangout via Hangout API
2. Call `POST /series` with the initial hangout ID and details for part 2
3. Series is created containing both hangouts
4. Add additional parts via `POST /series/{seriesId}/hangouts`

### Modifying a Series

1. Get series detail via `GET /series/{seriesId}`
2. Note the current `version` number
3. Update series via `PUT /series/{seriesId}` with version number
4. If update fails with 400, refresh the series (someone else updated it)

### Removing a Hangout

1. To remove without deleting: `DELETE /series/{seriesId}/hangouts/{hangoutId}`
2. To delete entire series: `DELETE /series/{seriesId}` (deletes all hangouts)
