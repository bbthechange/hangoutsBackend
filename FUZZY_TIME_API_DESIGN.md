# API Design for Hangouts with Fuzzy Time

This document specifies the API changes required to support creating and viewing hangouts with both fuzzy time periods (e.g., "tomorrow morning") and exact start/end times. This design is intended for the `/hangouts` endpoints and does not affect the legacy `/events` endpoints.

---

## Summary of Changes

1.  **Flexible Time Input**: The `POST /hangouts` and `PATCH /hangouts/{hangoutId}` endpoints will be modified to accept a single `timeInput` object. This object can contain either fuzzy time parameters or exact start/end time parameters.
2.  **UTC-Based Responses**: All `GET` requests that return hangout time information will do so using pure UTC timestamps in ISO 8601 format (ending in `Z`). This provides a consistent, timezone-agnostic source of truth for all clients.
3.  **New Options Endpoint**: A new `GET /hangouts/time-options` endpoint will be added to provide clients with the list of available fuzzy time granularities.

---

## 1. Create & Update Hangouts

These changes apply to `POST /hangouts` and `PATCH /hangouts/{hangoutId}`.

The request body will now feature a `timeInput` object instead of top-level `startTime` and `endTime` fields. The server will validate that the `timeInput` object contains one of the two valid structures below.

### Option A: Creating with Fuzzy Time

Used when the user selects a general period like "evening" or "weekend".

**Request Body Snippet:**
```json
{
  "name": "Team Dinner",
  "description": "Dinner to celebrate the project launch.",
  "timeInput": {
    "periodGranularity": "evening",
    "periodStart": "2025-08-05T19:00:00-04:00"
  },
  ...
}
```

### Option B: Creating with Exact Time

Used when the user specifies an exact start and end time.

**Request Body Snippet:**
```json
{
  "name": "Team Dinner",
  "description": "Dinner to celebrate the project launch.",
  "timeInput": {
    "startTime": "2025-08-05T19:15:00-04:00",
    "endTime": "2025-08-05T21:30:00-04:00"
  },
  ...
}
```

### Field Requirements:

-   **`periodGranularity`** (string): An enum-like key for the fuzzy period. Must be one of the values from the `/hangouts/time-options` endpoint.
-   **`periodStart`** (string): An ISO 8601 timestamp string **with timezone offset**. Represents the beginning of the fuzzy period.
-   **`startTime` / `endTime`** (string): An ISO 8601 timestamp string **with timezone offset**.

---

## 2. Get Hangout Details

This change applies to the response of `GET /hangouts/{hangoutId}`.

The response will return the original time input structure, but with all timestamps converted to pure UTC. This allows the client to reconstruct the user's original intent while also having a standardized time to display.

**`hangout.timeInput` Object in Response:**

```json
// Example for a hangout created with fuzzy time:
"timeInput": {
  "periodGranularity": "evening",
  "periodStart": "2025-08-05T23:00:00Z",
  "startTime": null,
  "endTime": null
}

// Example for a hangout created with exact time:
"timeInput": {
  "periodGranularity": null,
  "periodStart": null,
  "startTime": "2025-08-05T23:15:00Z",
  "endTime": "2025-08-06T01:30:00Z"
}
```

---

## 3. Get Group Feed

This change applies to the response of `GET /groups/{groupId}/feed`.

The `hangoutTime` field in the feed's hangout objects will be replaced with a `timeInfo` object that mirrors the structure from the detailed `GET /hangouts/{hangoutId}` response.

**`timeInfo` Object in Feed Response:**

```json
"timeInfo": {
  "periodGranularity": "evening", // or null
  "periodStart": "2025-08-05T23:00:00Z", // or null
  "startTime": null, // or a UTC timestamp string
  "endTime": null // or a UTC timestamp string
}
```

---

## 4. New Endpoint: Get Time Options

A new public endpoint will be created to provide clients with the list of available fuzzy time granularities. This allows the client to dynamically build its UI without hardcoding options.

**Endpoint:** `GET /hangouts/time-options`

**Authorization:** Public (no JWT required).

**Response (200 OK):**
```json
[
  "exact",
  "morning",
  "afternoon",
  "evening",
  "night",
  "day",
  "weekend"
]
```
