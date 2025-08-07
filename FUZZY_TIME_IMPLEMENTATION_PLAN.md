# Backend Implementation Plan: Hangouts with Fuzzy Time

This document outlines the complete backend plan for implementing the fuzzy time feature for hangouts. It covers the required database changes to the `InviterTable` and the application logic needed to process API requests and interact with the database.

---

## 1. Database Implementation (`InviterTable`)

The core of the database strategy is to add a new, flexible Global Secondary Index (GSI) to support time-based queries for both groups and individual users.

### 1.1. New Global Secondary Index (GSI): `EntityTimeIndex`

A new GSI will be provisioned on the `InviterTable`.

-   **Purpose**: To enable efficient, time-sorted queries for all hangouts associated with a specific entity (a group or a user).
-   **GSI Partition Key**: `GSI1PK` (String)
-   **GSI Sort Key**: `GSI1SK` (String)

### 1.2. Canonical Record (`EVENT#{HangoutID}`)

The single source of truth for a hangout will be updated to store the complete time context.

-   **PK**: `EVENT#{HangoutID}`
-   **SK**: `METADATA`
-   **New Attributes to Add**:
    -   `startTimestamp` (Number): The canonical UTC Unix timestamp (seconds since epoch) representing the event's start time. This is the source for the GSI sort key.
    -   `endTimestamp` (Number): The canonical UTC Unix timestamp for the event's end time.
    -   `timeInput` (Map): A direct copy of the `timeInput` object from the original `POST` or `PATCH` request. This preserves the user's original intent for display purposes.

### 1.3. Pointer Records

Two types of pointer records will be created to populate the `EntityTimeIndex`.

#### A. Group Pointer Record

-   **Purpose**: To associate a hangout with a group.
-   **PK**: `GROUP#{GroupID}`
-   **SK**: `HANGOUT#{HangoutID}`
-   **Attributes for GSI**:
    -   `GSI1PK`: `GROUP#{GroupID}` (The value of the PK)
    -   `GSI1SK`: `T#{startTimestamp}` (A string-formatted timestamp, e.g., "T#1723122000")
    -   Other denormalized data like `title` will be included as before.

#### B. Individual Invite Pointer Record

-   **Purpose**: To associate a hangout with an individually invited user.
-   **PK**: `USER#{UserID}`
-   **SK**: `HANGOUT#{HangoutID}`
-   **Attributes for GSI**:
    -   `GSI1PK`: `USER#{UserID}` (The value of the PK)
    -   `GSI1SK`: `T#{startTimestamp}` (The same string-formatted timestamp)
    -   Denormalized data like `title` will be included.

---

## 2. Backend Logic Implementation

### 2.1. Core Component: `FuzzyTimeService`

A new, dedicated service will be created to handle all time conversions, ensuring this complex logic is centralized and testable.

-   **Input**: The `timeInput` object from an API request.
-   **Logic**:
    1.  It inspects the `timeInput` object.
    2.  **If `startTime` and `endTime` are present**: It parses these ISO 8601 strings (which include the timezone offset) and converts them into absolute UTC Unix timestamps.
    3.  **If `periodGranularity` and `periodStart` are present**: It parses the `periodStart` ISO string to get the absolute UTC start time. It then calculates the `endTimestamp` based on predefined business rules for each granularity (e.g., `weekend` = `periodStart` + 2 days, `evening` = `periodStart` + 4 hours).
-   **Output**: A simple data object containing two fields: the canonical `startTimestamp` and `endTimestamp` (as Number types).

### 2.2. Write Logic: Creating/Updating a Hangout

This logic applies to `POST /hangouts` and `PATCH /hangouts/{hangoutId}`.

1.  **Validation**: The controller validates the incoming `timeInput` object to ensure it matches one of the two allowed structures (fuzzy or exact).
2.  **Time Conversion**: The `HangoutService` calls the `FuzzyTimeService` with the `timeInput` object to get the canonical `startTimestamp` and `endTimestamp`.
3.  **Transaction**: The service constructs and executes a `TransactWriteItems` operation to atomically perform all necessary writes:
    -   **Write Canonical Record**: `Put` the `EVENT#{HangoutID}` item with all its attributes, including the new `startTimestamp`, `endTimestamp`, and `timeInput` map.
    -   **Write Pointer Records**: For each associated group and each individually invited user, `Put` a corresponding pointer record (`GROUP#{...}` or `USER#{...}`). Each pointer record must be populated with its `GSI1PK` and `GSI1SK` attributes.

### 2.3. Read Logic: Getting All Hangouts for a User

This is the logic for fetching a user's complete, chronologically sorted list of upcoming hangouts.

1.  **Get User's Groups**: The application first fetches the list of all `GroupID`s the user is a member of.
2.  **Construct GSI Queries**: The application creates a list of partition keys to query against the GSI. This list includes the user's own ID (`USER#{UserID}`) and the ID of each group they belong to (`GROUP#{GroupID_A}`, `GROUP#{GroupID_B}`, etc.).
3.  **Execute Parallel GSI Queries**: The application executes multiple `Query` operations against the `EntityTimeIndex` in parallel. Each query will use one of the partition keys from the list and a sort key condition of `GSI1SK > T#{current_timestamp}`.
4.  **Merge & Sort Results**: The application collects the lists of pointer records from all the parallel queries. Since each list is already sorted by time (the GSI sort key), a simple and efficient merge of the sorted lists will produce the final, chronologically ordered list of all hangouts for the user.

### 2.4. Response Formatting

When preparing an API response for `GET /hangouts/{hangoutId}` or `GET /groups/{groupId}/feed`:

1.  The backend will retrieve the canonical `EVENT` record.
2.  It will take the `timeInput` map that was stored on that record.
3.  It will convert any timestamp values within that map from their stored UTC format back into ISO 8601 UTC strings (ending in `Z`).
4.  This formatted `timeInput` object will be placed in the API response, ensuring the client receives the data in the consistent, UTC-based format defined in the API specification.
