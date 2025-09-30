# Context: Group CRUD & Feed Operations

**AUDIENCE:** This document is for developers and AI agents modifying or extending group functionality. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md`.

## 1. Overview

Groups are collections of users that form the social fabric of the application. A user can be a member of multiple groups, and hangouts are typically associated with one or more groups. The implementation relies heavily on the single-table design patterns for efficiency.

- **Canonical Record (`Group`):** The source of truth for a group, stored with `PK=GROUP#{groupId}` and `SK=METADATA`.
- **Membership Record (`GroupMembership`):** Links a user to a group, stored with `PK=GROUP#{groupId}` and `SK=USER#{userId}`. This item is crucial for authorization and for finding all members of a group.
- **GSI for User-to-Group Lookups:** The `UserGroupIndex` GSI is used to efficiently find all groups a user belongs to. `GroupMembership` items have a `gsi1pk=USER#{userId}` to enable this pattern.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `GroupController.java` | Exposes REST endpoints for `/groups`. |
| `GroupServiceImpl.java` | Implements the core business logic for group and membership CRUD. |
| `GroupFeedServiceImpl.java` | Implements the logic for fetching and constructing the chronological group feed. |
| `GroupRepositoryImpl.java` | Handles all DynamoDB interactions for groups. **Note: This is deprecated in favor of `PolymorphicGroupRepositoryImpl`** |
| `PolymorphicGroupRepositoryImpl.java` | The active repository implementation that should be used for new development. |
| `Group.java` | The `@DynamoDbBean` for the canonical group record. Contains mainImagePath and backgroundImagePath. |
| `GroupMembership.java` | The `@DynamoDbBean` for the membership link between a user and a group. Contains denormalized groupName, groupMainImagePath, groupBackgroundImagePath, and userMainImagePath for efficient display. |
| `CreateGroupRequest.java` | DTO for creating a new group. |
| `GroupFeedDTO.java` | DTO that represents the complex, chronologically sorted feed for a group. |

## 3. CRUD Operations Flow

### Create Group

1.  **Endpoint:** `POST /groups`
2.  **Controller:** `GroupController.createGroup()`
3.  **Service:** `GroupServiceImpl.createGroup()`:
    *   Creates a `Group` (canonical) entity and a `GroupMembership` entity for the creator.
    *   The creator is automatically assigned the `ADMIN` role.
    *   **CRITICAL:** The `GroupMembership` record is populated with the `groupName` (denormalized), the group's image paths (groupMainImagePath, groupBackgroundImagePath), and the GSI keys (`gsi1pk`, `gsi1sk`) required for the `UserGroupIndex`.
4.  **Repository:** `groupRepository.createGroupWithFirstMember()`:
    *   Executes a `TransactWriteItems` operation to save the `Group` and `GroupMembership` records atomically.

### Read Groups (for a User)

1.  **Endpoint:** `GET /groups`
2.  **Controller:** `GroupController.getUserGroups()`
3.  **Service:** `GroupServiceImpl.getUserGroups()`
4.  **Repository:** `groupRepository.findGroupsByUserId()`:
    *   Executes a single, efficient **Query** against the **`UserGroupIndex` GSI** with `gsi1pk = USER#{userId}`.
    *   This returns all `GroupMembership` items for the user. The `groupName` is denormalized on these items, so **no additional queries are needed** to build the list of groups.

### Read Group Feed

1.  **Endpoint:** `GET /groups/{groupId}/feed-items`
2.  **Controller:** `GroupController.getGroupFeedItems()`
3.  **Service:** `GroupFeedServiceImpl.getFeedItems()`:
    *   This service uses a **backend loop aggregator pattern**.
    *   It paginates through the group's upcoming hangouts by querying the `EntityTimeIndex` GSI on the `HangoutPointer` items (`gsi1pk = GROUP#{groupId}`).
    *   For each hangout in the page, it queries for "actionable items" like open polls or undecided attributes.
    *   It continues this process until it has collected enough feed items to satisfy the request limit.

### Update Group

1.  **Endpoint:** `PATCH /groups/{groupId}`
2.  **Service:** `GroupServiceImpl.updateGroup()`:
    *   Updates the canonical `Group` record with new values (name, visibility, image paths)
    *   If `groupName` changed: calls `groupRepository.updateMembershipGroupNames()` to propagate to all memberships
    *   If image paths changed: calls `groupRepository.updateMembershipGroupImagePaths()` to propagate to all memberships

### Update Group Images

When a group's mainImagePath or backgroundImagePath changes:
1. The canonical `Group` record is updated first
2. `GroupRepository.updateMembershipGroupImagePaths()` is called
3. All `GroupMembership` records for that group are updated in batches (25 at a time) to maintain denormalized consistency

### Add/Remove Member

1.  **Endpoint:** `POST /groups/{groupId}/members` or `DELETE /groups/{groupId}/members/{userId}`
2.  **Service:** `GroupServiceImpl.addMember()` / `removeMember()`
3.  **Repository:** `groupRepository.addMember()` / `removeMember()`:
    *   `addMember`: Creates a new `GroupMembership` item with the correct PK/SK, GSI keys, and denormalized group data (groupName, groupMainImagePath, groupBackgroundImagePath).
    *   `removeMember`: Deletes the `GroupMembership` item.

## 4. Identified Tech Debt & Risks

-   **Deprecated Repository:** `GroupRepositoryImpl` is marked as `@Deprecated`. All new development **must** use `PolymorphicGroupRepositoryImpl`. This split in implementations is a significant source of confusion and potential bugs if the wrong repository is injected or modified.
-   **Complex Feed Logic:** The `GroupFeedServiceImpl` implements a complex backend loop. While documented, its performance is dependent on the density of actionable items within events. If many events have no actions, the service will loop and make multiple database calls before returning a result, which can be inefficient.
-   **Manual JSON in Pagination:** `GroupServiceImpl` manually constructs JSON strings for pagination tokens (`generatePreviousPageTokenFromBaseItems`). This is extremely brittle and unsafe. A structured object should be serialized with a proper JSON library (like Jackson, which is already a dependency) to create the token.
-   **Inconsistent Pagination:** There are two separate feed endpoints (`/feed` and `/feed-items`) with different logic and pagination strategies. `getGroupFeed` in `GroupServiceImpl` has complex, multi-part logic for past, present, and future events, while `getGroupFeedItems` in `GroupFeedServiceImpl` has the aggregator loop. This is confusing and redundant.
-   **Last Member Deletes Group:** The `leaveGroup` logic contains a potentially destructive side effect: if the last member leaves, the entire group is deleted. While this prevents orphaned groups, it's an implicit behavior that could lead to accidental data loss if not fully understood by the client or developer.
