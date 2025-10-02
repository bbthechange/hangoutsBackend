# Context: Saved Places

**AUDIENCE:** This document is for developers and AI agents working on the saved places feature. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md`.

## 1. Overview

Saved Places allow users and groups to store frequently-used locations (addresses) with nicknames and optional notes. This feature supports a "primary place" concept for users only, where one place can be designated as their home address for quick access.

The data model follows the single-table design pattern with owner-based partitioning, making it efficient to retrieve all places for a user or group in a single query. Primary places use a GSI for fast lookups.

## 2. Data Model & Key Structure

### Core Entity: Place

| Attribute | Type | Description |
| :--- | :--- | :--- |
| **Partition Key (PK)** | String | `USER#{userId}` or `GROUP#{groupId}` - Groups places by owner |
| **Sort Key (SK)** | String | `PLACE#{placeId}` - Unique place identifier |
| `placeId` | String | UUID of the place |
| `nickname` | String | User-friendly name (e.g., "Coffee Shop", "My Place") |
| `address` | Address | Full address object (street, city, state, zip) |
| `notes` | String | Optional notes (e.g., "Gate code: 1234", "Parking in back") |
| `isPrimary` | Boolean | Only meaningful for USER-owned places, indicates home address |
| `status` | String | `ACTIVE` or `ARCHIVED` (soft delete) |
| `ownerType` | String | `USER` or `GROUP` |
| `createdBy` | String | UUID of user who created the place |
| `createdAt` | Instant | Timestamp |
| `updatedAt` | Instant | Timestamp |

### GSI: UserGroupIndex (for Primary Places)

| Attribute | Value | Purpose |
| :--- | :--- | :--- |
| **GSI Partition Key (gsi1pk)** | `USER#{userId}` | Only set for primary places |
| **GSI Sort Key (gsi1sk)** | `"PRIMARY_PLACE"` | Fixed string value |

**Important:** The gsi1sk is a **string** value `"PRIMARY_PLACE"`, not a number. Only primary user places have these GSI keys set. When a place becomes non-primary, these keys are removed (set to null).

### Key Structure Examples

```
// User's non-primary place
PK: USER#0ab8b8c1-b3b4-4b81-b938-d9cdebf63a20
SK: PLACE#983087bb-78ad-4860-8175-074d451468f0
gsi1pk: null
gsi1sk: null

// User's primary place
PK: USER#0ab8b8c1-b3b4-4b81-b938-d9cdebf63a20
SK: PLACE#7845e6bb-be9a-421a-a3eb-d304ffc856cc
gsi1pk: USER#0ab8b8c1-b3b4-4b81-b938-d9cdebf63a20
gsi1sk: "PRIMARY_PLACE"

// Group place
PK: GROUP#7a56a405-bd0c-4c43-97b7-160c486d18a9
SK: PLACE#a332eb0d-5975-47b5-a602-5591e614d600
gsi1pk: null
gsi1sk: null
```

## 3. Key Files & Classes

### Controllers
| File | Purpose |
| :--- | :--- |
| `PlaceController.java` | Exposes REST endpoints at `/places`. Handles validation, authentication extraction, and delegates to service layer. |

### Services
| File | Purpose |
| :--- | :--- |
| `PlaceService.java` | Interface defining place operations. |
| `PlaceServiceImpl.java` | Implements business logic including primary place management, authorization checks, and soft delete. Contains critical `handlePrimaryPlaceChange()` method. |

### Repositories
| File | Purpose |
| :--- | :--- |
| `PlaceRepository.java` | Interface for DynamoDB operations. |
| `PlaceRepositoryImpl.java` | Implements DynamoDB queries using direct DynamoDbClient. Uses QueryPerformanceTracker for monitoring. |

### Models & DTOs
| File | Purpose |
| :--- | :--- |
| `Place.java` | `@DynamoDbBean` entity extending `BaseItem`. Has two constructors: one for user-owned, one for group-owned places. **Note:** Field is `isPrimary` but setter is `setPrimary()` per JavaBeans conventions. |
| `PlaceDto.java` | Response DTO with `primary` field (boolean). |
| `PlacesResponse.java` | Wrapper containing separate `userPlaces` and `groupPlaces` lists for client convenience. |
| `CreatePlaceRequest.java` | Request DTO with `OwnerDto` to specify USER or GROUP. |
| `UpdatePlaceRequest.java` | Request DTO with all optional fields including `isPrimary`. |
| `OwnerDto.java` | Simple DTO with `id` and `type` fields. |

### Utilities
| File | Purpose |
| :--- | :--- |
| `InviterKeyFactory.java` | Contains constants (`PLACE_PREFIX`, `PRIMARY_PLACE`, `STATUS_ACTIVE`, etc.) and key generation methods (`getPlaceSk()`, `getUserGsi1Pk()`, `getGroupPk()`). |

### Exceptions
| File | Purpose |
| :--- | :--- |
| `PlaceNotFoundException.java` | Thrown when place doesn't exist or is archived. Maps to 404. |
| `InvalidPlaceOwnerException.java` | Thrown for business rule violations (e.g., groups with primary places). Maps to 400. |

## 4. Core Flows

### Get Places (Batch Fetch)

This is the most commonly used endpoint and supports efficient batch fetching.

1. **Endpoint:** `GET /places?userId={userId}&groupId={groupId}`
2. **Controller:** `PlaceController.getPlaces()`
   - Validates that at least one parameter is provided
   - Extracts authenticated user ID from JWT
3. **Service:** `PlaceServiceImpl.getPlaces()`
   - **If userId provided:**
     - Permission check: userId must match authenticated user
     - Calls `placeRepository.findPlacesByOwner(USER#{userId})`
     - Filters to `STATUS_ACTIVE` only
     - Maps to DTOs
   - **If groupId provided:**
     - Permission check: user must be group member via `groupRepository.isUserMemberOfGroup()`
     - Calls `placeRepository.findPlacesByOwner(GROUP#{groupId})`
     - Filters to `STATUS_ACTIVE` only
     - Maps to DTOs
   - Returns `PlacesResponse` with separate lists
4. **Repository:** `PlaceRepositoryImpl.findPlacesByOwner()`
   - **DynamoDB Query:**
     - `PK = :pk` (USER#{userId} or GROUP#{groupId})
     - `SK begins_with :sk_prefix` (PLACE#)
   - **Efficiency:** Single partition query per owner, no table scan

**Key Design Decision:** The endpoint allows BOTH userId and groupId simultaneously for efficient batch fetching. This reduces network round trips from 2 to 1 when displaying places in the event creation UI.

### Create Place

1. **Endpoint:** `POST /places`
2. **Controller:** `PlaceController.createPlace()`
   - Validates request body (required fields, UUID formats)
   - Extracts authenticated user ID
3. **Service:** `PlaceServiceImpl.createPlace()`
   - **Authorization checks:**
     - For USER: ownerId must match authenticated user
     - For GROUP: user must be group member
   - **Business rules:**
     - Groups cannot have primary places
     - If user place is primary, call `handlePrimaryPlaceChange()` to unset existing primary
   - **Create entity:**
     - Generates new UUID for placeId
     - Uses appropriate constructor (user vs group)
     - For primary user places, sets `gsi1pk` and `gsi1sk`
   - Saves to repository
4. **Repository:** `PlaceRepositoryImpl.save()`
   - Calls `place.touch()` to update timestamp
   - Converts to DynamoDB item map using TableSchema
   - Executes `PutItem`

**Critical:** The `handlePrimaryPlaceChange()` method is called BEFORE creating the new primary place to ensure only one primary exists.

### Update Place

1. **Endpoint:** `PUT /places/{placeId}?userId={userId}` or `?groupId={groupId}`
2. **Controller:** `PlaceController.updatePlace()`
   - Validates that exactly one of userId or groupId is provided
   - Extracts authenticated user ID
3. **Service:** `PlaceServiceImpl.updatePlace()`
   - **Authorization checks:** Same as create
   - **Fetch existing place:**
     - Calls `placeRepository.findByOwnerAndPlaceId()`
     - Returns 404 if not found or archived
   - **Update fields:** Only updates fields present in request
   - **Primary place handling:**
     - If changing to primary: call `handlePrimaryPlaceChange()`, then set GSI keys
     - If changing from primary: unset GSI keys
   - **Business rules:** Groups cannot become primary (validation)
   - Saves updated place
4. **Repository:** Uses same `save()` method as create

**Important:** Primary place transitions are handled atomically - the old primary is updated first (GSI keys removed), then the new primary is updated (GSI keys added).

### Delete Place (Soft Delete)

1. **Endpoint:** `DELETE /places/{placeId}?userId={userId}` or `?groupId={groupId}`
2. **Controller:** `PlaceController.deletePlace()`
   - Same validation as update
   - Returns 200 OK (empty body)
3. **Service:** `PlaceServiceImpl.deletePlace()`
   - **Authorization checks:** Same as update
   - **Fetch existing place:** Returns 404 if not found
   - **Soft delete:** Sets `status = "ARCHIVED"`
   - **If primary:** Unsets `isPrimary` flag and removes GSI keys
   - Saves updated place (doesn't physically delete)
4. **Repository:** Uses same `save()` method

**Design Decision:** Soft delete preserves data integrity for historical hangouts that may reference archived places. Archived places are filtered out in the service layer during GET operations.

### Get Primary Place (Internal Use)

This is not exposed as a public endpoint but used internally by the service layer.

1. **Service:** Calls `placeRepository.findPrimaryPlaceForUser(userId)`
2. **Repository:** `PlaceRepositoryImpl.findPrimaryPlaceForUser()`
   - **DynamoDB Query on GSI:**
     - Index: `UserGroupIndex`
     - `gsi1pk = USER#{userId}`
     - `gsi1sk = "PRIMARY_PLACE"`
   - Returns `Optional<Place>`
   - **Efficiency:** Single GSI query, returns at most one result

## 5. Database Query Patterns

### All Places for User
```
Query: PK = "USER#{userId}" AND SK begins_with "PLACE#"
Returns: All user's places (active and archived)
Post-filter: Service layer filters to STATUS_ACTIVE
Efficiency: Single partition query
```

### All Places for Group
```
Query: PK = "GROUP#{groupId}" AND SK begins_with "PLACE#"
Returns: All group's places (active and archived)
Post-filter: Service layer filters to STATUS_ACTIVE
Efficiency: Single partition query
```

### Primary Place Lookup
```
Query: GSI UserGroupIndex
       gsi1pk = "USER#{userId}" AND gsi1sk = "PRIMARY_PLACE"
Returns: User's primary place (if exists)
Efficiency: Single GSI query with exact match
```

### Specific Place Lookup
```
GetItem: PK = "USER#{userId}" or "GROUP#{groupId}"
         SK = "PLACE#{placeId}"
Returns: Specific place
Efficiency: Single item lookup
```

**Critical:** NO FULL TABLE SCANS. All queries use partition keys with optional sort key conditions.

## 6. Business Rules & Constraints

### Primary Place Rules
1. **Only users** can have primary places (groups cannot)
2. **Only one primary** place per user at any time
3. When creating/updating to primary, the system automatically unsets the previous primary
4. When deleting a primary place, the `isPrimary` flag is unset (no new primary is auto-selected)
5. Primary places are indexed in GSI for efficient retrieval

### Authorization Rules
1. Users can only view/create/update/delete their own places
2. Users can only view/create/update/delete group places if they are group members
3. All endpoints require JWT authentication via Bearer token

### Validation Rules
1. `owner.type` must be "USER" or "GROUP"
2. All IDs must be valid UUIDs (36-character format)
3. `nickname` is required (1-100 characters)
4. `address.streetAddress`, `address.city`, `address.state` are required
5. `notes` max length is 500 characters
6. For UPDATE/DELETE: exactly one of userId or groupId must be provided
7. For GET: at least one of userId or groupId must be provided

### Soft Delete Pattern
1. Deleted places are marked `status = "ARCHIVED"`
2. Archived places are filtered out from GET responses
3. Archived places return 404 if accessed directly
4. DELETE is idempotent (deleting archived place returns 200 OK)

## 7. Common Pitfalls & Solutions

### Pitfall 1: JavaBeans Naming Convention
**Problem:** Field is named `isPrimary` but setter was initially `setIsPrimary()`, causing DynamoDB serialization to fail.

**Solution:** Setter must be `setPrimary()` per JavaBeans conventions. Getter remains `isPrimary()`.

**Reference:** See `Group.java` which uses same pattern with `isPublic` / `setPublic()`.

### Pitfall 2: Multiple Primary Places
**Problem:** Race condition could create multiple primary places if two updates happen simultaneously.

**Solution:** Always call `handlePrimaryPlaceChange()` BEFORE setting the new primary. This unsets the old primary first. DynamoDB's eventual consistency model means there could be a brief window where both exist, but the final state will be correct.

**Future Enhancement:** Consider using DynamoDB transactions for atomic primary place transitions.

### Pitfall 3: Forgetting to Filter Archived Places
**Problem:** Archived places appearing in lists.

**Solution:** Service layer ALWAYS filters to `STATUS_ACTIVE` in the `getPlaces()` method. Repository returns all items, service filters. This ensures consistent behavior.

### Pitfall 4: Using getUserPk() Instead of getUserGsi1Pk()
**Problem:** Initially had duplicate method `getUserPk()` which was confusing.

**Solution:** Removed duplicate. Always use `InviterKeyFactory.getUserGsi1Pk(userId)` for both PK and gsi1pk values.

### Pitfall 5: GSI Key Type
**Problem:** Initial design used `gsi1sk = 1` (number) for primary place.

**Solution:** Changed to `gsi1sk = "PRIMARY_PLACE"` (string) to match existing GSI schema. The UserGroupIndex uses string-typed keys.

## 8. Testing

### Integration Test Script
`scripts/test-places-integration.sh` provides comprehensive end-to-end testing:

- Create user places (primary and non-primary)
- Create group places
- Batch fetch (both userId and groupId)
- Update place fields
- Change primary place (verifies old primary is unset)
- Delete places (soft delete)
- Validation errors (groups can't have primary, authorization checks)

**Run tests:**
```bash
./scripts/test-places-integration.sh
```

### Unit Tests
Unit tests should mock the repository layer and test:
- Service layer authorization logic
- Primary place transition logic in `handlePrimaryPlaceChange()`
- Soft delete behavior
- DTO mapping

## 9. Future Enhancements

### Potential Improvements
1. **Coordinates Storage:** Add `latitude` and `longitude` to Address for map integration
2. **Place Categories:** Tag places as "home", "work", "restaurant", "bar", etc.
3. **Shared Places:** Allow users to share their primary place with friends/groups
4. **Place Suggestions:** Auto-suggest places based on hangout history
5. **Transactional Updates:** Use DynamoDB transactions for atomic primary place transitions
6. **Caching:** Cache primary place lookups for frequently accessed users
7. **Batch Operations:** Support bulk create/update/delete for migration scenarios

### Schema Evolution Considerations
- Address structure is flexible (uses embedded object, not separate table)
- Adding new fields to Place is straightforward (nullable fields)
- Changing primary place logic would require data migration of GSI keys
- Consider versioning if making breaking changes to Address structure

## 10. API Documentation

See `PLACES_API_DOCUMENTATION.md` for complete API reference with curl examples, error codes, and request/response formats.

## 11. Related Features

### Event Creation
Places are designed to integrate with the event creation flow. When creating a hangout, users can select from their saved places and group places for the location field.

**Recommended Implementation:**
1. Call `GET /places?userId={userId}&groupId={groupId}` on event creation screen load
2. Display places in dropdown/list grouped by "My Places" and "Group Places"
3. Highlight primary place as the default selection
4. Allow creating new place inline if needed

### Profile Management
User's primary place could be displayed in their profile or used for:
- Default location suggestions
- Distance calculations for nearby events
- Map centering in location-based features

**Integration Point:** The primary place is indexed via GSI, making it efficient to fetch without loading all user places.
