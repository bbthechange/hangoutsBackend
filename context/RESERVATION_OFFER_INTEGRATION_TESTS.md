# ReservationOffer API Integration Tests

This document specifies which tests from the manual curl testing should be automated as integration tests.

## Test Selection Criteria

Tests were selected based on:
1. **Core Business Logic**: Tests that verify critical functionality
2. **Non-Brittle**: Tests that verify stable contracts rather than implementation details
3. **Atomic Operations**: Tests that verify transactional consistency (claim/unclaim spot)
4. **Integration Points**: Tests that verify data flows between services (HangoutDetail inclusion)

---

## Recommended Integration Tests

### 1. Create Reservation Offer

**Endpoint**: `POST /hangouts/{hangoutId}/reservation-offers`

**Why Important**: Core CRUD operation, validates offer creation with proper user denormalization.

**Setup**:
- Authenticated user with JWT token
- Existing hangout in a group the user belongs to

**Request Body**:
```json
{
  "type": "TICKET",
  "section": "GA",
  "capacity": 10
}
```

**Assertions**:
- Response status 201 Created
- Response includes `offerId` (non-null UUID)
- `userId` matches authenticated user
- `displayName` is populated (denormalized from User)
- `mainImagePath` is populated (denormalized from User)
- `type` matches request ("TICKET")
- `section` matches request ("GA")
- `capacity` matches request (10)
- `claimedSpots` is 0
- `remainingSpots` equals capacity (10)
- `status` is "COLLECTING"
- `createdAt` and `updatedAt` are populated

**Teardown**: Delete the offer

---

### 2. Get Reservation Offers for Hangout

**Endpoint**: `GET /hangouts/{hangoutId}/reservation-offers`

**Why Important**: Verifies list retrieval and filtering by hangout.

**Setup**:
- Create 2 offers for test hangout

**Assertions**:
- Response status 200 OK
- Response is array with length 2
- Each offer has valid structure
- Both offers belong to test hangout

**Teardown**: Delete both offers

---

### 3. Update Reservation Offer

**Endpoint**: `PUT /hangouts/{hangoutId}/reservation-offers/{offerId}`

**Why Important**: Verifies partial updates work correctly.

**Setup**:
- Create an offer with `section: "GA"`, `capacity: 10`

**Request Body**:
```json
{
  "section": "VIP",
  "capacity": 15
}
```

**Assertions**:
- Response status 200 OK
- `section` is "VIP"
- `capacity` is 15
- `remainingSpots` is 15
- `version` incremented
- Other fields unchanged (type, status, etc.)

**Teardown**: Delete the offer

---

### 4. Claim Spot (Atomic Transaction Test)

**Endpoint**: `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot`

**Why Important**: Tests atomic transaction - both participation creation AND claimedSpots increment must succeed together.

**Setup**:
- Create offer with `capacity: 5`

**Assertions**:
- Response status 201 Created
- Response includes `participationId`
- `type` is "CLAIMED_SPOT"
- `reservationOfferId` matches the offer

**Post-Claim Verification** (GET the offer):
- `claimedSpots` is 1
- `remainingSpots` is 4

**Teardown**: Unclaim spot, delete offer

---

### 5. Unclaim Spot (Atomic Transaction Test)

**Endpoint**: `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot`

**Why Important**: Tests atomic transaction - both participation deletion AND claimedSpots decrement must succeed together.

**Setup**:
- Create offer with `capacity: 5`
- Claim a spot

**Pre-Test State** (GET the offer):
- `claimedSpots` is 1

**Assertions**:
- Response status 204 No Content

**Post-Unclaim Verification** (GET the offer):
- `claimedSpots` is 0
- `remainingSpots` is 5

**Teardown**: Delete offer

---

### 6. Complete Offer with Participation Conversion

**Endpoint**: `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/complete`

**Why Important**: Tests complex business logic - offer completion and batch participation conversion.

**Setup**:
1. Create offer with `capacity: 10`
2. Create 2 participations with `type: "TICKET_NEEDED"` linked to offer

**Request Body**:
```json
{
  "convertAll": true,
  "ticketCount": 5,
  "totalPrice": 250.00
}
```

**Assertions**:
- Response status 200 OK
- `status` is "COMPLETED"
- `completedDate` is populated (non-null timestamp)
- `ticketCount` is 5
- `totalPrice` is 250.00

**Post-Complete Verification** (GET participations):
- Both participations now have `type: "TICKET_PURCHASED"`

**Teardown**: Delete participations, delete offer

---

### 7. Hangout Detail Includes Participations and Offers

**Endpoint**: `GET /hangouts/{hangoutId}`

**Why Important**: Tests the new functionality - participations and offers are included in single-query hangout detail response.

**Setup**:
1. Create hangout
2. Create 1 reservation offer
3. Create 1 participation

**Assertions**:
- Response status 200 OK
- `participations` array has length 1
- `participations[0]` has `displayName` populated
- `participations[0]` has `mainImagePath` populated
- `reservationOffers` array has length 1
- `reservationOffers[0]` has `displayName` populated
- `reservationOffers[0]` has `mainImagePath` populated

**Teardown**: Delete participation, delete offer, delete hangout

---

### 8. Capacity Exceeded Error

**Endpoint**: `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot`

**Why Important**: Tests business rule enforcement - cannot exceed capacity.

**Setup**:
1. Create offer with `capacity: 1`
2. Claim the 1 available spot (as different user or use test helper)

**Assertions**:
- Response status 422 Unprocessable Entity
- Error code "CAPACITY_EXCEEDED"
- Error message contains capacity information

**Teardown**: Unclaim spot, delete offer

---

### 9. Authorization: Non-Member Cannot Access

**Endpoint**: `GET /hangouts/{hangoutId}/reservation-offers`

**Why Important**: Tests authorization - only group members can access hangout data.

**Setup**:
1. Create hangout in Group A
2. Authenticate as user who is NOT in Group A

**Assertions**:
- Response status 403 Forbidden

**Teardown**: None needed

---

## Test Data Requirements

### Test Users
- Primary test user: Phone `+19285251044`, ID `0ab8b8c1-b3b4-4b81-b938-d9cdebf63a20`
- Secondary test user (for multi-user scenarios): TBD

### Test Group
- Group ID: `2cbc0047-9b05-438c-ac50-9953ae8a81fb`

---

## Test Implementation Notes

### Setup/Teardown Pattern
```java
@BeforeEach
void setUp() {
    // 1. Create test hangout
    // 2. Store hangoutId for tests
}

@AfterEach
void tearDown() {
    // 1. Delete all participations for test hangout
    // 2. Delete all reservation offers for test hangout
    // 3. Delete test hangout
}
```

### Authentication Helper
```java
private String getAuthToken() {
    LoginResponse response = restTemplate.postForObject(
        "/auth/login",
        new LoginRequest(testPhone, testPassword),
        LoginResponse.class
    );
    return response.getAccessToken();
}
```

### Verification Pattern
For atomic operations (claim/unclaim), always verify both sides of the transaction:
1. Verify the direct response
2. GET the affected resources and verify state changes

---

## Tests NOT Recommended for Integration Suite

### Delete Offer
- **Reason**: Straightforward CRUD, covered by unit tests
- **Risk**: Setup complexity outweighs benefit

### Get Single Offer
- **Reason**: Simple retrieval, covered by list endpoint test
- **Risk**: Redundant with other tests

### Update with buyDate
- **Reason**: Same update mechanism as section/capacity
- **Risk**: Brittle date assertions

---

## Bug Found During Testing

**Issue**: `reservationOffers` was empty in hangout detail response.

**Root Cause**: Mismatch in `deserializeItem()` switch statement:
- Model used `itemType = "RESERVEOFFER"`
- Switch expected `case "RESERVATION_OFFER":`

**Fix**: Changed switch case to `case "RESERVEOFFER":`

**File**: `HangoutRepositoryImpl.java:151`

**Integration Test Coverage**: Test #7 would have caught this bug.
