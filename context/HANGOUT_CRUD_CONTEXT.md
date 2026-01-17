# Context: Hangout CRUD Operations

**AUDIENCE:** This document is for developers and AI agents modifying or extending hangout (event) functionality. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md`.

## 1. Overview

The Hangout feature is the core of the application, allowing users to create, view, and manage events. Its implementation strictly follows the single-table design principles outlined in the DynamoDB design guide, using a combination of **Canonical Records** and **Pointer Records** for data integrity and read efficiency.

- **Canonical Record (`Hangout`):** The source of truth for an event, stored with `PK=EVENT#{hangoutId}` and `SK=METADATA`.
- **Pointer Record (`HangoutPointer`):** A denormalized summary of a hangout, stored with `PK=GROUP#{groupId}` and `SK=HANGOUT#{hangoutId}`. These are used for efficient rendering of group feeds.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `HangoutController.java` | Exposes REST endpoints for `/hangouts` and legacy `/events`. |
| `HangoutServiceImpl.java` | Implements the core business logic for all CRUD operations. |
| `HangoutRepositoryImpl.java` | Handles all DynamoDB interactions for hangouts and their related entities (polls, cars, etc.). |
| `Hangout.java` | The `@DynamoDbBean` for the canonical hangout record. Contains an optional `seriesId`. |
| `HangoutPointer.java` | The `@DynamoDbBean` for the denormalized hangout pointer record. Also contains an optional `seriesId`. |
| `CreateHangoutRequest.java` | DTO for creating a new hangout. |
| `UpdateHangoutRequest.java` | DTO for updating an existing hangout. |
| `HangoutDetailDTO.java` | DTO that aggregates all data for a hangout's detail view. |

## 3. CRUD Operations Flow

### Create Hangout

1.  **Endpoint:** `POST /hangouts`
2.  **Controller:** `HangoutController.createHangout()` receives a `CreateHangoutRequest`.
3.  **Service:** `HangoutServiceImpl.createHangout()`:
    *   Creates a `Hangout` (canonical) entity from the request.
    *   For each associated group, it creates a corresponding `HangoutPointer` entity.
    *   **CRITICAL:** It denormalizes necessary data (title, time info, location, mainImagePath, GSI keys) from the `Hangout` to the `HangoutPointer`.
4.  **Repository:** `HangoutRepositoryImpl.createHangoutWithAttributes()`:
    *   Executes a `TransactWriteItems` operation to save the `Hangout` record and all `HangoutPointer` records atomically.

### Read Hangout (Detail View)

1.  **Endpoint:** `GET /hangouts/{hangoutId}`
2.  **Service:** `HangoutServiceImpl.getHangoutDetail()`
3.  **Repository:** `HangoutRepositoryImpl.getHangoutDetailData()`:
    *   Executes a single, highly efficient **Query** on the main table with `PK = EVENT#{hangoutId}`.
    *   This single query retrieves the `Hangout` metadata record AND all other items in its collection (Polls, Cars, Votes, InterestLevels, etc.).
    *   The service then assembles this data into the `HangoutDetailDTO`.

### Update Hangout

1.  **Endpoint:** `PATCH /hangouts/{hangoutId}`
2.  **Service:** `HangoutServiceImpl.updateHangout()`:
    *   **Rule: Canonical First:** It first loads and modifies the canonical `Hangout` record and saves it.
    *   **Rule: Pointers Second:** It then determines which fields have changed (e.g., `title`, `timeInfo`, `mainImagePath`) and calls a method to update the corresponding `HangoutPointer` records.
    *   **Series Interaction:** Crucially, after the update, it calls `eventSeriesService.updateSeriesAfterHangoutModification()`. This service is responsible for updating the parent `EventSeries` and its `SeriesPointer` if the hangout's modification (e.g., a time change, image path change) affects the series' overall start or end time.

### Delete Hangout

1.  **Endpoint:** `DELETE /hangouts/{hangoutId}`
2.  **Service:** `HangoutServiceImpl.deleteHangout()`:
    *   **Series Interaction:** The service first checks if the hangout is part of a series by inspecting the `seriesId` field.
    *   If it is part of a series, it calls `eventSeriesService.removeHangoutFromSeries()` which handles the complex logic of removing the hangout from the series and potentially deleting the series if it was the last part.
    *   If it is a standalone hangout, it proceeds with simple deletion: deleting the `HangoutPointer` records and then calling the repository to delete the entire item collection.
4.  **Repository:** `HangoutRepositoryImpl.deleteHangout()`:
    *   Queries for all items in the hangout's item collection (`PK = EVENT#{hangoutId}`).
    *   Deletes every item in the collection.

## 4. Host at My Place Feature

The "Host at My Place" feature allows a hangout to record which user is hosting when someone selects "my place" as the location.

### Data Model

| Model | Field | Description |
| :--- | :--- | :--- |
| `Hangout` | `hostAtPlaceUserId` | User ID of the person hosting at their place (canonical) |
| `HangoutPointer` | `hostAtPlaceUserId` | Denormalized from Hangout for feed efficiency |
| `HangoutSummaryDTO` | `hostAtPlaceUserId`, `hostAtPlaceDisplayName`, `hostAtPlaceImagePath` | Response includes resolved user info |
| `HangoutDetailDTO` | `hostAtPlaceDisplayName`, `hostAtPlaceImagePath` | Response includes resolved user info (userId comes from nested Hangout) |

### Create/Update Flow

1. **Validation:** When `hostAtPlaceUserId` is provided in `CreateHangoutRequest` or `UpdateHangoutRequest`, the service validates that the user exists via `UserService.getUserSummary()`. If the user doesn't exist, a `ValidationException` is thrown.
2. **Denormalization:** The field is copied to all `HangoutPointer` records during:
   - `createHangout()` - Initial pointer creation
   - `updateHangout()` - Via `updatePointersWithBasicFields()`
   - `associateEventWithGroups()` - When adding hangout to new groups
   - `resyncHangoutPointers()` - Manual resync operation

### Read Flow (Enrichment)

On read, the `hostAtPlaceUserId` is resolved to display name and image path:

1. **`HangoutServiceImpl.enrichHostAtPlaceInfo(HangoutSummaryDTO)`:** Called during feed hydration to populate `hostAtPlaceDisplayName` and `hostAtPlaceImagePath` from `UserService`.
2. **`getHangoutDetail()`:** Resolves host info when building `HangoutDetailDTO`.
3. **`GroupServiceImpl`:** Calls `hangoutService.enrichHostAtPlaceInfo()` for standalone hangouts and series parts in feeds.

### Edge Cases

- **Null value:** Setting `hostAtPlaceUserId` to `null` clears the host at place designation
- **User deleted:** If the user is deleted after the hangout was created, the `hostAtPlaceUserId` is still returned but `displayName` and `imagePath` will be null
- **Invalid user ID:** Attempting to set an invalid user ID throws `ValidationException` and the operation is rejected

## 5. Cross-Cutting Concerns: Event Series

The `Hangout` entity is deeply connected to the `EventSeries` feature.

-   **`seriesId` Field:** The `Hangout.java` and `HangoutPointer.java` models both contain a `seriesId` field. When this field is populated, the hangout is considered part of a series.
-   **Creation:** A hangout can be created as a new part of an existing series via the `SeriesController`. In this case, its `seriesId` is set upon creation.
-   **Updates:** As mentioned in the update flow, modifying a hangout's time or title can trigger a cascading update to the parent `EventSeries` and its `SeriesPointer` to ensure denormalized data remains consistent.
-   **Deletion:** Deleting a hangout that is part of a series is a more complex operation handled by the `EventSeriesService` to ensure the integrity of the series record.

## 6. TV Watch Party Fields

Hangouts that are part of a TV watch party series have additional fields. See `TV_WATCH_PARTY_CONTEXT.md` for full details.

### Additional Hangout Fields

| Field | Type | Description |
|-------|------|-------------|
| `titleNotificationSent` | Boolean | True after first title update notification sent |
| `combinedExternalIds` | List<String> | All TVMaze episode IDs if this is a combined episode hangout |

### How Watch Party Hangouts Are Created

Watch party hangouts are created by `WatchPartyService` (not `HangoutService`) with:
- `externalId` = TVMaze episode ID
- `externalSource` = "TVMAZE"
- `isGeneratedTitle` = true (unless user edits)
- `seriesId` linking to the parent `EventSeries`

### Title Update Protection

When `isGeneratedTitle=true`, the background polling system may update the hangout title when TVMaze updates the episode name. The `titleNotificationSent` flag ensures only one notification is sent per hangout.

If the user manually edits the title, `isGeneratedTitle` is set to `false` and the title will never be auto-updated.
