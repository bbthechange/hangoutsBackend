# Inviter DynamoDB Single-Table Design Guide

**Version: 2.0**
**Last Updated:** 2025-08-09

**AUDIENCE:** This document is the source of truth for all database interactions. All developers and AI agents **MUST** read and adhere to these patterns before performing any read or write operations.

---

## 1. Core Concepts

The application uses a **single DynamoDB table** named `InviterTable` for all data. This design is optimized for performance by minimizing the number of queries required to fetch data for common views.

The two most important concepts are **Canonical Records** and **Pointer Records**.

*   **Canonical Record:** The single source of truth for a data entity (e.g., a Hangout). It contains the full set of attributes for that entity.
*   **Pointer Record:** A denormalized, lightweight copy of data from a canonical record. It exists solely to create efficient read patterns for lists and feeds. **Pointer records are the foundation of this architecture's performance.**

---

## 2. Item Types & Key Schema

This table defines the primary key (`PK`) and sort key (`SK`) structure for each major item type.

| Item Type | PK (Partition Key) | SK (Sort Key) | Purpose / Description |
| :--- | :--- | :--- | :--- |
| **Hangout (Canonical)** | `EVENT#{hangoutId}` | `METADATA` | The full, authoritative record for a single hangout event. |
| **Hangout Pointer** | `GROUP#{groupId}` | `HANGOUT#{hangoutId}` | A denormalized summary of a hangout, for efficient listing in a group's feed. |
| **Group** | `GROUP#{groupId}` | `METADATA` | The canonical record for a single group. |
| **Group Membership** | `GROUP#{groupId}` | `USER#{userId}` | Links a user to a group. Contains denormalized user/group info. |

---

## 3. Global Secondary Indexes (GSIs)

GSIs are used for query patterns that are not possible with the main table's primary key.

| Index Name | Partition Key | Sort Key | Purpose |
| :--- | :--- | :--- | :--- |
| **`UserGroupIndex`** | `gsi1pk` (String) | `gsi1sk` (String) | To find all groups a user belongs to (`gsi1pk = USER#{userId}`). |
| **`EntityTimeIndex`** | `gsi1pk` (String) | `startTimestamp` (Number) | **PRIMARY GSI for Hangouts.** To find all upcoming hangouts for an entity (a user OR a group), sorted by time. |

---

## 4. Query Patterns (How-To Guide)

**FAILURE TO FOLLOW THESE PATTERNS WILL CAUSE SEVERE PERFORMANCE ISSUES.**

### Use Case 1: Fetching a Group's Feed (e.g., `/groups/{id}/feed`)

*   **RULE:** You **MUST** use `HangoutPointer` records to build this view.
*   **METHOD:** Perform a **Query** on the **main table** (not a GSI).
    *   `PK` = `GROUP#{groupId}`
    *   `SK` = `begins_with("HANGOUT#")`
*   **CRITICAL WARNING:** **NEVER** loop through these results and fetch the full `Hangout` canonical record (e.g., by calling `HangoutService.getHangoutDetail()`). The pointer record is designed to contain all the data needed for the list view. If a piece of data is missing, the pointer record **MUST** be updated (see Section 5).

### Use Case 2: Fetching a User's Complete Hangout List

*   **RULE:** You **MUST** use the `EntityTimeIndex` GSI. This is the only way to efficiently get hangouts from all of a user's groups plus direct invites in chronological order.
*   **METHOD:**
    1.  Get the user's ID and the list of all groups they are a member of.
    2.  Construct a list of partition keys to query: `USER#{userId}`, `GROUP#{groupId_A}`, `GROUP#{groupId_B}`, etc.
    3.  Execute parallel **Queries** against the **`EntityTimeIndex`** for each of those partition keys.
    4.  Use a sort key condition to get upcoming events: `startTimestamp > :current_timestamp`.
    5.  Merge the results from the parallel queries.

### Use Case 3: Fetching Full Details for ONE Hangout

*   **RULE:** Use this pattern **ONLY** when viewing a single hangout's detail page.
*   **METHOD:** Fetch the single **Canonical Record** from the main table.
    *   `PK` = `EVENT#{hangoutId}`
    *   `SK` = `METADATA`

---

## 5. Write & Update Patterns (Critical Rules)

### Rule 1: Canonical First, Pointers Second

When creating or updating data that is denormalized, you **MUST** write to the canonical record first, then update all associated pointer records.

*   **Example (`updateHangoutTitle`):**
    1.  Update the `title` on the `EVENT#{hangoutId}` canonical record.
    2.  Get the list of associated groups from the canonical record.
    3.  Loop through the group IDs and update the `title` attribute on each corresponding `GROUP#{groupId}` -> `HANGOUT#{hangoutId}` pointer record.

### Rule 2: Denormalization is Your Responsibility

If you need a new field to be displayed in a list view (like a group feed), you **MUST** add the new field to the `HangoutPointer` entity.

1.  Add the attribute to the `HangoutPointer.java` class.
2.  Modify the write/update logic (e.g., in `HangoutServiceImpl`) to denormalize the data from the canonical record to the pointer record whenever a hangout is created or updated.
3.  **DO NOT** break the pointer pattern by being lazy and fetching the full record in the read path.

### Rule 3: GSI Keys Are Not Optional

For an item to appear in a GSI query, it **MUST** have the GSI's partition key and sort key attributes populated. When creating `HangoutPointer` records, it is **CRITICAL** that you set `gsi1pk` and `startTimestamp`.

---

## 6. Summary of Critical "Gotchas"

*   **NEVER call a "get detail" service method inside a loop.** This is the N+1 anti-pattern and will be caught in code reviews.
*   **ALWAYS update all pointer records** after a canonical record is changed. Stale pointer data is a critical bug.
*   **VERIFY GSI keys (`gsi1pk`, `startTimestamp`) are populated** on all `HangoutPointer` writes.
*   **USE the `EntityTimeIndex`** for user-centric, time-sorted hangout lists.
*   **USE the main table `begins_with` query** for displaying the feed of a single group.
