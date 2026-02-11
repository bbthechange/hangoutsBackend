# Context: Event Series Operations

**AUDIENCE:** This document is for developers and AI agents modifying or extending event series functionality. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md`, `HANGOUT_CRUD_CONTEXT.md`, and `GROUP_CRUD_CONTEXT.md`.

## 1. Overview

Event Series are multi-part events, where a single conceptual event (the series) contains multiple `Hangout` records as its parts. This feature is built on top of the existing Hangout and Group models and uses similar single-table design patterns for data storage and retrieval.

- **Canonical Record (`EventSeries`):** The source of truth for a series, stored with `PK=SERIES#{seriesId}` and `SK=METADATA`. It contains the series title, description, and a list of `hangoutIds` that belong to it.
- **Pointer Record (`SeriesPointer`):** A denormalized summary of a series, stored with `PK=GROUP#{groupId}` and `SK=SERIES#{seriesId}`. This record is crucial for efficiently rendering series in a group's feed. It contains a denormalized copy of the `EventSeries` data and, importantly, a list of denormalized `HangoutPointer` objects for each part of the series.
- **GSI for Feeds:** Both `EventSeries` and `SeriesPointer` records are written to the `EntityTimeIndex` GSI, allowing them to be queried chronologically alongside standalone hangouts in a group's feed.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `SeriesController.java` | Exposes REST endpoints for `/series`. |
| `EventSeriesServiceImpl.java` | Implements the complex business logic for series operations, orchestrating transactions across multiple record types. |
| `EventSeriesRepositoryImpl.java` | Handles direct DynamoDB interactions for `EventSeries` canonical records. |
| `SeriesTransactionRepository.java` | A specialized repository responsible for executing large, atomic `TransactWriteItems` operations that span series, hangouts, and pointers. |
| `EventSeries.java` | The `@DynamoDbBean` for the canonical series record. Contains mainImagePath typically copied from the primary hangout. |
| `SeriesPointer.java` | The `@DynamoDbBean` for the denormalized series pointer record. Contains a list of `HangoutPointer` objects and mainImagePath denormalized from EventSeries. |
| `HangoutPointerFactory.java` | Centralized factory for creating HangoutPointer records for series parts via `fromHangout()`. |
| `CreateSeriesRequest.java` | DTO for creating a new series from an existing hangout. |
| `EventSeriesDetailDTO.java` | DTO that aggregates a series and the full `HangoutDetailDTO` for each of its parts. |

## 3. Core Operations Flow

Event series operations are significantly more complex than simple Hangout CRUD due to the number of records that must be kept in sync.

### Create Series (from an existing Hangout)

This is the primary method for creating a series.

1.  **Endpoint:** `POST /series`
2.  **Controller:** `SeriesController.createSeries()`
3.  **Service:** `EventSeriesServiceImpl.convertToSeriesWithNewMember()`:
    *   Takes an `existingHangoutId` and a `CreateHangoutRequest` for a new, second part of the series.
    *   **In-Memory Preparation:** It constructs all the necessary records before writing to the database:
        1.  A new `EventSeries` canonical record with mainImagePath copied from the primary hangout.
        2.  A new `Hangout` canonical record for the second part.
        3.  New `HangoutPointer` records via `HangoutPointerFactory.fromHangout()` (automatically copies all denormalized fields).
        4.  A new `SeriesPointer` record for the group feed.
    *   **Updates Existing Records:** It modifies the existing `Hangout` and its `HangoutPointer`s to link them to the new `seriesId`.
4.  **Repository:** `SeriesTransactionRepository.createSeriesWithNewPart()`:
    *   Executes a single, large `TransactWriteItems` request to atomically:
        *   `Put` the new `EventSeries`.
        *   `Put` the new `Hangout`.
        *   `Put` all new `HangoutPointer`s.
        *   `Put` the new `SeriesPointer`.
        *   `Update` the existing `Hangout` (to add `seriesId`).
        *   `Update` all existing `HangoutPointer`s (to add `seriesId`).

### Read Series (Detail View)

1.  **Endpoint:** `GET /series/{seriesId}`
2.  **Controller:** `SeriesController.getSeriesDetail()`
3.  **Service:** `EventSeriesServiceImpl.getSeriesDetail()`:
    *   Fetches the `EventSeries` canonical record.
    *   It then iterates through the `hangoutIds` stored on the series record.
    *   For each `hangoutId`, it calls `HangoutServiceImpl.getHangoutDetail()`, which executes the efficient item collection query for that hangout.
    *   Finally, it aggregates all the results into the `EventSeriesDetailDTO`.

### Add a Hangout to a Series

1.  **Endpoint:** `POST /series/{seriesId}/hangouts`
2.  **Service:** `EventSeriesServiceImpl.createHangoutInExistingSeries()`:
    *   Similar to creation, it prepares a new `Hangout`, its `HangoutPointer`s (via `HangoutPointerFactory.fromHangout()`), and an updated `SeriesPointer` in memory.
3.  **Repository:** `SeriesTransactionRepository.addPartToExistingSeries()`:
    *   Atomically updates the `EventSeries` (to add the new `hangoutId`), and puts the new `Hangout`, `HangoutPointer`s, and updated `SeriesPointer`.

### Delete Entire Series

1.  **Endpoint:** `DELETE /series/{seriesId}`
2.  **Service:** `EventSeriesServiceImpl.deleteEntireSeries()`:
    *   Gathers a list of all records associated with the series: the `EventSeries` itself, all `Hangout` records within it, all `HangoutPointer`s for those hangouts, and the `SeriesPointer`.
3.  **Repository:** `SeriesTransactionRepository.deleteEntireSeriesWithAllHangouts()`:
    *   Executes a `TransactWriteItems` request to atomically delete every record collected by the service.

## 4. Watch Party Series (TV Watch Party Feature)

A specialized type of EventSeries used for TV show watch parties. See `TV_WATCH_PARTY_CONTEXT.md` for full details.

### Identifying Watch Party Series

```java
// EventSeries and SeriesPointer have:
private String eventSeriesType;  // "WATCH_PARTY" for TV watch parties

public boolean isWatchParty() {
    return "WATCH_PARTY".equals(eventSeriesType);
}
```

### Additional Watch Party Fields on EventSeries

| Field | Type | Description |
|-------|------|-------------|
| `eventSeriesType` | String | "WATCH_PARTY" discriminator |
| `seasonId` | String | Reference to Season record |
| `defaultHostId` | String | Default host for new episodes |
| `defaultTime` | String | "HH:mm" format |
| `dayOverride` | Integer | 0=Sun...6=Sat |
| `timezone` | String | IANA timezone |
| `deletedEpisodeIds` | Set<String> | User-deleted episode IDs |

### Additional Watch Party Fields on SeriesPointer

| Field | Type | Description |
|-------|------|-------------|
| `interestLevels` | List<InterestLevel> | Series-level interest for notifications |

### Version Filtering

Watch party series are filtered from group feeds for old app versions (< 2.0.0):

```java
if (seriesPointer.isWatchParty() && !clientInfo.isVersionAtLeast("2.0.0")) {
    continue; // Filter out
}
```

### Watch Party CRUD

Watch parties use separate endpoints (`/groups/{groupId}/watch-parties`) handled by `WatchPartyController`, not the standard series endpoints. The `WatchPartyService` handles creation, updates, and deletion with TV-specific logic like episode combination and DST-aware scheduling.
