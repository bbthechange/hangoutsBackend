# Polls API Deep Dive

**Version:** 1.0
**Last Updated:** 2025-11-16
**Base Path:** `/hangouts/{eventId}/polls`

---

## Table of Contents

1. [Overview](#overview)
2. [Database Architecture](#database-architecture)
3. [API Endpoints](#api-endpoints)
4. [Data Models](#data-models)
5. [Error Handling](#error-handling)
6. [Authorization](#authorization)
7. [Pointer Synchronization](#pointer-synchronization)
8. [Testing Considerations](#testing-considerations)

---

## Overview

The Polls API provides functionality for creating and managing polls within hangouts/events. Users can:
- Create polls with multiple options
- Vote on poll options (single or multiple choice)
- View poll results with vote counts
- Manage poll options dynamically
- Delete polls and options (hosts only)

**Key Characteristics:**
- All poll data is stored in the InviterTable using the **Item Collection Pattern**
- Poll data is **denormalized** into HangoutPointer records for efficient group feed queries
- Vote counts are calculated **at runtime** (not stored)
- Supports both **single-choice** and **multiple-choice** polls
- Uses **DynamoDB transactions** for atomic operations

---

## Database Architecture

### Single-Table Design (InviterTable)

All poll data resides within the `EVENT#{eventId}` partition using the **Item Collection Pattern**, which allows retrieving all poll-related data in a single query.

### Key Patterns

| Entity | PK (Partition Key) | SK (Sort Key) | Example |
|--------|-------------------|---------------|---------|
| **Poll** | `EVENT#{eventId}` | `POLL#{pollId}` | `EVENT#123-456 / POLL#abc-def` |
| **PollOption** | `EVENT#{eventId}` | `POLL#{pollId}#OPTION#{optionId}` | `EVENT#123-456 / POLL#abc-def#OPTION#xyz-123` |
| **Vote** | `EVENT#{eventId}` | `POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}` | `EVENT#123-456 / POLL#abc-def#VOTE#user-789#OPTION#xyz-123` |

**Key Factory Methods (InviterKeyFactory):**
```java
getEventPk(eventId)                        // EVENT#{eventId}
getPollSk(pollId)                          // POLL#{pollId}
getPollOptionSk(pollId, optionId)          // POLL#{pollId}#OPTION#{optionId}
getVoteSk(pollId, userId, optionId)        // POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}
```

### Item Collection Pattern Benefits

1. **Single Query Efficiency**: All polls, options, and votes for an event retrieved in one query
2. **Hierarchical Filtering**: Sort key prefixes enable efficient filtering
   - `begins_with("POLL#")` → Get all poll data
   - `begins_with("POLL#{pollId}")` → Get specific poll + options + votes
   - `begins_with("POLL#{pollId}#OPTION#")` → Get all options for a poll
3. **Consistent Performance**: Query cost is proportional to data size, not table size

### Type Detection Helpers

```java
InviterKeyFactory.isPollItem(sk)     // Returns true if SK = POLL#{pollId}
InviterKeyFactory.isPollOption(sk)   // Returns true if SK contains #OPTION# but not #VOTE#
InviterKeyFactory.isVoteItem(sk)     // Returns true if SK contains #VOTE#
```

---

## API Endpoints

### 1. Create Poll

**Endpoint:** `POST /hangouts/{eventId}/polls`

**Description:** Creates a new poll for a hangout. Only event hosts can create polls.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format, validated with pattern `[0-9a-f-]{36}`

#### Request Body (`CreatePollRequest`)
```json
{
  "title": "What time works best?",
  "description": "Choose your preferred meeting time",
  "multipleChoice": false,
  "options": ["Morning", "Afternoon", "Evening"]
}
```

**Field Validations:**
- `title`: Required, 1-200 characters, trimmed
- `description`: Optional, max 1000 characters, trimmed
- `multipleChoice`: Boolean, defaults to `false`
- `options`: Optional list of strings

#### Response
**Status:** `201 CREATED`

**Body:** `Poll` object
```json
{
  "pollId": "abc-def-123",
  "eventId": "event-456",
  "title": "What time works best?",
  "description": "Choose your preferred meeting time",
  "multipleChoice": false,
  "isActive": true,
  "pk": "EVENT#event-456",
  "sk": "POLL#abc-def-123",
  "createdAt": 1699999999,
  "updatedAt": 1699999999
}
```

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:53-65`)
   - Retrieves hangout using `hangoutRepository.getHangoutDetailData(eventId)`
   - Throws `EventNotFoundException` if hangout doesn't exist
   - Checks `authorizationService.canUserEditHangout(userId, hangout)`
   - Throws `UnauthorizedException` if user cannot edit

2. **Poll Creation** (`PollServiceImpl:68-69`)
   - Creates `Poll` object with:
     - Auto-generated UUID for `pollId`
     - Keys: `PK=EVENT#{eventId}`, `SK=POLL#{pollId}`
     - `isActive=true`
   - Saves poll using `hangoutRepository.savePoll(poll)`

3. **Option Creation** (`PollServiceImpl:72-77`)
   - Iterates through `request.getOptions()` if provided
   - Creates `PollOption` for each with:
     - Auto-generated UUID for `optionId`
     - Keys: `PK=EVENT#{eventId}`, `SK=POLL#{pollId}#OPTION#{optionId}`
   - Saves each option individually using `hangoutRepository.savePollOption(option)`

4. **Pointer Synchronization** (`PollServiceImpl:80`)
   - Calls `updatePointersWithPolls(eventId)` to denormalize poll data
   - Updates all `HangoutPointer` records for associated groups
   - Updates group timestamps for ETag invalidation

#### Database Operations

**DynamoDB Operations:**
1. `GetHangoutDetailData`: Query to retrieve hangout (for authorization)
2. `PutItem`: Save Poll record
   - Table: `InviterTable`
   - Item: Poll with `pk=EVENT#{eventId}`, `sk=POLL#{pollId}`
3. `PutItem` (per option): Save each PollOption
   - Table: `InviterTable`
   - Item: PollOption with `pk=EVENT#{eventId}`, `sk=POLL#{pollId}#OPTION#{optionId}`
4. `BatchWriteItem`: Update pointer records (see Pointer Synchronization section)

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `400 BAD_REQUEST` | `VALIDATION_ERROR` | Invalid title length, description too long |
| `403 FORBIDDEN` | `UNAUTHORIZED` | User is not a host of the hangout |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event ID doesn't exist |
| `500 INTERNAL_SERVER_ERROR` | `REPOSITORY_ERROR` | Database operation failed |

#### Important Notes
- Poll options are created **individually**, not in a batch transaction
- Vote counts are NOT stored; they are calculated at runtime when polls are retrieved
- The `multipleChoice` flag determines voting behavior (see Vote API)

---

### 2. Get Event Polls

**Endpoint:** `GET /hangouts/{eventId}/polls`

**Description:** Retrieves all polls for an event with vote counts and user vote status.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format

#### Response
**Status:** `200 OK`

**Body:** Array of `PollWithOptionsDTO`
```json
[
  {
    "pollId": "poll-123",
    "title": "What time works best?",
    "description": "Choose your preferred meeting time",
    "multipleChoice": false,
    "totalVotes": 5,
    "options": [
      {
        "optionId": "opt-1",
        "text": "Morning",
        "voteCount": 2,
        "userVoted": true
      },
      {
        "optionId": "opt-2",
        "text": "Afternoon",
        "voteCount": 3,
        "userVoted": false
      }
    ]
  }
]
```

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:92-101`)
   - Retrieves hangout using `hangoutRepository.getHangoutDetailData(eventId)`
   - Checks `authorizationService.canUserViewHangout(userId, hangout)`
   - Throws `UnauthorizedException` if user cannot view

2. **Data Retrieval** (`PollServiceImpl:104`)
   - Single query: `hangoutRepository.getAllPollData(eventId)`
   - Returns ALL items with `pk=EVENT#{eventId}` and `sk` starting with `POLL#`
   - Includes polls, options, AND votes in one query (Item Collection Pattern)

3. **Data Transformation** (`PollServiceImpl:394-441`)
   - Separates `BaseItem` list into typed collections using SK pattern matching:
     - `isPollItem(sk)` → `List<Poll>`
     - `isPollOption(sk)` → `Map<pollId, List<PollOption>>`
     - `isVoteItem(sk)` → `Map<pollId, List<Vote>>`
   - **Runtime Vote Counting**: Groups votes by optionId and counts
   - Builds `PollWithOptionsDTO` hierarchy with:
     - Vote counts per option (calculated from vote list)
     - `userVoted` flag (checks if any vote belongs to current user)
     - Total votes (total count of vote records)

#### Database Operations

**DynamoDB Operations:**
1. `GetHangoutDetailData`: Query for authorization check
2. `Query`: Single query on `InviterTable`
   - KeyConditionExpression: `pk = :pk AND begins_with(sk, :sk_prefix)`
   - Values: `pk=EVENT#{eventId}`, `sk_prefix=POLL#`
   - Returns: All polls, options, and votes

**Performance Notes:**
- **Single query retrieves all poll data** for the event (not N+1 queries)
- Vote counts calculated in-memory (O(n) where n = number of votes)
- Efficient for events with typical poll counts (1-10 polls)

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `403 FORBIDDEN` | `UNAUTHORIZED` | User cannot view event |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |
| `500 INTERNAL_SERVER_ERROR` | `REPOSITORY_ERROR` | Database query failed |

#### Important Notes
- Returns empty array `[]` if event has no polls
- Vote counts are **calculated at runtime**, not stored in DynamoDB
- The `userVoted` flag only indicates if current user voted for that specific option

---

### 3. Get Poll Detail

**Endpoint:** `GET /hangouts/{eventId}/polls/{pollId}`

**Description:** Retrieves detailed information for a specific poll, including all votes with user IDs.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format
  - `pollId` (String, required): UUID format

#### Response
**Status:** `200 OK`

**Body:** `PollDetailDTO`
```json
{
  "pollId": "poll-123",
  "title": "What time works best?",
  "description": "Choose your preferred meeting time",
  "multipleChoice": false,
  "totalVotes": 5,
  "options": [
    {
      "optionId": "opt-1",
      "text": "Morning",
      "voteCount": 2,
      "userVoted": true,
      "votes": [
        {
          "userId": "user-123",
          "voteType": "YES"
        },
        {
          "userId": "user-456",
          "voteType": "YES"
        }
      ]
    }
  ]
}
```

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:113-122`)
   - Same as Get Event Polls

2. **Data Retrieval** (`PollServiceImpl:126`)
   - Single query: `hangoutRepository.getSpecificPollData(eventId, pollId)`
   - Returns items with `pk=EVENT#{eventId}` and `sk` starting with `POLL#{pollId}`
   - Includes the poll, all its options, AND all votes

3. **Data Transformation** (`PollServiceImpl:443-485`)
   - Similar to Get Event Polls but includes detailed vote information
   - Creates `VoteDTO` objects containing:
     - `userId`: Who voted
     - `voteType`: Their vote type (YES/NO/MAYBE)
   - Groups votes by option for hierarchical response

#### Database Operations

**DynamoDB Operations:**
1. `GetHangoutDetailData`: Authorization check
2. `Query`: Single query on `InviterTable`
   - KeyConditionExpression: `pk = :pk AND begins_with(sk, :sk_prefix)`
   - Values: `pk=EVENT#{eventId}`, `sk_prefix=POLL#{pollId}`
   - Returns: Poll + all options + all votes for that poll

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `400 BAD_REQUEST` | - | Poll not found (IllegalArgumentException) |
| `403 FORBIDDEN` | `UNAUTHORIZED` | User cannot view event |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |

#### Important Notes
- Exposes individual user IDs who voted (consider privacy implications)
- Vote counts still calculated at runtime
- Throws `IllegalArgumentException` if poll doesn't exist (caught by general error handler)

---

### 4. Vote on Poll

**Endpoint:** `POST /hangouts/{eventId}/polls/{pollId}/vote`

**Description:** Submits a vote on a poll option. Behavior differs for single vs. multiple choice polls.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format
  - `pollId` (String, required): UUID format

#### Request Body (`VoteRequest`)
```json
{
  "optionId": "option-123",
  "voteType": "YES"
}
```

**Field Validations:**
- `optionId`: Required, UUID format (36 character pattern)
- `voteType`: Must be "YES", "NO", or "MAYBE" (defaults to "YES")

#### Response
**Status:** `200 OK`

**Body:** `Vote` object
```json
{
  "eventId": "event-456",
  "pollId": "poll-123",
  "optionId": "option-789",
  "userId": "user-abc",
  "voteType": "YES",
  "pk": "EVENT#event-456",
  "sk": "POLL#poll-123#VOTE#user-abc#OPTION#option-789",
  "createdAt": 1699999999,
  "updatedAt": 1699999999
}
```

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:136-144`)
   - Verifies user can view the hangout
   - Users must have access to event to vote

2. **Poll Data Retrieval** (`PollServiceImpl:147-152`)
   - Fetches poll and all votes using `getSpecificPollData(eventId, pollId)`
   - Extracts poll settings (`multipleChoice` flag)
   - Filters to find user's existing votes

3. **Vote Logic - Single Choice** (`PollServiceImpl:163-174`)
   - If user has existing vote for a **different option**:
     - **Deletes old vote** using `hangoutRepository.deleteVote()`
     - Allows new vote to proceed
   - If user already voted for **same option**:
     - Throws `IllegalStateException` (400 error)

4. **Vote Logic - Multiple Choice** (`PollServiceImpl:176-183`)
   - Checks if user already voted for this **specific option**
   - Throws `IllegalStateException` if duplicate vote
   - Allows voting on multiple different options

5. **Vote Creation** (`PollServiceImpl:186-187`)
   - Creates `Vote` with keys: `pk=EVENT#{eventId}`, `sk=POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}`
   - Saves using `hangoutRepository.saveVote(newVote)`

6. **Pointer Synchronization** (`PollServiceImpl:190`)
   - Updates all pointer records with new vote data

#### Database Operations

**Single-Choice Poll:**
1. `GetHangoutDetailData`: Authorization
2. `Query`: Get poll + options + votes
3. `DeleteItem`: Delete old vote (if changing vote)
4. `PutItem`: Create new vote
5. `BatchWriteItem`: Update pointers

**Multiple-Choice Poll:**
1. `GetHangoutDetailData`: Authorization
2. `Query`: Get poll + options + votes
3. `PutItem`: Create new vote
4. `BatchWriteItem`: Update pointers

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `400 BAD_REQUEST` | - | User already voted for this option |
| `400 BAD_REQUEST` | `VALIDATION_ERROR` | Invalid optionId format or voteType |
| `403 FORBIDDEN` | `UNAUTHORIZED` | User cannot access event |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |

#### Important Notes

**Single-Choice Behavior:**
- User can change their vote by voting on a different option
- Previous vote is **automatically deleted**
- Voting on same option again is rejected

**Multiple-Choice Behavior:**
- User can vote on multiple options
- Each option can only be voted on once by the user
- No automatic deletion of other votes

**Vote Type:**
- Supports YES, NO, MAYBE for potential future use cases
- Currently not used in UI logic but stored for flexibility

---

### 5. Remove Vote

**Endpoint:** `DELETE /hangouts/{eventId}/polls/{pollId}/vote`

**Description:** Removes user's vote(s) from a poll.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format
  - `pollId` (String, required): UUID format
- **Query Parameters:**
  - `optionId` (String, optional): Specific option to remove vote from

#### Response
**Status:** `204 NO CONTENT`

**Body:** Empty

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:199-208`)
   - Verifies user can view hangout

2. **Find Existing Votes** (`PollServiceImpl:211-223`)
   - Queries poll data to find user's votes
   - Returns silently if no votes exist (idempotent)

3. **Delete Logic** (`PollServiceImpl:226-233`)
   - **If `optionId` provided**: Delete only that specific vote
   - **If `optionId` is null**: Delete ALL user's votes on the poll (for single-choice cleanup)

4. **Pointer Synchronization** (`PollServiceImpl:236`)
   - Updates pointer records to reflect vote removal

#### Database Operations

1. `GetHangoutDetailData`: Authorization
2. `Query`: Get poll data to find votes
3. `DeleteItem`: Delete specific vote(s)
   - Single delete if `optionId` provided
   - Multiple deletes if removing all votes
4. `BatchWriteItem`: Update pointers

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `403 FORBIDDEN` | `UNAUTHORIZED` | User cannot access event |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |

#### Important Notes
- **Idempotent**: Returns success even if vote doesn't exist
- Users can only remove their own votes (enforced by userId from JWT)
- No way to remove other users' votes (even for hosts)

---

### 6. Delete Poll

**Endpoint:** `DELETE /hangouts/{eventId}/polls/{pollId}`

**Description:** Deletes a poll and all associated data (options and votes). Host-only operation.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format
  - `pollId` (String, required): UUID format

#### Response
**Status:** `204 NO CONTENT`

**Body:** Empty

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:245-254`)
   - Verifies user can **edit** hangout (host check)
   - Throws `UnauthorizedException` if not a host

2. **Delete Poll** (`PollServiceImpl:256`)
   - Calls `hangoutRepository.deletePoll(eventId, pollId)`
   - **Only deletes the poll record itself**
   - Options and votes remain (orphaned data)

3. **Pointer Synchronization** (`PollServiceImpl:259`)
   - Updates pointer records to remove poll from feeds

#### Database Operations

1. `GetHangoutDetailData`: Authorization
2. `DeleteItem`: Delete poll record
   - Key: `pk=EVENT#{eventId}`, `sk=POLL#{pollId}`
   - Does NOT delete options or votes
3. `BatchWriteItem`: Update pointers

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `403 FORBIDDEN` | `UNAUTHORIZED` | User is not a host |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |

#### Important Notes

**⚠️ CRITICAL BUG: Orphaned Data**
- Deleting a poll **DOES NOT** delete its options or votes
- Options (`POLL#{pollId}#OPTION#...`) remain in database
- Votes (`POLL#{pollId}#VOTE#...`) remain in database
- These orphaned records are invisible but consume storage
- **Recommendation**: Implement transaction to delete poll + options + votes atomically

**Workaround:**
- Options/votes won't appear in queries because they require knowing the pollId
- Eventually consistent: pointer updates remove poll from feeds

---

### 7. Add Poll Option

**Endpoint:** `POST /hangouts/{eventId}/polls/{pollId}/options`

**Description:** Adds a new option to an existing poll. Host-only operation.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format
  - `pollId` (String, required): UUID format

#### Request Body (`AddPollOptionRequest`)
```json
{
  "text": "Late Evening"
}
```

**Field Validations:**
- `text`: Required, 1-100 characters

#### Response
**Status:** `201 CREATED`

**Body:** `PollOption` object
```json
{
  "eventId": "event-456",
  "pollId": "poll-123",
  "optionId": "option-new",
  "text": "Late Evening",
  "pk": "EVENT#event-456",
  "sk": "POLL#poll-123#OPTION#option-new",
  "createdAt": 1699999999,
  "updatedAt": 1699999999
}
```

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:268-277`)
   - Verifies user can edit hangout (host check)

2. **Option Creation** (`PollServiceImpl:280-281`)
   - Creates `PollOption` with auto-generated UUID
   - Keys: `pk=EVENT#{eventId}`, `sk=POLL#{pollId}#OPTION#{optionId}`
   - Saves using `hangoutRepository.savePollOption(option)`

3. **Pointer Synchronization** (`PollServiceImpl:284`)
   - Updates pointer records with new option

#### Database Operations

1. `GetHangoutDetailData`: Authorization
2. `PutItem`: Save new option
3. `BatchWriteItem`: Update pointers

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `400 BAD_REQUEST` | `VALIDATION_ERROR` | Text too short/long |
| `403 FORBIDDEN` | `UNAUTHORIZED` | User is not a host |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |

#### Important Notes
- Does NOT validate that poll exists (would succeed even for non-existent poll)
- New option starts with zero votes
- Existing votes are unaffected

---

### 8. Delete Poll Option

**Endpoint:** `DELETE /hangouts/{eventId}/polls/{pollId}/options/{optionId}`

**Description:** Deletes a poll option and all votes for that option atomically. Host-only operation.

#### Request Parameters
- **Path Variables:**
  - `eventId` (String, required): UUID format
  - `pollId` (String, required): UUID format
  - `optionId` (String, required): UUID format

#### Response
**Status:** `204 NO CONTENT`

**Body:** Empty

#### Internal Flow

1. **Authorization Check** (`PollServiceImpl:294-303`)
   - Verifies user can edit hangout

2. **Validation** (`PollServiceImpl:306-321`)
   - Queries poll data to verify poll and option exist
   - Throws `IllegalArgumentException` if poll not found
   - Throws `IllegalArgumentException` if option not found

3. **Atomic Deletion** (`PollServiceImpl:324`)
   - Uses `hangoutRepository.deletePollOptionTransaction(eventId, pollId, optionId)`
   - **DynamoDB Transaction** ensures all-or-nothing deletion

4. **Pointer Synchronization** (`PollServiceImpl:327`)
   - Updates pointer records

#### Database Operations

**Transaction Steps (HangoutRepositoryImpl:1062-1114):**

1. `Query`: Get all poll data
2. Filter votes for the specific optionId
3. **TransactWriteItems**:
   - Delete poll option item
   - Delete ALL votes for that option (can be 0 to N deletes)
   - All operations succeed or all fail (atomicity)
4. `BatchWriteItem`: Update pointers

**Example Transaction:**
```
TransactWriteItems:
  - Delete: pk=EVENT#123, sk=POLL#abc#OPTION#xyz
  - Delete: pk=EVENT#123, sk=POLL#abc#VOTE#user1#OPTION#xyz
  - Delete: pk=EVENT#123, sk=POLL#abc#VOTE#user2#OPTION#xyz
```

#### Error Responses

| HTTP Status | Error Code | Condition |
|-------------|-----------|-----------|
| `400 BAD_REQUEST` | - | Poll or option not found |
| `403 FORBIDDEN` | `UNAUTHORIZED` | User is not a host |
| `404 NOT_FOUND` | `EVENT_NOT_FOUND` | Event doesn't exist |
| `500 INTERNAL_SERVER_ERROR` | `REPOSITORY_ERROR` | Transaction failed |

#### Important Notes
- **This is the ONLY poll operation using a transaction**
- Ensures votes are never orphaned (unlike delete poll)
- Transaction can include 0 to N vote deletions
- If option has 100 votes, transaction includes 101 delete operations

---

## Data Models

### Poll (Canonical Record)

**Table:** `InviterTable`
**Keys:** `pk=EVENT#{eventId}`, `sk=POLL#{pollId}`

```java
public class Poll extends BaseItem {
    private String eventId;          // Event this poll belongs to
    private String pollId;           // Auto-generated UUID
    private String title;            // 1-200 characters
    private String description;      // Max 1000 characters
    private boolean multipleChoice;  // Single vs. multiple choice
    private boolean isActive;        // Currently always true

    // Inherited from BaseItem:
    private String pk;               // EVENT#{eventId}
    private String sk;               // POLL#{pollId}
    private String itemType;         // "POLL"
    private Long createdAt;          // Unix timestamp
    private Long updatedAt;          // Unix timestamp
}
```

### PollOption (Canonical Record)

**Table:** `InviterTable`
**Keys:** `pk=EVENT#{eventId}`, `sk=POLL#{pollId}#OPTION#{optionId}`

```java
public class PollOption extends BaseItem {
    private String eventId;
    private String pollId;
    private String optionId;         // Auto-generated UUID
    private String text;             // 1-100 characters

    // Inherited from BaseItem:
    private String pk;               // EVENT#{eventId}
    private String sk;               // POLL#{pollId}#OPTION#{optionId}
    private String itemType;         // "POLL_OPTION"
    private Long createdAt;
    private Long updatedAt;
}
```

### Vote (Canonical Record)

**Table:** `InviterTable`
**Keys:** `pk=EVENT#{eventId}`, `sk=POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}`

```java
public class Vote extends BaseItem {
    private String eventId;
    private String pollId;
    private String optionId;
    private String userId;           // Who voted
    private String voteType;         // "YES", "NO", or "MAYBE"

    // Inherited from BaseItem:
    private String pk;               // EVENT#{eventId}
    private String sk;               // POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}
    private Long createdAt;
    private Long updatedAt;
}
```

**Note:** Vote does NOT have an `itemType` field in the model.

### DTOs (Data Transfer Objects)

#### PollWithOptionsDTO (for list views)
```java
public class PollWithOptionsDTO {
    private String pollId;
    private String title;
    private String description;
    private boolean multipleChoice;
    private List<PollOptionDTO> options;
    private int totalVotes;          // Calculated at runtime
}
```

#### PollDetailDTO (for detail view)
```java
public class PollDetailDTO {
    private String pollId;
    private String title;
    private String description;
    private boolean multipleChoice;
    private List<PollOptionDetailDTO> options;
    private int totalVotes;          // Calculated at runtime
}
```

#### PollOptionDTO (summary)
```java
public class PollOptionDTO {
    private String optionId;
    private String text;
    private int voteCount;           // Calculated at runtime
    private boolean userVoted;       // Does current user have a vote here?
}
```

#### PollOptionDetailDTO (with vote list)
```java
public class PollOptionDetailDTO {
    private String optionId;
    private String text;
    private int voteCount;           // Calculated at runtime
    private boolean userVoted;
    private List<VoteDTO> votes;     // All votes for this option
}
```

#### VoteDTO (individual vote)
```java
public class VoteDTO {
    private String userId;           // Who voted
    private String voteType;         // Their vote type
}
```

---

## Error Handling

### Exception Hierarchy

All controllers extend `BaseController` which provides centralized exception handling.

### Common Exceptions

| Exception | HTTP Status | Error Code | When Thrown |
|-----------|-------------|------------|-------------|
| `UnauthorizedException` | 403 | `UNAUTHORIZED` | User cannot view/edit hangout |
| `EventNotFoundException` | 404 | `EVENT_NOT_FOUND` | Hangout doesn't exist |
| `IllegalArgumentException` | 400 | - | Poll/option not found, invalid state |
| `IllegalStateException` | 400 | - | Already voted on this option |
| `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | Path variable format invalid |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | Request body validation failed |
| `RepositoryException` | 500 | `REPOSITORY_ERROR` | Database operation failed |
| `DynamoDbException` | 500 | `DATABASE_ERROR` | Low-level DynamoDB error |

### Error Response Format

All errors return JSON in this format:
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": 1699999999000
}
```

### Exception Handler Examples

```java
@ExceptionHandler(UnauthorizedException.class)
public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
}

@ExceptionHandler(IllegalStateException.class)
// Caught by general Exception handler, returns 500
```

**Note:** `IllegalArgumentException` and `IllegalStateException` are caught by the general `Exception` handler and return 500 errors, not 400. This may be suboptimal for client debugging.

---

## Authorization

### Authorization Service

All poll operations use `AuthorizationService` to check permissions:

```java
authorizationService.canUserViewHangout(userId, hangout)  // For view/vote operations
authorizationService.canUserEditHangout(userId, hangout)  // For create/delete operations
```

### Permission Matrix

| Operation | Required Permission | Who Has It |
|-----------|-------------------|------------|
| Create Poll | Edit Hangout | Event hosts (group admins) |
| Get Polls | View Hangout | Event members + hosts |
| Get Poll Detail | View Hangout | Event members + hosts |
| Vote | View Hangout | Event members + hosts |
| Remove Own Vote | View Hangout | Event members + hosts |
| Delete Poll | Edit Hangout | Event hosts only |
| Add Option | Edit Hangout | Event hosts only |
| Delete Option | Edit Hangout | Event hosts only |

### User ID Extraction

User ID comes from JWT token, extracted by `BaseController.extractUserId()`:
```java
String userId = (String) request.getAttribute("userId");
// Set by JwtAuthenticationFilter before controller method runs
```

**Security Notes:**
- Users cannot remove other users' votes (userId from JWT, not request)
- No direct poll ownership concept; authorization is through hangout
- Poll detail exposes all voter userIds (privacy consideration)

---

## Pointer Synchronization

### Why Pointer Synchronization?

Poll data must be denormalized to `HangoutPointer` records for efficient group feed queries. Without this, fetching a group's feed would require:
1. Query for all hangouts in group
2. For each hangout, query for its polls (N+1 problem)

With pointers:
1. Single query gets hangouts WITH poll data embedded

### Synchronization Trigger

**Every poll/option/vote write operation** triggers pointer synchronization:
- `createPoll()` → line 80
- `voteOnPoll()` → line 190
- `removeVote()` → line 236
- `deletePoll()` → line 259
- `addPollOption()` → line 284
- `deletePollOption()` → line 327

### Synchronization Process (`PollServiceImpl:342-388`)

1. **Find Associated Groups**
   - Retrieves hangout to get `associatedGroups` list
   - Each hangout can belong to multiple groups

2. **Get Current Poll Data**
   - Queries all poll data from canonical records
   - Separates into `List<Poll>`, `List<PollOption>`, `List<Vote>`

3. **Update Each Pointer**
   - For each group in `associatedGroups`:
     - Uses `pointerUpdateService.updatePointerWithRetry()`
     - Sets `pointer.polls`, `pointer.pollOptions`, `pointer.votes`
     - Optimistic locking with retry on conflict

4. **Update Group Timestamps**
   - Calls `groupTimestampService.updateGroupTimestamps(associatedGroups)`
   - Invalidates ETag cache for affected groups

### Pointer Structure

```java
public class HangoutPointer extends BaseItem {
    // ... other hangout fields ...
    private List<Poll> polls;           // ALL polls for this hangout
    private List<PollOption> pollOptions;  // ALL options for ALL polls
    private List<Vote> votes;           // ALL votes for ALL polls
}
```

**Keys:** `pk=GROUP#{groupId}`, `sk=HANGOUT#{hangoutId}`

### Performance Implications

**Benefits:**
- Group feed queries are fast (no N+1)
- Single read gets all hangout + poll data

**Costs:**
- Every poll write triggers pointer updates
- Large hangouts with many polls/votes increase pointer size
- Write amplification: 1 vote → N pointer updates (where N = number of groups)

**Example:** A hangout in 3 groups with 1 new vote:
1. Write vote to canonical record (1 PutItem)
2. Update 3 HangoutPointer records (3 PutItems in batch)
3. Update 3 Group timestamp records (3 PutItems in batch)
= **7 write operations** for 1 vote

### Optimistic Locking

Pointer updates use optimistic locking (version field):
```java
pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
    pointer.setPolls(new ArrayList<>(polls));
    pointer.setPollOptions(new ArrayList<>(pollOptions));
    pointer.setVotes(new ArrayList<>(votes));
}, "poll data");
```

**Retry Logic:**
- If version mismatch (concurrent update), retry
- Fresh read → apply changes → conditional write
- Prevents lost updates in concurrent scenarios

---

## Testing Considerations

### Unit Test Patterns

**Service Layer Tests:**
- Mock `HangoutRepository`, `AuthorizationService`, `PointerUpdateService`
- Test authorization logic
- Test vote logic (single vs. multiple choice)
- Test error cases (already voted, poll not found)

**Repository Layer Tests:**
- Use TestContainers with real DynamoDB Local
- Verify key structure matches patterns
- Test item collection queries
- Test transaction atomicity (delete option)

### Integration Test Scenarios

1. **Create Poll Workflow**
   - Create poll with options
   - Verify poll query returns all data
   - Verify pointer records updated

2. **Vote Logic**
   - Single-choice: Vote, change vote, verify old vote deleted
   - Multiple-choice: Vote on multiple options, verify all retained
   - Already voted: Attempt duplicate, expect 400 error

3. **Delete Option Transaction**
   - Create poll with options
   - Add votes to option
   - Delete option
   - Verify option AND votes deleted
   - Verify transaction atomicity (partial failure rolls back)

4. **Authorization Tests**
   - Non-host attempts create/delete poll → 403
   - Non-member attempts vote → 403
   - Member can vote but not delete → verify permissions

5. **Pointer Synchronization**
   - Create hangout in multiple groups
   - Add poll with options
   - Vote
   - Query each group's pointers
   - Verify all have identical poll data

### Edge Cases to Test

1. **Concurrent Votes**
   - Multiple users voting simultaneously
   - User changing vote while another user votes
   - Verify optimistic locking handles conflicts

2. **Large Polls**
   - Poll with 100+ options
   - Options with 1000+ votes
   - Verify query performance and transaction limits

3. **Orphaned Data**
   - Delete poll without deleting options/votes
   - Verify data invisible but exists
   - Test storage impact

4. **Invalid References**
   - Vote on non-existent option (should fail at app level)
   - Add option to non-existent poll (currently succeeds - bug?)
   - Delete already-deleted option (should be idempotent)

### Performance Testing

**Query Performance:**
- Measure `getAllPollData` with varying poll counts
- Measure `getSpecificPollData` query time
- Test pagination if implementing (currently loads all in memory)

**Write Amplification:**
- Measure pointer update time vs. number of groups
- Test hangout in 10+ groups
- Verify batch write efficiency

**Runtime Calculations:**
- Measure vote counting time with 1000+ votes
- Test memory usage for large poll responses
- Consider pre-calculating counts if performance degrades

---

## Appendix: Key Code References

### Controller
`src/main/java/com/bbthechange/inviter/controller/PollController.java`

### Service
- Interface: `src/main/java/com/bbthechange/inviter/service/PollService.java`
- Implementation: `src/main/java/com/bbthechange/inviter/service/impl/PollServiceImpl.java`

### Repository
- Interface: `src/main/java/com/bbthechange/inviter/repository/HangoutRepository.java` (lines 45-55)
- Implementation: `src/main/java/com/bbthechange/inviter/repository/impl/HangoutRepositoryImpl.java` (lines 482-1114)

### Models
- `src/main/java/com/bbthechange/inviter/model/Poll.java`
- `src/main/java/com/bbthechange/inviter/model/PollOption.java`
- `src/main/java/com/bbthechange/inviter/model/Vote.java`

### DTOs
- `src/main/java/com/bbthechange/inviter/dto/CreatePollRequest.java`
- `src/main/java/com/bbthechange/inviter/dto/AddPollOptionRequest.java`
- `src/main/java/com/bbthechange/inviter/dto/VoteRequest.java`
- `src/main/java/com/bbthechange/inviter/dto/PollWithOptionsDTO.java`
- `src/main/java/com/bbthechange/inviter/dto/PollDetailDTO.java`
- `src/main/java/com/bbthechange/inviter/dto/PollOptionDTO.java`
- `src/main/java/com/bbthechange/inviter/dto/PollOptionDetailDTO.java`
- `src/main/java/com/bbthechange/inviter/dto/VoteDTO.java`

### Utilities
`src/main/java/com/bbthechange/inviter/util/InviterKeyFactory.java` (lines 81-135, 177-203)

---

## Summary

The Polls API leverages DynamoDB's item collection pattern for efficient querying and uses pointer synchronization to maintain denormalized data in group feeds. Key characteristics:

- **Single-table design** with hierarchical sort keys
- **Runtime vote counting** (not stored in database)
- **Atomic option deletion** with transaction
- **Write amplification** for pointer updates
- **Authorization through hangout ownership**
- **Both single and multiple choice** poll support

**Performance:** Excellent read performance (single queries), moderate write performance (pointer updates).

**Scalability:** Suitable for typical event polls (1-10 polls, 10-100 votes each). Consider optimization for very large polls or high-concurrency scenarios.
