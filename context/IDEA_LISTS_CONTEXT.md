# Context: Idea Lists

**AUDIENCE:** This document is for developers and AI agents working on the idea lists feature. It assumes familiarity with the single-table DynamoDB design pattern.

## 1. Overview

Idea Lists allow group members to collaboratively create and manage lists of ideas (restaurants, activities, movies, etc.) within a group. Each idea list belongs to a group and contains individual idea items. Any group member can create lists, add ideas, update, or delete them.

The data model follows the single-table design pattern with group-based partitioning. Both the idea list metadata and its individual ideas share the same partition key (`GROUP#{groupId}`), enabling efficient single-query retrieval of a list with all its ideas.

## 2. Data Model & Key Structure

### Core Entity: IdeaList

| Attribute | Type | Description |
| :--- | :--- | :--- |
| **Partition Key (PK)** | String | `GROUP#{groupId}` - Groups idea lists by group |
| **Sort Key (SK)** | String | `IDEALIST#{listId}` - Unique list identifier |
| `listId` | String | UUID of the idea list |
| `groupId` | String | UUID of the owning group |
| `name` | String | List name (1-100 chars) |
| `category` | IdeaListCategory | Category enum for organizing lists |
| `note` | String | Optional note (max 500 chars) |
| `createdBy` | String | UUID of user who created the list |
| `isLocation` | Boolean | Whether this list represents location-based ideas |
| `itemType` | String | `"IDEALIST"` |
| `createdAt` | Instant | Inherited from BaseItem |
| `updatedAt` | Instant | Inherited from BaseItem |

### Core Entity: IdeaListMember (Individual Idea)

| Attribute | Type | Description |
| :--- | :--- | :--- |
| **Partition Key (PK)** | String | `GROUP#{groupId}` - Same partition as parent list |
| **Sort Key (SK)** | String | `IDEALIST#{listId}#IDEA#{ideaId}` - Nested under parent list |
| `ideaId` | String | UUID of the idea |
| `listId` | String | UUID of the parent idea list |
| `groupId` | String | UUID of the owning group |
| `name` | String | Idea name (1-200 chars) |
| `url` | String | Optional URL (max 500 chars) |
| `note` | String | Optional note (max 1000 chars) |
| `addedBy` | String | UUID of user who added the idea |
| `addedTime` | Instant | Timestamp when idea was added |
| `imageUrl` | String | Optional image URL (max 2000 chars) |
| `externalId` | String | Optional ID from external source (max 200 chars) |
| `externalSource` | String | Optional source system, e.g. "TICKETMASTER", "YELP" (max 50 chars) |
| `interestedUserIds` | Set<String> | User IDs who expressed interest ("I'd do this"). DynamoDB string set; null when empty. |
| `itemType` | String | `"IDEA"` |

### Key Structure Examples

```
// Idea list metadata
PK: GROUP#7a56a405-bd0c-4c43-97b7-160c486d18a9
SK: IDEALIST#983087bb-78ad-4860-8175-074d451468f0

// Individual idea within a list
PK: GROUP#7a56a405-bd0c-4c43-97b7-160c486d18a9
SK: IDEALIST#983087bb-78ad-4860-8175-074d451468f0#IDEA#a332eb0d-5975-47b5-a602-5591e614d600
```

### Key Factory Methods (InviterKeyFactory)

| Method | Returns | Example |
| :--- | :--- | :--- |
| `getGroupPk(groupId)` | `GROUP#{groupId}` | Partition key |
| `getIdeaListSk(listId)` | `IDEALIST#{listId}` | Idea list sort key |
| `getIdeaListMemberSk(listId, ideaId)` | `IDEALIST#{listId}#IDEA#{ideaId}` | Idea member sort key |
| `getIdeaListPrefix(listId)` | `IDEALIST#{listId}` | Query prefix for a specific list + its ideas |
| `getIdeaListQueryPrefix()` | `IDEALIST#` | Query prefix for all idea lists in a group |
| `isIdeaList(sortKey)` | boolean | True if SK is an idea list (not a member) |
| `isIdeaListMember(sortKey)` | boolean | True if SK is an idea member |

## 3. Categories

```java
public enum IdeaListCategory {
    RESTAURANT, ACTIVITY, TRAIL, MOVIE, BOOK, TRAVEL, SHOW, BAR, OTHER
}
```

Categories are optional when creating a list. They help organize lists within a group.

## 4. API Endpoints

Base path: `/groups/{groupId}/idea-lists`

All endpoints require JWT authentication and group membership.

### Idea List Operations

| Method | Path | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/groups/{groupId}/idea-lists` | Get all lists with ideas | - | `List<IdeaListDTO>` |
| `GET` | `/groups/{groupId}/idea-lists/{listId}` | Get single list with ideas | - | `IdeaListDTO` |
| `POST` | `/groups/{groupId}/idea-lists` | Create new list | `CreateIdeaListRequest` | `IdeaListDTO` (201) |
| `PUT` | `/groups/{groupId}/idea-lists/{listId}` | Update list metadata | `UpdateIdeaListRequest` | `IdeaListDTO` |
| `DELETE` | `/groups/{groupId}/idea-lists/{listId}` | Delete list and all ideas | - | 204 No Content |

### Idea Operations

| Method | Path | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `POST` | `.../{listId}/ideas` | Add idea to list | `CreateIdeaRequest` | `IdeaDTO` (201) |
| `PATCH` | `.../{listId}/ideas/{ideaId}` | Update idea (partial) | `UpdateIdeaRequest` | `IdeaDTO` |
| `DELETE` | `.../{listId}/ideas/{ideaId}` | Delete idea | - | 204 No Content |

### Interest Operations ("I'd do this")

| Method | Path | Description | Request Body | Response |
| :--- | :--- | :--- | :--- | :--- |
| `PUT` | `.../{listId}/ideas/{ideaId}/interest` | Add interest (idempotent) | - | `IdeaDTO` |
| `DELETE` | `.../{listId}/ideas/{ideaId}/interest` | Remove interest (idempotent) | - | `IdeaDTO` |

No request body needed — user is extracted from JWT. Both return the updated IdeaDTO with refreshed `interestedUsers` and `interestCount`.

### Path Parameter Validation

All path parameters (`groupId`, `listId`, `ideaId`) are validated against UUID format: `[0-9a-f-]{36}`.

## 5. Request/Response DTOs

### CreateIdeaListRequest
```json
{
  "name": "Best Pizza Places",       // required, 1-100 chars
  "category": "RESTAURANT",          // optional, IdeaListCategory enum
  "note": "Our favorites",           // optional, max 500 chars
  "isLocation": true                 // optional
}
```

### UpdateIdeaListRequest (partial update)
```json
{
  "name": "Updated Name",            // optional, 1-100 chars
  "category": "ACTIVITY",            // optional
  "note": "Updated note",            // optional, max 500 chars
  "isLocation": false                // optional
}
```

### CreateIdeaRequest
```json
{
  "name": "Joe's Pizza",             // 1-200 chars (at least one of name/url/note required)
  "url": "https://joespizza.com",    // optional, max 500 chars
  "note": "Great thin crust",        // optional, max 1000 chars
  "imageUrl": "https://...",          // optional, max 2000 chars
  "externalId": "abc123",            // optional, max 200 chars
  "externalSource": "YELP"           // optional, max 50 chars
}
```

### UpdateIdeaRequest (PATCH semantics)
```json
{
  "name": "Updated name",            // optional, 1-200 chars
  "url": "https://updated.com",      // optional, max 500 chars
  "note": "Updated note",            // optional, max 1000 chars
  "imageUrl": "https://...",          // optional, max 2000 chars
  "externalId": "xyz789",            // optional, max 200 chars
  "externalSource": "TICKETMASTER"   // optional, max 50 chars
}
```

### IdeaListDTO (Response)
```json
{
  "id": "983087bb-78ad-4860-8175-074d451468f0",
  "name": "Best Pizza Places",
  "category": "RESTAURANT",
  "note": "Our favorites",
  "createdBy": "0ab8b8c1-b3b4-4b81-b938-d9cdebf63a20",
  "createdAt": "2025-01-15T10:30:00Z",
  "isLocation": true,
  "ideas": [
    {
      "id": "a332eb0d-5975-47b5-a602-5591e614d600",
      "name": "Joe's Pizza",
      "url": "https://joespizza.com",
      "note": "Great thin crust",
      "addedBy": "0ab8b8c1-b3b4-4b81-b938-d9cdebf63a20",
      "addedByName": "Jeana",
      "addedByImagePath": "users/0ab8b8c1.../profile.jpg",
      "addedTime": "2025-01-15T11:00:00Z",
      "imageUrl": null,
      "externalId": null,
      "externalSource": null,
      "interestedUsers": [
        {
          "userId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
          "displayName": "Alex",
          "profileImagePath": "users/f47ac10b.../profile.jpg"
        }
      ],
      "interestCount": 2
    }
  ]
}
```

## 6. Authorization Rules

All idea list operations require **group membership** only. There is no distinction between list creator and other group members:

- **View lists/ideas**: Any group member
- **Create list**: Any group member
- **Update list**: Any group member
- **Delete list**: Any group member
- **Add idea**: Any group member
- **Update idea**: Any group member
- **Delete idea**: Any group member

Authorization is enforced via `ensureUserIsGroupMember()` which calls `groupRepository.isUserMemberOfGroup()`. Non-members receive `UnauthorizedException`.

## 7. Business Logic & Validation

### Idea List Creation
- `name` is required and cannot be blank
- `category`, `note`, and `isLocation` are optional
- `createdBy` is set to the requesting user's ID automatically

### Idea Creation
- At least one of `name`, `url`, or `note` must be provided
- `addedBy` is set to the requesting user's ID automatically
- `addedTime` is set to `Instant.now()` automatically

### Update Semantics
- **IdeaList update** (`PUT`): Only provided (non-null) fields are updated; calls `touch()` to update timestamp
- **Idea update** (`PATCH`): Only provided (non-null) fields are updated; calls `touch()` to update timestamp

### Input Sanitization
- All string inputs are trimmed in DTO getters (name, url, note, imageUrl, externalId, externalSource)

### Delete Cascade
- Deleting an idea list deletes all its ideas via `deleteIdeaListWithAllMembers()`
- Uses batch delete: queries all items with `begins_with(sk, "IDEALIST#{listId}")`, then batch-deletes them

## 8. Interest Feature ("I'd do this")

### Overview
Group members can express interest in ideas by toggling an "I'd do this" signal. Interest data is stored as `Set<String> interestedUserIds` directly on the `IdeaListMember` DynamoDB item.

### Storage Pattern
- **DynamoDB `ADD`**: Atomically adds userId to the `interestedUserIds` string set (concurrent-safe)
- **DynamoDB `DELETE`**: Atomically removes userId from the string set
- **Empty sets**: DynamoDB cannot store empty sets; Enhanced Client returns null for absent attributes, treated as "no interest"
- **Precedent**: Same pattern as `EventSeries.deletedEpisodeIds`

### Interest Count
`interestCount = interestedUsers.size() + 1` — the "+1" represents the implicit creator who always counts as interested in their own idea.

### User Resolution
Interested user IDs are resolved to `InterestedUserDTO` (userId, displayName, profileImagePath) via `UserService.getUserSummary()` (Caffeine-cached). Deleted users are silently skipped.

### Backward Compatibility
- New `interestedUsers` and `interestCount` fields in IdeaDTO — old clients ignore unknown JSON fields
- New PUT/DELETE interest endpoints — old clients never call them
- Existing items without `interestedUserIds` — Enhanced Client returns null, treated as empty

## 9. Query Patterns

### Get All Lists for Group (single DynamoDB query)
```
PK = GROUP#{groupId} AND begins_with(SK, "IDEALIST#")
```
Returns both idea lists and their ideas. Repository separates them using `isIdeaList()` / `isIdeaListMember()`, then attaches ideas to their parent lists.

### Get Single List with Ideas (single DynamoDB query)
```
PK = GROUP#{groupId} AND begins_with(SK, "IDEALIST#{listId}")
```
Returns the list metadata and all its ideas in one query.

### Sorting
- **Idea lists**: Sorted by `createdAt` descending (newest first)
- **Ideas within a list**: Sorted by `interestCount` descending (most interest first), then `addedTime` descending (newest first)

## 10. File Locations

| Component | Path |
| :--- | :--- |
| Controller | `controller/IdeaListController.java` |
| Service Interface | `service/IdeaListService.java` |
| Service Impl | `service/impl/IdeaListServiceImpl.java` |
| Repository Interface | `repository/IdeaListRepository.java` |
| Repository Impl | `repository/impl/IdeaListRepositoryImpl.java` |
| IdeaList Model | `model/IdeaList.java` |
| IdeaListMember Model | `model/IdeaListMember.java` |
| IdeaListCategory Enum | `model/IdeaListCategory.java` |
| IdeaListDTO | `dto/IdeaListDTO.java` |
| IdeaDTO | `dto/IdeaDTO.java` |
| InterestedUserDTO | `dto/InterestedUserDTO.java` |
| CreateIdeaListRequest | `dto/CreateIdeaListRequest.java` |
| UpdateIdeaListRequest | `dto/UpdateIdeaListRequest.java` |
| CreateIdeaRequest | `dto/CreateIdeaRequest.java` |
| UpdateIdeaRequest | `dto/UpdateIdeaRequest.java` |

## 11. Feed Surfacing

Idea lists participate in the group feed via `IdeaFeedSurfacingServiceImpl`. High-interest ideas (interestCount ≥ 3) are surfaced as `IdeaFeedItemDTO` items when the group's upcoming weeks are not fully covered by CONFIRMED hangouts.

### Surfacing Rules

- **Suppression**: If all 3 upcoming ISO weeks each have ≥1 CONFIRMED hangout for the group, no ideas are surfaced.
- **Filtering**: Only ideas with `interestCount >= 3` qualify.
- **Sorting**: Qualifying ideas are sorted by `interestCount` descending (most-wanted first).
- **Graceful degradation**: If `IdeaListService` or the hangout repository throws, an empty list is returned and the feed still works.

### IdeaFeedItemDTO

Each surfaced idea becomes an `IdeaFeedItemDTO` in the group feed:

```json
{
  "type": "idea_suggestion",
  "ideaId": "...",
  "listId": "...",
  "groupId": "...",
  "ideaName": "Sushi Nakazawa",
  "listName": "NYC Restaurants",
  "imageUrl": "https://...",
  "note": "Omakase only",
  "interestCount": 5,
  "googlePlaceId": "ChIJ...",
  "address": "23 Commerce St, New York",
  "latitude": 40.7271,
  "longitude": -74.0028,
  "placeCategory": "restaurant"
}
```

### Key Files

| File | Purpose |
|------|---------|
| `service/IdeaFeedSurfacingService.java` | Interface |
| `service/impl/IdeaFeedSurfacingServiceImpl.java` | Suppression check + idea filtering |
| `dto/IdeaFeedItemDTO.java` | Feed DTO for surfaced ideas |
| `service/impl/GroupServiceImpl.java` | Calls `getSurfacedIdeas()` and includes results in feed |

See `MOMENTUM_CONTEXT.md` Section 13 for more detail on the suppression algorithm.

## 12. Testing

| Test File | Scope |
| :--- | :--- |
| `controller/IdeaListControllerTest.java` | Controller integration tests |
| `controller/IdeaListControllerUnitTest.java` | Controller unit tests |
| `service/impl/IdeaListServiceImplTest.java` | Service logic unit tests |
| `repository/impl/IdeaListRepositoryImplUnitTest.java` | Repository unit tests |
| `repository/impl/IdeaListRepositoryImplMethodsTest.java` | Repository method tests |
| `integration/repository/impl/IdeaListRepositoryImplTest.java` | Integration tests (TestContainers) |
| `dto/IdeaListDTOTest.java` | DTO conversion tests |
