# Groups API Deep Dive

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [API Endpoints](#api-endpoints)
- [Data Models](#data-models)
- [Database Schema](#database-schema)
- [Authorization Rules](#authorization-rules)
- [Error Handling](#error-handling)
- [Performance Optimizations](#performance-optimizations)
- [Integration Testing Guide](#integration-testing-guide)

---

## Overview

The Groups API provides comprehensive group management functionality including creation, membership management, feed retrieval, and invite code-based joining. The system uses a **single-table DynamoDB design** (`InviterTable`) with denormalization for query efficiency and atomic transactions for data consistency.

**Key Features:**
- Atomic group creation with first member
- Efficient membership queries using GSI patterns
- Denormalized data for N+1 query prevention
- ETag-based caching for feed endpoints
- Rate-limited invite code preview
- Calendar subscription token support

**CRITICAL:** This API follows the **Canonical and Pointer Pattern** defined in `DYNAMODB_DESIGN_GUIDE.md`. All database operations must adhere to these patterns to prevent severe performance issues. Read that document before working with this API.

### Canonical vs Pointer Pattern

- **Group (Canonical)**: The authoritative record at `PK=GROUP#{groupId}, SK=METADATA`
- **GroupMembership**: Links users to groups with denormalized data
- **HangoutPointer**: Denormalized hangout summaries for efficient group feeds
  - **NEVER** fetch canonical Hangout records when displaying feeds
  - Pointer contains all data needed for list views
  - If data is missing, update the pointer schema (see `ATTRIBUTE_ADDITION_GUIDE.md`)

---

## Architecture

### Layer Structure
```
Controller (HTTP) → Service (Business Logic) → Repository (DynamoDB)
     ↓                     ↓                          ↓
  DTOs               Domain Models              TableSchema
```

### Key Components
- **GroupController**: REST endpoints with JWT authentication
- **GroupService/GroupServiceImpl**: Business logic and authorization
- **GroupRepository/PolymorphicGroupRepositoryImpl**: DynamoDB operations
- **GroupFeedService**: Polymorphic feed aggregation
- **RateLimitingService**: Invite preview rate limiting

---

## API Endpoints

### 1. Create Group
**Endpoint:** `POST /groups`
**Authentication:** Required (JWT)
**Request Body:**
```json
{
  "groupName": "string (1-100 chars, required)",
  "isPublic": "boolean (required)",
  "mainImagePath": "string (optional)",
  "backgroundImagePath": "string (optional)"
}
```

**Response:** `201 Created`
```json
{
  "groupId": "uuid",
  "groupName": "string",
  "isPublic": boolean,
  "userRole": "ADMIN",
  "joinedAt": "ISO-8601 timestamp",
  "createdAt": "ISO-8601 timestamp",
  "mainImagePath": "string",
  "backgroundImagePath": "string",
  "userMainImagePath": "string"
}
```

**Internal Flow:**
1. Extract userId from JWT (via `extractUserId()`)
2. Validate request (groupName required, 1-100 chars, isPublic required)
3. Verify creator exists in Users table
4. Create Group entity with UUID
5. Create GroupMembership entity (role=ADMIN)
6. Denormalize: group images → membership, user image → membership
7. **Atomic operation**: `TransactWriteItems` with both Group and GroupMembership
8. Return GroupDTO with combined data

**Database Operations:**
- **Transaction**: 2 PutItem operations (Group + GroupMembership)
- **Queries**: 1 GetItem (verify creator exists)
- **Total WCU**: 2 (transaction)

**Errors:**
- `400 VALIDATION_ERROR`: Invalid groupName or missing isPublic
- `403 UNAUTHORIZED`: No authenticated user
- `404 USER_NOT_FOUND`: Creator not found

**Authorization:** Authenticated users only (no other checks)

---

### 2. Get User's Groups
**Endpoint:** `GET /groups`
**Authentication:** Required (JWT)
**Request:** None

**Response:** `200 OK`
```json
[
  {
    "groupId": "uuid",
    "groupName": "string",
    "userRole": "ADMIN|MEMBER",
    "joinedAt": "ISO-8601 timestamp",
    "mainImagePath": "string",
    "backgroundImagePath": "string",
    "userMainImagePath": "string"
  }
]
```

**Internal Flow:**
1. Extract userId from JWT
2. Query UserGroupIndex GSI: `gsi1pk = USER#{userId}`
3. Deserialize GroupMembership records
4. Map to GroupDTO using denormalized data (NO additional Group queries needed!)

**Database Operations:**
- **GSI Query**: UserGroupIndex on `gsi1pk`
- **RCU**: ~0.5 per group (denormalized data eliminates N+1 queries)

**Performance Note:** This is highly optimized - single GSI query returns ALL needed data due to denormalization of groupName and image paths on membership records.

**Errors:**
- `403 UNAUTHORIZED`: No authenticated user

**Authorization:** Authenticated users only

---

### 3. Get Group Details
**Endpoint:** `GET /groups/{groupId}`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)

**Response:** `200 OK`
```json
{
  "groupId": "uuid",
  "groupName": "string",
  "isPublic": boolean,
  "userRole": "ADMIN|MEMBER",
  "joinedAt": "ISO-8601 timestamp",
  "createdAt": "ISO-8601 timestamp",
  "mainImagePath": "string",
  "backgroundImagePath": "string",
  "userMainImagePath": "string"
}
```

**Internal Flow:**
1. Extract userId from JWT
2. Validate groupId format (UUID regex)
3. GetItem: Group metadata (PK=GROUP#{groupId}, SK=METADATA)
4. GetItem: User's membership (PK=GROUP#{groupId}, SK=USER#{userId})
5. Return GroupDTO combining both

**Database Operations:**
- **GetItem**: 2 operations (Group + Membership)
- **RCU**: 1 (2 strongly consistent reads)

**Errors:**
- `400 VALIDATION_ERROR`: Invalid groupId format
- `403 UNAUTHORIZED`: User not a member
- `404 NOT_FOUND`: Group doesn't exist

**Authorization:** User must be a member of the group

---

### 4. Update Group
**Endpoint:** `PATCH /groups/{groupId}`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)
**Request Body:**
```json
{
  "groupName": "string (1-100 chars, optional)",
  "isPublic": "boolean (optional)",
  "mainImagePath": "string (optional)",
  "backgroundImagePath": "string (optional)"
}
```

**Response:** `200 OK` (same as Get Group Details)

**Internal Flow:**
1. Extract userId from JWT
2. Validate: At least one field must be provided (`hasUpdates()`)
3. Verify membership exists
4. Get current Group entity
5. Update only provided fields
6. Save updated Group
7. **If groupName changed**: Update all membership records with new name (batched TransactWriteItems)
8. **If images changed**: Update all membership records with new paths + async S3 deletion of old images
9. Return updated GroupDTO

**Database Operations:**
- **GetItem**: 2 (membership + group)
- **PutItem**: 1 (updated group)
- **If name changed**: Query all memberships + batched TransactWriteItems (25 per batch)
- **If images changed**: Query all memberships + batched TransactWriteItems + async S3 delete

**Denormalization Sync:**
- **Group name change**: Triggers `updateMembershipGroupNames()`
  - Queries all members
  - Batches into groups of 25
  - Uses TransactWriteItems for atomic updates
- **Image path change**: Triggers `updateMembershipGroupImagePaths()`
  - Same batching strategy
  - Async S3 deletion via `s3Service.deleteImageAsync()`

**Errors:**
- `400 VALIDATION_ERROR`: No fields provided or invalid groupName length
- `403 UNAUTHORIZED`: User not a member
- `404 NOT_FOUND`: Group doesn't exist

**Authorization:** User must be a member (not admin-only)

**Important Notes:**
- Old S3 images are deleted asynchronously (fire-and-forget)
- Denormalization updates use transactions for consistency
- Large groups (>25 members) require multiple transaction batches

---

### 5. Delete Group
**Endpoint:** `DELETE /groups/{groupId}`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)

**Response:** `204 No Content`

**Internal Flow:**
1. Extract userId from JWT
2. Get user's membership
3. **Verify user is ADMIN** (only admins can delete groups)
4. Query ALL records for group (PK=GROUP#{groupId})
5. Batch delete in chunks of 25 (DynamoDB BatchWriteItem limit)

**Database Operations:**
- **GetItem**: 1 (verify membership)
- **Query**: 1 (get all group records)
- **BatchWriteItem**: Multiple (25 items per batch)
- **Deletes**: Group metadata + all memberships + all hangout pointers

**What Gets Deleted:**
- Group metadata record (SK=METADATA)
- All membership records (SK=USER#{...})
- All hangout pointer records (SK=HANGOUT#{...})
- All series pointer records (SK=SERIES#{...})

**Errors:**
- `403 UNAUTHORIZED`: User not an admin
- `404 NOT_FOUND`: Group or membership doesn't exist

**Authorization:** User must be a group ADMIN

**Important Notes:**
- This is a complete deletion - all group data is removed
- Hangout canonical records (in HANGOUT#{hangoutId}) are NOT deleted (only pointers)
- Uses batching to handle groups with many members/hangouts

---

### 6. Add Member
**Endpoint:** `POST /groups/{groupId}/members`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)
**Request Body:**
```json
{
  "userId": "uuid (optional)",
  "phoneNumber": "string (optional)"
}
```
**Constraint:** Exactly ONE of userId or phoneNumber must be provided

**Response:** `200 OK` (empty body)

**Internal Flow:**
1. Extract userId from JWT (addedBy)
2. Verify group exists
3. **For private groups**: Verify addedBy is a member
4. **For public groups**: Skip membership check
5. If phoneNumber provided: Find or create user via `inviteService.findOrCreateUserByPhoneNumber()`
6. If userId provided: Verify user exists
7. Check user is not already a member
8. Create GroupMembership (role=MEMBER)
9. Denormalize: group images → membership, user image → membership
10. Save membership

**Database Operations:**
- **GetItem**: 1 (group), 1 (verify user exists)
- **GetItem**: 1 (check existing membership)
- **PutItem**: 1 (new membership)
- **If phoneNumber**: Additional query/create user

**Errors:**
- `400 VALIDATION_ERROR`: Both userId and phoneNumber provided, or neither
- `400 VALIDATION_ERROR`: User already a member
- `403 UNAUTHORIZED`: Non-member trying to add to private group
- `404 NOT_FOUND`: Group or user not found

**Authorization:**
- **Private groups**: Only members can add new members
- **Public groups**: Anyone (authenticated) can add members

**Important Notes:**
- New members always get role=MEMBER (not ADMIN)
- phoneNumber path creates user if doesn't exist

---

### 7. Remove Member
**Endpoint:** `DELETE /groups/{groupId}/members/{userId}`
**Authentication:** Required (JWT)
**Path Parameters:**
- `groupId` (UUID format validated)
- `userId` (UUID format validated)

**Response:** `204 No Content`

**Internal Flow:**
1. Extract userId from JWT (removedBy)
2. Verify target membership exists
3. **Authorization check**: User can remove themselves OR admins can remove others
4. Delete membership record

**Database Operations:**
- **GetItem**: 1-2 (verify target membership + optionally verify removedBy is admin)
- **DeleteItem**: 1

**Errors:**
- `403 UNAUTHORIZED`: Non-admin trying to remove someone else
- `404 NOT_FOUND`: Target user not in group

**Authorization:**
- User can remove themselves
- Admins can remove anyone (not implemented in current code - treats all members equally)

**Important Notes:**
- Does NOT delete the group if last member
- Use `leaveGroup` instead for better last-member handling

---

### 8. Leave Group
**Endpoint:** `POST /groups/{groupId}/leave`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)

**Response:** `204 No Content`

**Internal Flow:**
1. Extract userId from JWT
2. Verify user is a member
3. **Query all members** to check if user is last member
4. **If last member**: Delete entire group (calls `groupRepository.delete()`)
5. **Otherwise**: Remove membership only

**Database Operations:**
- **GetItem**: 1 (verify membership)
- **Query**: 1 (get all members)
- **If last member**: Query + BatchWriteItem (full group deletion)
- **Otherwise**: DeleteItem (single membership)

**Errors:**
- `404 NOT_FOUND`: User not in group

**Authorization:** User must be a member

**Important Notes:**
- **Auto-cleanup**: Last member leaving triggers full group deletion
- This prevents orphaned groups with no members
- Different from `removeMember` which doesn't check last member

---

### 9. Get Group Members
**Endpoint:** `GET /groups/{groupId}/members`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)

**Response:** `200 OK`
```json
[
  {
    "userId": "uuid",
    "userName": "string (current display name)",
    "mainImagePath": "string (current profile image)",
    "role": "ADMIN|MEMBER",
    "joinedAt": "ISO-8601 timestamp"
  }
]
```

**Internal Flow:**
1. Extract userId from JWT
2. Verify user is a member
3. Query memberships: PK=GROUP#{groupId}, SK begins_with USER#
4. **For each membership**: GetItem from Users table for current userName and mainImagePath
5. Map to GroupMemberDTO

**Database Operations:**
- **GetItem**: 1 (verify membership)
- **Query**: 1 (all memberships)
- **GetItem**: N (one per member for current user data)

**Errors:**
- `403 UNAUTHORIZED`: User not a member
- `404 NOT_FOUND`: Group doesn't exist

**Authorization:** User must be a member

**Important Notes:**
- **N+1 Query Alert**: Gets current user data for each member
- userName and mainImagePath come from live User records (not denormalized on membership)
- If user not found, defaults to "Unknown User" and null image

---

### 10. Get Group Feed
**Endpoint:** `GET /groups/{groupId}/feed`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)
**Query Parameters:**
- `limit` (integer, optional, min=1)
- `startingAfter` (string, pagination token, optional)
- `endingBefore` (string, pagination token, optional)
- **Headers:** `If-None-Match` (ETag, optional)

**Response:** `200 OK` or `304 Not Modified`
```json
{
  "groupId": "uuid",
  "withDay": [
    {
      "hangoutId": "uuid",
      "title": "string",
      "startTimestamp": number,
      "endTimestamp": number,
      "mainImagePath": "string",
      "participantCount": number,
      "userRsvpStatus": "string",
      "hasVoted": boolean
    },
    {
      "seriesId": "uuid",
      "seriesTitle": "string",
      "seriesDescription": "string",
      "primaryEventId": "uuid",
      "startTimestamp": number,
      "endTimestamp": number,
      "mainImagePath": "string",
      "parts": [ /* array of HangoutSummaryDTO */ ]
    }
  ],
  "needsDay": [],  // DEPRECATED: Always empty - feed only returns scheduled hangouts
  "nextPageToken": "string (for more future events)",
  "previousPageToken": "string (for past events)"
}
```

**IMPORTANT BEHAVIOR:**
- The feed **ONLY returns hangouts with timestamps** (scheduled events)
- Hangouts created without `timeInfo` will **NOT appear in the feed**
- `needsDay` array is deprecated and always returns empty
- Only current, in-progress, and future scheduled hangouts are included
- Past events can be accessed via `endingBefore` pagination parameter

**ETag Caching Flow:**
1. Cheap check: GetItem Group metadata + membership (2 RCU)
2. Calculate ETag: `"{groupId}-{lastHangoutModified.millis}"`
3. **If ETag matches** `If-None-Match`: Return `304 Not Modified` (saves expensive queries!)
4. **If ETag different**: Execute expensive feed queries

**Feed Query Flow (when ETag miss):**
1. Determine direction:
   - `endingBefore` provided → Past events
   - Default or `startingAfter` → Current/Future events
2. **For current/future** (parallel):
   - Query future events (startTimestamp > now)
   - Query in-progress events (endTimestamp > now)
   - Merge results
3. **For past**:
   - Query past events (endTimestamp < now)
4. Hydrate feed (see hydration below)
5. All results go to `withDay` (only scheduled hangouts are queried)
6. `needsDay` is always empty (deprecated)
7. Generate pagination tokens

**CRITICAL:** Only hangouts with timestamps are returned. Hangouts created without `timeInfo` are excluded from the feed.

**Feed Hydration (Mixed Content):**
1. **First Pass**: Identify all hangoutIds that are part of series
2. **Second Pass**: Build feed items
   - SeriesPointer → SeriesSummaryDTO (with parts)
   - HangoutPointer (standalone) → HangoutSummaryDTO
   - Skip HangoutPointers already in series

**Database Operations:**
- **ETag check**: 2 GetItem (group + membership)
- **ETag hit**: Return 304 (0 additional queries)
- **ETag miss**:
  - **Current/Future**: 2 parallel GSI queries (FutureEventsIndex + InProgressEventsIndex)
  - **Past**: 1 GSI query (PastEventsIndex)

**Pagination:**
- Uses opaque tokens (Base64-encoded JSON)
- Token contains: lastEventId, lastTimestamp, isForward flag
- Translates between service layer format and repository format

**Errors:**
- `403 FORBIDDEN`: User not a member
- `404 NOT_FOUND`: Group doesn't exist

**Authorization:** User must be a member

**Performance Notes:**
- ETag check is **cheap** (2 RCU) vs feed queries (4-10 RCU)
- Parallel queries reduce latency for current/future feed
- Denormalized HangoutPointer contains all display data (no hangout canonical lookups)

---

### 11. Get Group Feed Items (Polymorphic)
**Endpoint:** `GET /groups/{groupId}/feed-items`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)
**Query Parameters:**
- `limit` (integer, default=10, min=1, max=50)
- `startToken` (string, pagination token, optional)

**Response:** `200 OK`
```json
{
  "items": [
    {
      "itemType": "POLL|DATE_POLL|LOCATION_POLL",
      "hangoutId": "uuid",
      "hangoutTitle": "string",
      "pollData": { /* poll-specific data */ }
    }
  ],
  "nextPageToken": "string"
}
```

**Internal Flow:**
1. Extract userId from JWT
2. Delegate to `GroupFeedService.getFeedItems()`
3. Uses **backend loop aggregator pattern**:
   - Loops through upcoming events
   - Extracts actionable items (polls, undecided attributes)
   - Continues until enough items to fill page
4. Returns polymorphic items with pagination

**Database Operations:**
- Handled by GroupFeedService (not in current scope)

**Errors:**
- `403 UNAUTHORIZED`: User not a member

**Authorization:** User must be a member

**Important Notes:**
- Different from `/feed` - this returns actionable items, not events
- Backend loop may return slightly more than `limit` (returns all items from final batch)

---

### 12. Generate Invite Code
**Endpoint:** `POST /groups/{groupId}/invite-code`
**Authentication:** Required (JWT)
**Path Parameters:** `groupId` (UUID format validated)

**Response:** `200 OK`
```json
{
  "inviteCode": "abc123xy (8 chars)",
  "shareUrl": "https://app.inviter.com/join-group/abc123xy"
}
```

**Internal Flow:**
1. Extract userId from JWT
2. Verify group exists
3. Verify user is a member
4. **Check for existing active code** (idempotent)
5. If exists: Return existing code
6. If not: Generate new 8-char code (collision-resistant)
7. Create InviteCode entity
8. Save to database
9. Return code + shareable URL

**Database Operations:**
- **GetItem**: 1 (group)
- **GetItem**: 1 (membership check)
- **Query**: InviteCodeIndex GSI for active codes (gsi1pk=GROUP#{groupId})
- **PutItem**: 1 (if new code needed)

**InviteCode Entity Details:**
- **Canonical record**: PK=INVITE_CODE#{id}, SK=METADATA
- **GSI for code lookup**: gsi3pk=CODE#{code}, gsi3sk=METADATA
- **GSI for group listing**: gsi1pk=GROUP#{groupId}, gsi1sk=CREATED#{timestamp}
- Fields: code, groupId, groupName, createdBy, isActive, expiresAt, usages[]

**Idempotency:**
- Checks `findActiveCodeForGroup()` first
- Reuses existing active code instead of creating duplicate
- Prevents code proliferation

**Errors:**
- `403 UNAUTHORIZED`: User not a member
- `404 NOT_FOUND`: Group doesn't exist

**Authorization:** User must be a member (any member, not just admins)

---

### 13. Get Group Preview by Invite Code (Public)
**Endpoint:** `GET /groups/invite/{inviteCode}`
**Authentication:** NOT REQUIRED (public endpoint)
**Path Parameters:** `inviteCode` (string)

**Response:** `200 OK` or `429 Too Many Requests`
```json
{
  "isPrivate": boolean,
  "groupName": "string (only if public group)",
  "mainImagePath": "string (only if public group)"
}
```

**Rate Limiting:**
- 60 requests/hour per IP address
- 100 requests/hour per invite code
- Uses Caffeine in-memory cache
- Returns `429` if limit exceeded

**Internal Flow:**
1. Extract client IP (handles X-Forwarded-For from API Gateway)
2. **Check rate limits** (IP + code)
3. If exceeded: Return 429
4. Find invite code via InviteCodeIndex GSI
5. Get group details
6. **Privacy filtering**:
   - Public groups: Return name and image
   - Private groups: Return only isPrivate=true, omit name/image

**Database Operations:**
- **Query**: InviteCodeIndex GSI (gsi3pk=CODE#{code})
- **GetItem**: 1 (group metadata)

**Errors:**
- `404 NOT_FOUND`: Invalid invite code
- `429 RATE_LIMIT_EXCEEDED`: Too many requests

**Authorization:** None (public endpoint)

**Privacy Notes:**
- Private group names/images are NOT exposed
- Only isPrivate flag is returned for private groups
- Null fields are omitted from JSON response (`@JsonInclude(NON_NULL)`)

---

### 14. Join Group by Invite Code
**Endpoint:** `POST /groups/invite/join`
**Authentication:** Required (JWT)
**Request Body:**
```json
{
  "inviteCode": "string (required)"
}
```

**Response:** `200 OK`
```json
{
  "groupId": "uuid",
  "groupName": "string",
  "isPublic": boolean,
  "userRole": "MEMBER",
  "joinedAt": "ISO-8601 timestamp",
  "createdAt": "ISO-8601 timestamp",
  "mainImagePath": "string",
  "backgroundImagePath": "string",
  "userMainImagePath": "string"
}
```

**Internal Flow:**
1. Extract userId from JWT
2. Find invite code via InviteCodeIndex GSI
3. **Validate code is usable** (`isUsable()` checks isActive and expiration)
4. Get group details
5. **Check if already a member** (idempotent - returns existing membership)
6. Get user details for denormalization
7. Create GroupMembership (role=MEMBER)
8. Denormalize group images and user image to membership
9. Save membership
10. **Record usage** on invite code (adds userId to usages array)
11. If single-use code: Auto-deactivate

**Database Operations:**
- **Query**: InviteCodeIndex GSI (find code)
- **GetItem**: 1 (group), 1 (check existing membership), 1 (user)
- **PutItem**: 1 (new membership if needed)
- **PutItem**: 1 (update invite code usage)

**Invite Code Validation:**
- Must be `isActive = true`
- Must not be expired (`expiresAt` not passed)
- Single-use codes auto-deactivate after first use

**Errors:**
- `400 VALIDATION_ERROR`: Empty invite code
- `404 NOT_FOUND`: Invalid code or code no longer valid
- `404 NOT_FOUND`: Group not found

**Authorization:** Authenticated users only

**Idempotency:**
- If user already a member, returns existing membership (doesn't fail)
- Still records usage on invite code

**Important Notes:**
- New members always join as MEMBER (not ADMIN)
- Usage tracking maintains list of userIds who joined
- Single-use codes automatically deactivate

---

## Data Models

### Group (Canonical Record)
**DynamoDB Keys:**
- PK: `GROUP#{groupId}`
- SK: `METADATA`

**Attributes:**
```java
String groupId;           // UUID
String groupName;         // 1-100 chars
boolean isPublic;         // Access control
String mainImagePath;     // S3 path
String backgroundImagePath; // S3 path
Instant lastHangoutModified; // For ETag generation
Instant createdAt;        // Auto-managed by BaseItem
Instant updatedAt;        // Auto-managed by BaseItem
String itemType = "GROUP"; // Type discriminator
```

---

### GroupMembership
**DynamoDB Keys:**
- PK: `GROUP#{groupId}`
- SK: `USER#{userId}`
- GSI1 (UserGroupIndex):
  - gsi1pk: `USER#{userId}`
  - gsi1sk: `GROUP#{groupId}`
- GSI2 (CalendarTokenIndex):
  - gsi2pk: `TOKEN#{calendarToken}` (only if calendar subscribed)

**Attributes:**
```java
String groupId;
String userId;
String groupName;            // Denormalized from Group
String role;                 // "ADMIN" or "MEMBER"
String groupMainImagePath;   // Denormalized from Group
String groupBackgroundImagePath; // Denormalized from Group
String userMainImagePath;    // Denormalized from User
String calendarToken;        // UUID for calendar subscriptions
Instant createdAt;           // Join timestamp
Instant updatedAt;
String itemType = "GROUP_MEMBERSHIP";
```

**Denormalization Strategy:**
- Group data (name, images) stored on membership for efficient user groups query
- User image stored on membership for efficient group member display
- When source data changes, batch updates maintain consistency

---

### InviteCode (Canonical Record)
**DynamoDB Keys:**
- PK: `INVITE_CODE#{inviteCodeId}`
- SK: `METADATA`
- GSI1 (UserGroupIndex):
  - gsi1pk: `GROUP#{groupId}`
  - gsi1sk: `CREATED#{createdAt}`
- GSI3 (InviteCodeIndex):
  - gsi3pk: `CODE#{code}`
  - gsi3sk: `METADATA`

**Attributes:**
```java
String inviteCodeId;      // UUID
String code;              // 8-char alphanumeric (e.g., "abc123xy")
String groupId;           // Which group this belongs to
String groupName;         // Denormalized for display
String createdBy;         // userId who generated it
Instant createdAt;        // When generated
String deactivatedBy;     // userId who disabled it (optional)
Instant deactivatedAt;    // When disabled (optional)
boolean isSingleUse;      // true = disable after first use
Instant expiresAt;        // Optional expiration timestamp
boolean isActive;         // Can be used?
String deactivationReason; // Why disabled (optional)
List<String> usages;      // User IDs who joined via this code
String itemType = "InviteCode";
```

**Lifecycle:**
- Created with `isActive=true`, `isSingleUse=false`
- Usage recorded in `usages` array
- Single-use codes auto-deactivate after first use
- Can expire based on `expiresAt`
- Can be manually deactivated

---

### DTOs

#### CreateGroupRequest
```java
@NotBlank String groupName; // 1-100 chars
@NotNull Boolean isPublic;
String mainImagePath;       // Optional
String backgroundImagePath; // Optional
```

#### UpdateGroupRequest
```java
@Size(1-100) String groupName;    // Optional
Boolean isPublic;                  // Optional
String mainImagePath;              // Optional
String backgroundImagePath;        // Optional

boolean hasUpdates(); // At least one field must be non-null
```

#### GroupDTO
```java
String groupId;
String groupName;
boolean isPublic;
String userRole;              // User's role in this group
Instant joinedAt;             // When user joined
Instant createdAt;            // When group created
String mainImagePath;
String backgroundImagePath;
String userMainImagePath;     // User's profile image
```

#### GroupMemberDTO
```java
String userId;
String userName;              // LIVE from User table
String mainImagePath;         // LIVE from User table
String role;                  // "ADMIN" or "MEMBER"
Instant joinedAt;
```

#### GroupFeedDTO
```java
String groupId;
List<FeedItem> withDay;       // Hangouts and series with timestamps
List<HangoutSummaryDTO> needsDay; // Hangouts without timestamps
String nextPageToken;         // For more future events
String previousPageToken;     // For past events
```

#### AddMemberRequest
```java
@Pattern("[0-9a-f-]{36}") String userId;  // Optional
String phoneNumber;                        // Optional
// Exactly one must be provided
```

#### InviteCodeResponse
```java
String inviteCode;  // 8-char code
String shareUrl;    // Full joinable URL
```

#### GroupPreviewDTO
```java
boolean isPrivate;
String groupName;       // Null for private groups
String mainImagePath;   // Null for private groups
```

#### JoinGroupRequest
```java
@NotBlank String inviteCode;
```

---

## Database Schema

### Single Table Design (InviterTable)

**CRITICAL:** All database operations must follow the patterns defined in `DYNAMODB_DESIGN_GUIDE.md`. Failure to do so will cause severe performance issues.

#### Key Schema for Group-Related Items

| Item Type | PK (Partition Key) | SK (Sort Key) | Type | Purpose |
|-----------|-------------------|---------------|------|---------|
| Group (Canonical) | `GROUP#{groupId}` | `METADATA` | Canonical | Authoritative group record |
| GroupMembership | `GROUP#{groupId}` | `USER#{userId}` | Link Record | User-group relationship with denormalized data |
| HangoutPointer | `GROUP#{groupId}` | `HANGOUT#{hangoutId}` | Pointer | Denormalized hangout summary for group feed |
| SeriesPointer | `GROUP#{groupId}` | `SERIES#{seriesId}` | Pointer | Denormalized series summary for group feed |
| InviteCode | `INVITE_CODE#{id}` | `METADATA` | Canonical | Invite code record |

#### Access Patterns

| Pattern | Index | Key Condition | Example | Performance Note |
|---------|-------|---------------|---------|------------------|
| Get group metadata | Main | PK=GROUP#{id}, SK=METADATA | Get group details | 1 RCU |
| Get user's membership | Main | PK=GROUP#{id}, SK=USER#{userId} | Check if user in group | 1 RCU |
| List group members | Main | PK=GROUP#{id}, SK begins_with USER# | Get all members | ~0.5 RCU per member |
| **Get group feed** | **Main** | **PK=GROUP#{id}, SK begins_with HANGOUT#** | **Display group hangouts** | **USE POINTERS ONLY - NEVER FETCH CANONICAL** |
| List user's groups | UserGroupIndex | gsi1pk=USER#{userId} | Get all groups for user | Single GSI query (denormalized) |
| Find by calendar token | CalendarTokenIndex | gsi2pk=TOKEN#{token} | Calendar subscription lookup | 1 RCU |
| Find invite code | InviteCodeIndex | gsi3pk=CODE#{code} | Lookup by invite code | 1 RCU |
| List group's codes | UserGroupIndex | gsi1pk=GROUP#{id}, gsi1sk begins_with CREATED# | List all codes for group | ~0.5 RCU per code |

#### Indexes

**Main Table:**
- PK: Partition key (GROUP#{id}, INVITE_CODE#{id}, etc.)
- SK: Sort key (METADATA, USER#{id}, HANGOUT#{id}, etc.)

**UserGroupIndex (GSI1):**
- gsi1pk: USER#{userId} or GROUP#{groupId}
- gsi1sk: GROUP#{groupId} or CREATED#{timestamp}
- Purpose: User → Groups query, Group → Invite codes query

**CalendarTokenIndex (GSI2):**
- gsi2pk: TOKEN#{calendarToken}
- Purpose: Calendar subscription token lookup

**InviteCodeIndex (GSI3):**
- gsi3pk: CODE#{code}
- gsi3sk: METADATA
- Purpose: Public invite code lookup

#### Denormalization Strategy

**CRITICAL RULE:** Canonical First, Pointers Second (see `DYNAMODB_DESIGN_GUIDE.md` Section 5)

| Source | Denormalized To | Update Trigger | Update Method | Why Denormalized |
|--------|-----------------|----------------|---------------|------------------|
| Group.groupName | GroupMembership.groupName | Group name change | `updateMembershipGroupNames()` - batched TransactWriteItems | UserGroupIndex query returns all data without additional lookups |
| Group.mainImagePath | GroupMembership.groupMainImagePath | Group image change | `updateMembershipGroupImagePaths()` - batched TransactWriteItems | UserGroupIndex query returns all data without additional lookups |
| Group.backgroundImagePath | GroupMembership.groupBackgroundImagePath | Group image change | `updateMembershipGroupImagePaths()` - batched TransactWriteItems | UserGroupIndex query returns all data without additional lookups |
| User.mainImagePath | GroupMembership.userMainImagePath | User image change | `updateMembershipUserImagePath()` - batched TransactWriteItems | UserGroupIndex query returns all data without additional lookups |

**Denormalization Update Pattern:**
1. **Update canonical record first** (Group or User)
2. **Query all affected pointer records** (GroupMembership)
3. **Batch updates** in groups of 25 (conservative - DynamoDB supports 100)
4. **Use TransactWriteItems** for atomic consistency
5. **Multiple batches** for large groups (>25 members)

**N+1 Query Prevention:**
- WITHOUT denormalization: 1 GSI query + N GetItem operations for group names
- WITH denormalization: 1 GSI query returns ALL data
- Performance improvement: ~100x faster for users in 10+ groups

#### Critical Database "Gotchas"

**⚠️ NEVER DO THESE (from DYNAMODB_DESIGN_GUIDE.md):**

1. **NEVER call a "get detail" service method inside a loop** (N+1 anti-pattern)
   - BAD: Loop through HangoutPointers and call `hangoutService.getHangout(id)`
   - GOOD: Use denormalized data on HangoutPointer

2. **NEVER fetch canonical Hangout records when displaying feeds**
   - BAD: Query pointers, then fetch full Hangout for each
   - GOOD: Display using pointer data only

3. **ALWAYS update all pointer records** after canonical record changes
   - Stale pointer data is a critical bug
   - Use batched TransactWriteItems for consistency

4. **VERIFY GSI keys are populated** on all pointer writes
   - `gsi1pk` and `gsi1sk` must be set for UserGroupIndex queries
   - `gsi2pk` must be set for CalendarTokenIndex queries
   - Missing GSI keys = invisible records in queries

---

## Authorization Rules

| Endpoint | Rule | Implementation |
|----------|------|----------------|
| Create Group | Authenticated user | JWT required |
| Get User's Groups | Authenticated user | JWT required |
| Get Group Details | User must be member | `findMembership()` must succeed |
| Update Group | User must be member | `findMembership()` must succeed |
| Delete Group | User must be ADMIN | `membership.isAdmin()` must be true |
| Add Member (private) | User must be member | `groupRepository.isUserMemberOfGroup()` |
| Add Member (public) | Authenticated user | JWT required only |
| Remove Member | Self or admin | `userId == removedBy` (admin check not implemented) |
| Leave Group | User must be member | `findMembership()` must succeed |
| Get Members | User must be member | `isUserInGroup()` must be true |
| Get Feed | User must be member | `isUserInGroup()` must be true |
| Get Feed Items | User must be member | Delegated to GroupFeedService |
| Generate Invite Code | User must be member | `isUserMemberOfGroup()` must be true |
| Preview by Code | Public | Rate limited only |
| Join by Code | Authenticated user | JWT required + code must be active |

**Authorization Helper Methods:**
- `isUserInGroup(userId, groupId)`: Checks membership existence
- `findMembership(groupId, userId)`: Gets membership with role
- `membership.isAdmin()`: Checks ADMIN role

---

## Error Handling

### Error Response Format
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "timestamp": 1234567890
}
```

### HTTP Status Codes

| Status | Error Type | When Thrown |
|--------|------------|-------------|
| 400 | VALIDATION_ERROR | Invalid input, missing required fields, constraint violations |
| 403 | UNAUTHORIZED | No JWT token, not a member, not an admin |
| 404 | NOT_FOUND | Group/user/membership/invite code doesn't exist |
| 404 | USER_NOT_FOUND | Specific user not found |
| 409 | TRANSACTION_FAILED | DynamoDB transaction failed (rare) |
| 429 | RATE_LIMIT_EXCEEDED | Too many invite preview requests |
| 500 | REPOSITORY_ERROR | Database error |
| 500 | DATABASE_ERROR | DynamoDB service error |
| 500 | INTERNAL_ERROR | Unexpected error |

### Common Exceptions

**ValidationException:**
- Empty group name
- Group name > 100 chars
- isPublic missing
- User already a member
- No fields provided for update
- Invalid userId/phoneNumber combination

**UnauthorizedException:**
- No authenticated user (JWT missing)
- User not in group
- User not admin (for delete)
- Non-member adding to private group

**ResourceNotFoundException:**
- Group not found
- User not found
- Membership not found
- Invite code not found

**UserNotFoundException:**
- Specific case when user lookup fails

**RepositoryException:**
- DynamoDB transaction failed
- Query/write operation failed
- Wraps underlying DynamoDbException

---

## Performance Optimizations

### 1. Denormalization
**Problem:** N+1 queries when listing user's groups
**Solution:** Store groupName and image paths on GroupMembership
**Impact:** Single GSI query instead of 1 + N GetItem operations

### 2. ETag Caching (Feed Endpoint)
**Problem:** Expensive feed queries on every request
**Solution:** Cheap metadata check (2 RCU) with ETag comparison
**Impact:**
- ETag hit: 304 response, 2 RCU total
- ETag miss: Full feed query, 4-10 RCU
- Cache hit rate: 70-90% expected

**ETag Format:** `"{groupId}-{lastHangoutModified.millis}"`

### 3. Parallel Queries
**Where:** Get Group Feed (current/future events)
**Implementation:** CompletableFuture for future + in-progress queries
**Impact:** ~2x faster than sequential queries

### 4. Atomic Transactions
**Where:** Create group, update denormalized data
**Why:** Ensure consistency between Group and GroupMembership
**Cost:** Slightly higher latency but prevents partial writes

### 5. Batch Operations
**Where:** Delete group, update denormalized data
**Implementation:** BatchWriteItem (25 per batch) or TransactWriteItems (25 per batch)
**Impact:** Reduces number of API calls for large groups

### 6. Rate Limiting (Invite Preview)
**Implementation:** Caffeine in-memory cache
**Limits:**
- 60 requests/hour per IP
- 100 requests/hour per code
**Why:** Prevents scraping and brute-force attacks on public endpoint

### 7. Async S3 Deletion
**Where:** Update group images
**Implementation:** `s3Service.deleteImageAsync()` - fire-and-forget
**Impact:** Doesn't block API response for cleanup operations

---

## Integration Testing Guide

### Test Data Setup

**Prerequisite Users:**
```java
User admin = createUser("admin@test.com");
User member1 = createUser("member1@test.com");
User member2 = createUser("member2@test.com");
User outsider = createUser("outsider@test.com");
```

**Prerequisite Groups:**
```java
// Public group with admin as creator
Group publicGroup = createGroup("Public Group", true, admin);

// Private group with admin as creator
Group privateGroup = createGroup("Private Group", false, admin);
addMember(privateGroup, member1);
```

### Test Scenarios

#### 1. Group Creation
```java
@Test
void createGroup_WithValidData_ReturnsGroupDTO() {
    // Given
    CreateGroupRequest request = new CreateGroupRequest("Test Group", true);
    String token = getJwtToken(admin);

    // When
    Response response = post("/groups", request, token);

    // Then
    assertThat(response.status()).isEqualTo(201);
    GroupDTO group = response.body(GroupDTO.class);
    assertThat(group.getGroupName()).isEqualTo("Test Group");
    assertThat(group.getUserRole()).isEqualTo("ADMIN");

    // Verify database
    assertGroupExists(group.getGroupId());
    assertMembershipExists(group.getGroupId(), admin.getId());
}
```

#### 2. Authorization Tests
```java
@Test
void getGroup_UserNotMember_Returns403() {
    // Given
    String token = getJwtToken(outsider);

    // When
    Response response = get("/groups/" + privateGroup.getId(), token);

    // Then
    assertThat(response.status()).isEqualTo(403);
}

@Test
void deleteGroup_UserNotAdmin_Returns403() {
    // Given
    addMember(publicGroup, member1); // member1 is MEMBER, not ADMIN
    String token = getJwtToken(member1);

    // When
    Response response = delete("/groups/" + publicGroup.getId(), token);

    // Then
    assertThat(response.status()).isEqualTo(403);
}
```

#### 3. Denormalization Consistency
```java
@Test
void updateGroupName_UpdatesAllMemberships() {
    // Given
    addMember(publicGroup, member1);
    addMember(publicGroup, member2);
    UpdateGroupRequest request = new UpdateGroupRequest("New Name", null);
    String token = getJwtToken(admin);

    // When
    put("/groups/" + publicGroup.getId(), request, token);

    // Then
    List<GroupMembership> memberships = getAllMemberships(publicGroup.getId());
    assertThat(memberships).allMatch(m ->
        m.getGroupName().equals("New Name")
    );
}
```

#### 4. Last Member Leave
```java
@Test
void leaveGroup_LastMember_DeletesGroup() {
    // Given
    Group soloGroup = createGroup("Solo Group", true, admin);
    String token = getJwtToken(admin);

    // When
    post("/groups/" + soloGroup.getId() + "/leave", null, token);

    // Then
    assertGroupDeleted(soloGroup.getId());
}
```

#### 5. Invite Code Flow
```java
@Test
void inviteCodeFlow_PublicGroup_Success() {
    // Step 1: Generate code
    String adminToken = getJwtToken(admin);
    Response genResponse = post("/groups/" + publicGroup.getId() + "/invite-code", null, adminToken);
    InviteCodeResponse codeResp = genResponse.body(InviteCodeResponse.class);

    // Step 2: Preview (unauthenticated)
    Response previewResponse = get("/groups/invite/" + codeResp.getInviteCode(), null);
    GroupPreviewDTO preview = previewResponse.body(GroupPreviewDTO.class);
    assertThat(preview.getGroupName()).isEqualTo("Public Group");

    // Step 3: Join
    String outsiderToken = getJwtToken(outsider);
    JoinGroupRequest joinReq = new JoinGroupRequest(codeResp.getInviteCode());
    Response joinResponse = post("/groups/invite/join", joinReq, outsiderToken);
    GroupDTO joined = joinResponse.body(GroupDTO.class);
    assertThat(joined.getUserRole()).isEqualTo("MEMBER");

    // Verify membership
    assertMembershipExists(publicGroup.getId(), outsider.getId());
}
```

#### 6. ETag Caching
```java
@Test
void getFeed_WithMatchingETag_Returns304() {
    // Given
    String token = getJwtToken(member1);
    Response firstResponse = get("/groups/" + publicGroup.getId() + "/feed", token);
    String etag = firstResponse.header("ETag");

    // When - Request again with same ETag
    Response cachedResponse = get("/groups/" + publicGroup.getId() + "/feed",
        token,
        Map.of("If-None-Match", etag));

    // Then
    assertThat(cachedResponse.status()).isEqualTo(304);
}

@Test
void getFeed_AfterHangoutCreated_ETagChanges() {
    // Given
    String token = getJwtToken(admin);
    Response firstResponse = get("/groups/" + publicGroup.getId() + "/feed", token);
    String oldEtag = firstResponse.header("ETag");

    // When - Create hangout (changes lastHangoutModified)
    createHangout(publicGroup.getId(), admin);
    Response newResponse = get("/groups/" + publicGroup.getId() + "/feed", token);
    String newEtag = newResponse.header("ETag");

    // Then
    assertThat(newEtag).isNotEqualTo(oldEtag);
}
```

#### 7. Rate Limiting
```java
@Test
void invitePreview_ExceedsRateLimit_Returns429() {
    // Given
    String code = generateInviteCode(publicGroup.getId(), admin);

    // When - Make 61 requests from same IP
    for (int i = 0; i < 61; i++) {
        Response response = get("/groups/invite/" + code, null,
            Map.of("X-Forwarded-For", "192.168.1.1"));

        if (i < 60) {
            assertThat(response.status()).isEqualTo(200);
        } else {
            assertThat(response.status()).isEqualTo(429);
        }
    }
}
```

### Database Verification Helpers

```java
void assertGroupExists(String groupId) {
    Group group = groupRepository.findById(groupId)
        .orElseThrow(() -> new AssertionError("Group not found: " + groupId));
}

void assertMembershipExists(String groupId, String userId) {
    GroupMembership membership = groupRepository.findMembership(groupId, userId)
        .orElseThrow(() -> new AssertionError("Membership not found"));
}

void assertGroupDeleted(String groupId) {
    assertThat(groupRepository.findById(groupId)).isEmpty();
}

List<GroupMembership> getAllMemberships(String groupId) {
    return groupRepository.findMembersByGroupId(groupId);
}
```

### Edge Cases to Test

1. **Empty group name** → 400
2. **Group name > 100 chars** → 400
3. **Update with no fields** → 400
4. **Add member with both userId and phoneNumber** → 400
5. **Add member with neither userId nor phoneNumber** → 400
6. **Add already-existing member** → 400
7. **Join with expired invite code** → 404
8. **Join with deactivated invite code** → 404
9. **Delete group with 100+ members** → Test batching
10. **Update group name with 100+ members** → Test transaction batching
11. **Concurrent group deletion** → Test transaction isolation
12. **Invalid UUID format in path** → 400

---

## Related Documentation

**MUST READ FIRST:**
- `DYNAMODB_DESIGN_GUIDE.md` - **Critical patterns for database operations** (READ THIS!)

**Then read:**
- `DATABASE_ARCHITECTURE_CRITICAL.md` - Single-table design patterns
- `context/GROUP_CRUD_CONTEXT.md` - Business requirements
- `context/ATTRIBUTE_ADDITION_GUIDE.md` - Adding new fields to User/Group/Hangout
- `context/UNIT_TESTING_PATTERNS.md` - Testing guidelines

**Common Anti-Patterns to Avoid:**
1. Fetching canonical records in loops → Use pointer data
2. Missing denormalization updates → Stale data bugs
3. Forgetting GSI keys → Invisible records in queries
4. N+1 queries → Use denormalized data from single query

---

## Glossary

**GSI** - Global Secondary Index (DynamoDB)
**RCU** - Read Capacity Unit (DynamoDB)
**WCU** - Write Capacity Unit (DynamoDB)
**ETag** - Entity Tag (HTTP caching mechanism)
**Denormalization** - Storing duplicate data for query efficiency
**Atomic Transaction** - All-or-nothing database operation
**Feed Hydration** - Converting database records into rich DTOs
**N+1 Query** - Anti-pattern where 1 query triggers N additional queries
