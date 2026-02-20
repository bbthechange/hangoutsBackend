# Context: Carpooling

**AUDIENCE:** This document is for developers and AI agents working on the carpooling feature. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md` and `HANGOUT_CRUD_CONTEXT.md`.

## 1. Overview

The Carpooling feature allows users attending a hangout to either offer seats in their car or request a ride. The entire data model is contained within a Hangout's item collection, ensuring data is retrieved efficiently along with the rest of the event details.

All carpool-related entities share the same partition key as their parent hangout (`PK=EVENT#{hangoutId}`) but have distinct sort keys.

## 2. Data Model & Key Structure

| Entity | Sort Key (SK) Structure | Purpose |
| :--- | :--- | :--- |
| `Car` | `CAR#{driverId}` | Represents a car being offered by a driver, including its total capacity and available seats. |
| `CarRider` | `CAR#{driverId}#RIDER#{riderId}` | Links a specific user (rider) to a specific car, signifying a reserved seat. Stores optional `notes` (String) and `plusOneCount` (int, default 0) representing additional passengers. `getTotalSeatsNeeded()` returns `1 + plusOneCount`. |
| `NeedsRide` | `NEEDS_RIDE#{userId}` | A flag-like item indicating a user needs a ride but has not yet reserved a seat. |

This hierarchical key structure allows for efficient queries. For example, one can fetch a car and all its riders using a `begins_with(CAR#{driverId})` condition on the sort key.

## 3. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `CarpoolController.java` | Exposes REST endpoints for all carpooling actions, nested under `/events/{eventId}/carpool`. |
| `CarpoolServiceImpl.java` | Implements the business logic for offering cars, reserving seats, and managing ride requests. |
| `HangoutRepositoryImpl.java` | Contains the methods (`saveCar`, `saveCarRider`, `saveNeedsRide`, etc.) that persist carpool entities to DynamoDB. |
| `Car.java` | The `@DynamoDbBean` for a car offer. |
| `CarRider.java` | The `@DynamoDbBean` for a seat reservation. |
| `NeedsRide.java` | The `@DynamoDbBean` for a user's ride request. |
| `CarWithRidersDTO.java` | A DTO used to return a car and its list of confirmed riders. Includes `driverImagePath`. |
| `RiderDTO.java` | A DTO for rider information within a car listing. Includes `riderImagePath`. |
| `CarDetailDTO.java` | A DTO for detailed car information with driver and rider details. Includes `driverImagePath`. |
| `RiderDetailDTO.java` | A DTO for detailed rider information. Includes `riderImagePath`. |
| `NeedsRideDTO.java` | A DTO for ride requests. Includes `displayName` and `mainImagePath`. |

## 4. Core Flows

### Offering a Car

1.  **Endpoint:** `POST /events/{eventId}/carpool/cars`
2.  **Controller:** `CarpoolController.offerCar()`
3.  **Service:** `CarpoolServiceImpl.offerCar()` creates a `Car` entity.
4.  **Repository:** `HangoutRepositoryImpl.saveCar()` saves the `Car` record to the hangout's item collection.

### Requesting a Ride

1.  **Endpoint:** `POST /events/{eventId}/carpool/riderequests`
2.  **Service:** `CarpoolServiceImpl.createNeedsRideRequest()` creates a `NeedsRide` entity.
3.  **Repository:** `HangoutRepositoryImpl.saveNeedsRide()` saves the record.

### Reserving a Seat

This is the key transactional flow.

1.  **Endpoint:** `POST /events/{eventId}/carpool/cars/{driverId}/reserve`
2.  **Request body (optional):** `ReserveSeatRequest` with `notes` (String, max 500 chars), `plusOneCount` (Integer, 0–7), and `riderId` (String, UUID format, optional). The body is `@RequestBody(required = false)` for backward compatibility — clients sending no body get the same behavior as before (1 seat, no notes, reserved for the caller).
3.  **Service:** `CarpoolServiceImpl.reserveSeat()` orchestrates the process:
    *   It extracts `notes`, `plusOneCount`, and `riderId` from the request (null-safe — null request treated as no notes, plusOneCount=0, riderId=caller).
    *   **Driver-on-behalf-of:** When `riderId` is provided and differs from the caller, the caller must be the driver of the target car. The rider must also have access to the event. This allows drivers to pull users from the "needs ride" list into their car.
    *   It calculates `seatsNeeded = 1 + effectivePlusOneCount`.
    *   It validates that the caller can view the event and that the target car has enough available seats (`availableSeats >= seatsNeeded`).
    *   It creates a `CarRider` entity with the effective rider's ID, display name, `notes`, and `plusOneCount`.
    *   It decrements `availableSeats` on the `Car` entity by `seatsNeeded`.
    *   It deletes the **rider's** `NeedsRide` record (not the caller's), as they have now found a ride.
4.  **Repository:** The service calls the `HangoutRepository` multiple times to save the `CarRider`, update the `Car`, and delete the `NeedsRide` record. These are currently separate calls and not executed in a single atomic transaction.

### Releasing a Seat

1.  **Endpoint:** `DELETE /events/{eventId}/carpool/cars/{driverId}/reserve`
2.  **Service:** `CarpoolServiceImpl.releaseSeat()` restores seats using the rider's `getTotalSeatsNeeded()` (1 + plusOneCount), not a hardcoded value of 1.

### Updating a Car Offer

1.  **Endpoint:** `PUT /events/{eventId}/carpool/cars/{driverId}`
2.  **Seat math:** When validating a capacity reduction, the service sums `getTotalSeatsNeeded()` across all riders (not just the rider count) to determine occupied seats. Capacity cannot be reduced below this total.

### Getting Carpool State for a Hangout

1.  **Trigger:** A client requests the details for a hangout (e.g., `GET /hangouts/{hangoutId}`).
2.  **Repository:** `HangoutRepositoryImpl.getHangoutDetailData()` executes its standard item collection query (`PK = EVENT#{hangoutId}`).
3.  **Result:** This single query returns the `Hangout` record itself, plus all `Poll`, `Car`, `CarRider`, and `NeedsRide` items associated with it.
4.  **Service-Layer Assembly:** `CarpoolServiceImpl.getEventCars()` receives this collection of items and assembles the `CarWithRidersDTO` list in application memory before returning it.

## 5. Profile Enrichment (Avatars & Display Names)

Carpool DTOs are enriched with profile data (avatar image paths, display names) at the service layer using `UserService.getUserSummary()`, which reads from a Caffeine cache (60-min TTL, 10k entries). This avoids storing stale profile data in DynamoDB and keeps DTOs up-to-date with current user profiles.

### Enrichment Points

| Method | DTO | Enriched Fields |
| :--- | :--- | :--- |
| `getEventCars()` | `CarWithRidersDTO` | `driverImagePath` on the car, `riderImagePath` on each `RiderDTO` |
| `getCarDetail()` | `CarDetailDTO` | `driverImagePath` on the car, `riderImagePath` on each `RiderDetailDTO` |
| `getNeedsRideRequests()` | `NeedsRideDTO` | `displayName` and `mainImagePath` on each ride request |

### Pattern

After constructing the DTO from the model data, the service calls `userService.getUserSummary(UUID)` for each participant. If the user is not found in the cache, the enriched fields remain `null` — the frontend handles this gracefully by showing a default avatar.

### Note on Denormalized Names

The `driverName` and `riderName` fields are still written to DynamoDB at creation time (in `offerCar()` and `reserveSeat()`). These denormalized names are used in pointer records and remain available even if the cache is cold. The `driverImagePath`/`riderImagePath` fields are **not** stored in DynamoDB — they are resolved at read time from the cache.
