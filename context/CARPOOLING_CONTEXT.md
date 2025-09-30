# Context: Carpooling

**AUDIENCE:** This document is for developers and AI agents working on the carpooling feature. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md` and `HANGOUT_CRUD_CONTEXT.md`.

## 1. Overview

The Carpooling feature allows users attending a hangout to either offer seats in their car or request a ride. The entire data model is contained within a Hangout's item collection, ensuring data is retrieved efficiently along with the rest of the event details.

All carpool-related entities share the same partition key as their parent hangout (`PK=EVENT#{hangoutId}`) but have distinct sort keys.

## 2. Data Model & Key Structure

| Entity | Sort Key (SK) Structure | Purpose |
| :--- | :--- | :--- |
| `Car` | `CAR#{driverId}` | Represents a car being offered by a driver, including its total capacity and available seats. |
| `CarRider` | `CAR#{driverId}#RIDER#{riderId}` | Links a specific user (rider) to a specific car, signifying a reserved seat. |
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
| `CarWithRidersDTO.java` | A DTO used to return a car and its list of confirmed riders. |

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
2.  **Service:** `CarpoolServiceImpl.reserveSeat()` orchestrates the process:
    *   It first validates that the user can view the event and that the target car has available seats.
    *   It creates a `CarRider` entity to represent the reservation.
    *   It decrements the `availableSeats` count on the `Car` entity.
    *   It deletes the user's `NeedsRide` record, as they have now found a ride.
3.  **Repository:** The service calls the `HangoutRepository` multiple times to save the `CarRider`, update the `Car`, and delete the `NeedsRide` record. These are currently separate calls and not executed in a single atomic transaction.

### Getting Carpool State for a Hangout

1.  **Trigger:** A client requests the details for a hangout (e.g., `GET /hangouts/{hangoutId}`).
2.  **Repository:** `HangoutRepositoryImpl.getHangoutDetailData()` executes its standard item collection query (`PK = EVENT#{hangoutId}`).
3.  **Result:** This single query returns the `Hangout` record itself, plus all `Poll`, `Car`, `CarRider`, and `NeedsRide` items associated with it.
4.  **Service-Layer Assembly:** `CarpoolServiceImpl.getEventCars()` receives this collection of items and assembles the `CarWithRidersDTO` list in application memory before returning it.
