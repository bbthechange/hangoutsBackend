# Idea List Controller API Documentation

## Base Information

**Base Path:** `/groups/{groupId}/idea-lists`

**Authentication:** All endpoints require JWT authentication via Bearer token in `Authorization` header

**Content-Type:** `application/json`

---

## Endpoints

### 1. Get All Idea Lists for a Group

**GET** `/groups/{groupId}/idea-lists`

Retrieves all idea lists within a group, including all ideas in each list.

#### Path Parameters
- `groupId` (string, UUID format) - The group ID

#### Example Request
```bash
GET /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists
Authorization: Bearer <jwt_token>
```

#### Success Response (200 OK)
```json
[
  {
    "id": "abc12345-e89b-12d3-a456-426614174001",
    "name": "Weekend Restaurant Ideas",
    "category": "RESTAURANT",
    "note": "Places we want to try",
    "isLocation": true,
    "createdBy": "user-123",
    "createdAt": "2025-01-15T10:30:00Z",
    "ideas": [
      {
        "id": "idea-001",
        "name": "Blue Water Cafe",
        "url": "https://bluewatercafe.net",
        "note": "Great seafood!",
        "addedBy": "user-456",
        "addedTime": "2025-01-15T11:00:00Z"
      }
    ]
  }
]
```

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" | Group ID is not a valid UUID |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

### 2. Get Single Idea List

**GET** `/groups/{groupId}/idea-lists/{listId}`

Retrieves a specific idea list with all its ideas.

#### Path Parameters
- `groupId` (string, UUID) - The group ID
- `listId` (string, UUID) - The idea list ID

#### Example Request
```bash
GET /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists/abc12345-e89b-12d3-a456-426614174001
Authorization: Bearer <jwt_token>
```

#### Success Response (200 OK)
```json
{
  "id": "abc12345-e89b-12d3-a456-426614174001",
  "name": "Weekend Restaurant Ideas",
  "category": "RESTAURANT",
  "note": "Places we want to try",
  "isLocation": true,
  "createdBy": "user-123",
  "createdAt": "2025-01-15T10:30:00Z",
  "ideas": [
    {
      "id": "idea-001",
      "name": "Blue Water Cafe",
      "url": "https://bluewatercafe.net",
      "note": "Great seafood!",
      "addedBy": "user-456",
      "addedTime": "2025-01-15T11:00:00Z"
    }
  ]
}
```

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" / "Invalid list ID format" | ID is not a valid UUID |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 404 | `NOT_FOUND` | "Idea list not found: {listId}" | Idea list doesn't exist |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

### 3. Create Idea List

**POST** `/groups/{groupId}/idea-lists`

Creates a new idea list (initially empty, without ideas).

#### Path Parameters
- `groupId` (string, UUID) - The group ID

#### Request Body
```json
{
  "name": "Summer Activities",
  "category": "ACTIVITY",
  "note": "Fun things to do this summer",
  "isLocation": false
}
```

**Field Validations:**
- `name` (required, string, 1-100 characters) - Name of the idea list
- `category` (optional, enum) - One of: `RESTAURANT`, `ACTIVITY`, `TRAIL`, `MOVIE`, `BOOK`, `TRAVEL`, `SHOW`, `BAR`, `OTHER`
- `note` (optional, string, max 500 characters) - Additional notes about the list
- `isLocation` (optional, boolean) - Whether ideas in this list represent physical locations

#### Example Request
```bash
POST /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "name": "Summer Activities",
  "category": "ACTIVITY",
  "note": "Fun things to do this summer",
  "isLocation": false
}
```

#### Success Response (201 Created)
```json
{
  "id": "new-list-uuid",
  "name": "Summer Activities",
  "category": "ACTIVITY",
  "note": "Fun things to do this summer",
  "isLocation": false,
  "createdBy": "user-123",
  "createdAt": "2025-01-15T12:00:00Z",
  "ideas": []
}
```

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" | Group ID is not a valid UUID |
| 400 | `VALIDATION_ERROR` | "Idea list name is required" | Name is missing or empty |
| 400 | `VALIDATION_ERROR` | "Idea list name must be between 1 and 100 characters" | Name length violation |
| 400 | `VALIDATION_ERROR` | "Note must be less than 500 characters" | Note too long |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

### 4. Update Idea List

**PUT** `/groups/{groupId}/idea-lists/{listId}`

Updates an idea list's name, category, or note. All fields are optional (partial update).

#### Path Parameters
- `groupId` (string, UUID) - The group ID
- `listId` (string, UUID) - The idea list ID

#### Request Body
```json
{
  "name": "Updated Restaurant List",
  "category": "RESTAURANT",
  "note": "Updated notes",
  "isLocation": true
}
```

**Field Validations:**
- `name` (optional, string, 1-100 characters) - Updated name
- `category` (optional, enum) - Updated category
- `note` (optional, string, max 500 characters) - Updated note
- `isLocation` (optional, boolean) - Whether ideas represent physical locations

**Note:** Only include fields you want to update. Omitted fields remain unchanged.

#### Example Request
```bash
PUT /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists/abc12345-e89b-12d3-a456-426614174001
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "name": "Updated Restaurant List"
}
```

#### Success Response (200 OK)
```json
{
  "id": "abc12345-e89b-12d3-a456-426614174001",
  "name": "Updated Restaurant List",
  "category": "RESTAURANT",
  "note": "Places we want to try",
  "isLocation": true,
  "createdBy": "user-123",
  "createdAt": "2025-01-15T10:30:00Z",
  "ideas": []
}
```

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" / "Invalid list ID format" | ID is not a valid UUID |
| 400 | `VALIDATION_ERROR` | "Idea list name must be between 1 and 100 characters" | Name length violation |
| 400 | `VALIDATION_ERROR` | "Note must be less than 500 characters" | Note too long |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 404 | `NOT_FOUND` | "Idea list not found: {listId}" | Idea list doesn't exist |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

### 5. Delete Idea List

**DELETE** `/groups/{groupId}/idea-lists/{listId}`

Deletes an idea list and all its ideas permanently.

#### Path Parameters
- `groupId` (string, UUID) - The group ID
- `listId` (string, UUID) - The idea list ID

#### Example Request
```bash
DELETE /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists/abc12345-e89b-12d3-a456-426614174001
Authorization: Bearer <jwt_token>
```

#### Success Response (204 No Content)
No response body.

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" / "Invalid list ID format" | ID is not a valid UUID |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 404 | `NOT_FOUND` | "Idea list not found: {listId}" | Idea list doesn't exist |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

## Idea Management (within Lists)

### 6. Add Idea to List

**POST** `/groups/{groupId}/idea-lists/{listId}/ideas`

Adds a new idea to an existing idea list.

#### Path Parameters
- `groupId` (string, UUID) - The group ID
- `listId` (string, UUID) - The idea list ID

#### Request Body
```json
{
  "name": "Blue Water Cafe",
  "url": "https://bluewatercafe.net",
  "note": "Recommended by Sarah"
}
```

**Field Validations:**
- `name` (optional, string, 1-200 characters) - Idea name
- `url` (optional, string, max 500 characters) - Related URL
- `note` (optional, string, max 1000 characters) - Additional notes

**Important:** At least one field (name, url, or note) must be provided and non-empty.

#### Example Request
```bash
POST /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists/abc12345-e89b-12d3-a456-426614174001/ideas
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "name": "Blue Water Cafe",
  "url": "https://bluewatercafe.net",
  "note": "Recommended by Sarah"
}
```

#### Success Response (201 Created)
```json
{
  "id": "idea-uuid",
  "name": "Blue Water Cafe",
  "url": "https://bluewatercafe.net",
  "note": "Recommended by Sarah",
  "addedBy": "user-123",
  "addedTime": "2025-01-15T13:00:00Z"
}
```

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" / "Invalid list ID format" | ID is not a valid UUID |
| 400 | `VALIDATION_ERROR` | "At least one field (name, url, or note) is required for an idea" | All fields are empty/null |
| 400 | `VALIDATION_ERROR` | "Idea name must be between 1 and 200 characters" | Name length violation |
| 400 | `VALIDATION_ERROR` | "URL must be less than 500 characters" | URL too long |
| 400 | `VALIDATION_ERROR` | "Note must be less than 1000 characters" | Note too long |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 404 | `NOT_FOUND` | "Idea list not found: {listId}" | Idea list doesn't exist |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

### 7. Update Idea

**PATCH** `/groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}`

Updates an idea's name, URL, or note. Only provided fields are updated (PATCH semantics).

#### Path Parameters
- `groupId` (string, UUID) - The group ID
- `listId` (string, UUID) - The idea list ID
- `ideaId` (string, UUID) - The idea ID

#### Request Body
```json
{
  "name": "Blue Water Cafe & Raw Bar",
  "note": "Updated note - highly recommended!"
}
```

**Field Validations:**
- `name` (optional, string, 1-200 characters) - Updated name
- `url` (optional, string, max 500 characters) - Updated URL
- `note` (optional, string, max 1000 characters) - Updated note

**Note:** Only include fields you want to update.

#### Example Request
```bash
PATCH /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists/abc12345-e89b-12d3-a456-426614174001/ideas/idea-uuid
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "note": "Updated note - highly recommended!"
}
```

#### Success Response (200 OK)
```json
{
  "id": "idea-uuid",
  "name": "Blue Water Cafe",
  "url": "https://bluewatercafe.net",
  "note": "Updated note - highly recommended!",
  "addedBy": "user-123",
  "addedTime": "2025-01-15T13:00:00Z"
}
```

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" / "Invalid list ID format" / "Invalid idea ID format" | ID is not a valid UUID |
| 400 | `VALIDATION_ERROR` | "Idea name must be between 1 and 200 characters" | Name length violation |
| 400 | `VALIDATION_ERROR` | "URL must be less than 500 characters" | URL too long |
| 400 | `VALIDATION_ERROR` | "Note must be less than 1000 characters" | Note too long |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 404 | `NOT_FOUND` | "Idea not found: {ideaId}" | Idea doesn't exist |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

### 8. Delete Idea

**DELETE** `/groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}`

Removes an idea from a list permanently.

#### Path Parameters
- `groupId` (string, UUID) - The group ID
- `listId` (string, UUID) - The idea list ID
- `ideaId` (string, UUID) - The idea ID

#### Example Request
```bash
DELETE /groups/123e4567-e89b-12d3-a456-426614174000/idea-lists/abc12345-e89b-12d3-a456-426614174001/ideas/idea-uuid
Authorization: Bearer <jwt_token>
```

#### Success Response (204 No Content)
No response body.

#### Error Responses

| Status | Error Code | Message | When It Occurs |
|--------|------------|---------|----------------|
| 400 | `VALIDATION_ERROR` | "Invalid group ID format" / "Invalid list ID format" / "Invalid idea ID format" | ID is not a valid UUID |
| 403 | `UNAUTHORIZED` | "User is not a member of group: {groupId}" | User is not a member of the group |
| 404 | `NOT_FOUND` | "Idea not found: {ideaId}" | Idea doesn't exist |
| 500 | `INTERNAL_ERROR` | "An unexpected error occurred" | Server error |

---

## Data Models

### IdeaListCategory Enum
Valid values for the `category` field:
- `RESTAURANT`
- `ACTIVITY`
- `TRAIL`
- `MOVIE`
- `BOOK`
- `TRAVEL`
- `SHOW`
- `BAR`
- `OTHER`

---

## Common Error Response Format

All error responses follow this structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": 1705321200000
}
```

---

## Authorization Rules

1. **All endpoints require JWT authentication** - Must include `Authorization: Bearer <token>` header
2. **Group membership required** - User must be a member of the group to access any endpoint
3. **No special permissions** - Any group member can create, update, or delete lists and ideas

---

## Notes for Frontend Developers

1. **UUID Format**: All IDs must be valid UUIDs (format: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`)
2. **Input Trimming**: All string inputs are automatically trimmed of leading/trailing whitespace
3. **Partial Updates**: PUT (for lists) and PATCH (for ideas) support partial updates - only send fields you want to change
4. **Empty Ideas**: When creating a list, it starts empty. Use the "Add Idea" endpoint to populate it
5. **Timestamps**: All timestamps are in ISO 8601 format (UTC)
6. **Case Sensitivity**: Category enum values are case-sensitive (use uppercase)
