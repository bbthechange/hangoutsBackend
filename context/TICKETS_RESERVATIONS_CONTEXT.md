# Context: Ticket Coordination & Reservations

**AUDIENCE:** This document is for developers and AI agents working on the ticket coordination and reservations feature. It assumes familiarity with `DATABASE_ARCHITECTURE_CRITICAL.md` and `HANGOUT_CRUD_CONTEXT.md`.

## 1. Overview

The Ticket Coordination & Reservations feature enables users to coordinate ticket purchases and venue reservations for hangouts. Users can indicate whether they need tickets, confirm they've purchased tickets, record seating information, offer to buy tickets in batches for others, or manage reservations with capacity limits (like booking a karaoke room for 5 people).

All ticket-related entities share the same partition key as their parent hangout (`PK=EVENT#{hangoutId}`), ensuring efficient single-partition queries that retrieve all hangout data including participations and offers.

**Key Features:**
- Track who needs/has tickets with section/seat details
- Batch ticket purchase offers with buy-by deadlines
- Capacity-limited reservations with atomic claim/unclaim operations
- Denormalized summaries in group feeds for quick coordination visibility
- Hangout-level ticket metadata (link, required flag, discount code)

## 2. Data Model & Key Structure

| Entity | Sort Key (SK) | Purpose |
| :--- | :--- | :--- |
| `Participation` | `PARTICIPATION#{participationId}` | Records a user's ticket/reservation status (needs ticket, has ticket, extra ticket, claimed spot, or section preference) |
| `ReservationOffer` | `RESERVEOFFER#{offerId}` | An offer to buy tickets in batch or book a reservation with optional capacity limits |
| `Hangout` | `METADATA` | Enhanced with ticket fields: `ticketLink`, `ticketsRequired`, `discountCode` |

### Participation Types

| Type | Description | Use Case |
| :--- | :--- | :--- |
| `TICKET_NEEDED` | User needs a ticket | "I want to go but haven't bought a ticket yet" |
| `TICKET_PURCHASED` | User has purchased a ticket | "I have my ticket, sitting in Section 102" |
| `TICKET_EXTRA` | User has an extra ticket available | "I have 2 extra tickets if anyone needs them" |
| `SECTION` | User's seating preference | "I prefer to sit in the balcony section" |
| `CLAIMED_SPOT` | User claimed a spot in a capacity-limited offer | "I'm one of the 5 people in the karaoke room" |

### Reservation Offer Types

| Type | Description | Use Case |
| :--- | :--- | :--- |
| `TICKET` | Batch ticket purchase offer | "I'm buying tickets tomorrow, who needs one?" |
| `RESERVATION` | Venue/experience reservation | "Booking a karaoke room for up to 8 people" |

### Reservation Offer Status

| Status | Description |
| :--- | :--- |
| `COLLECTING` | Default state, still accepting commitments |
| `COMPLETED` | Offer fulfilled, tickets purchased or reservation made |
| `CANCELLED` | Offer was cancelled |

## 3. Key Files & Classes

### Controllers
| File | Purpose |
| :--- | :--- |
| `ParticipationController.java` | REST endpoints for `/hangouts/{hangoutId}/participations` |
| `ReservationOfferController.java` | REST endpoints for `/hangouts/{hangoutId}/reservation-offers` including special transaction endpoints (claim, unclaim, complete) |
| `HangoutController.java` | PATCH endpoint for updating hangout ticket fields |

### Services
| File | Purpose |
| :--- | :--- |
| `ParticipationServiceImpl.java` | Business logic for participation CRUD, user denormalization, pointer sync |
| `ReservationOfferServiceImpl.java` | Business logic for offer CRUD, complex transactions (claim/unclaim with retry, batched complete), pointer sync |
| `HangoutServiceImpl.java` | Includes `updateHangout()` method that handles ticket field updates and pointer sync |

### Repositories
| File | Purpose |
| :--- | :--- |
| `ParticipationRepositoryImpl.java` | DynamoDB operations for participations using Enhanced Client |
| `ReservationOfferRepositoryImpl.java` | DynamoDB operations for offers using Enhanced Client |
| `HangoutRepositoryImpl.java` | Enhanced to fetch participations and offers in `getHangoutDetailData()` |

### Entities
| File | Purpose |
| :--- | :--- |
| `Participation.java` | `@DynamoDbBean` with fields: participationId, userId, type, section, seat, reservationOfferId |
| `ReservationOffer.java` | `@DynamoDbBean` with fields: offerId, userId, type, status, capacity, claimedSpots, buyDate (TimeInfo), section, completedDate, ticketCount, totalPrice, version (optimistic locking) |

### DTOs
| File | Purpose |
| :--- | :--- |
| `ParticipationDTO.java` | API response with denormalized user info (displayName, mainImagePath) |
| `ReservationOfferDTO.java` | API response with denormalized user info |
| `ParticipationSummaryDTO.java` | Denormalized summary for HangoutPointer with grouped user lists |
| `UserSummary.java` | Lightweight user representation (userId, displayName, mainImagePath) |
| `HangoutSummaryDTO.java` | Enhanced to include `participationSummary`, `ticketLink`, `ticketsRequired`, `discountCode` |

### Request DTOs
| File | Purpose |
| :--- | :--- |
| `CreateParticipationRequest.java` | type (required), section, seat, reservationOfferId |
| `UpdateParticipationRequest.java` | All fields optional, uses `hasUpdates()` |
| `CreateReservationOfferRequest.java` | type (required), buyDate, section, capacity, status |
| `UpdateReservationOfferRequest.java` | All fields optional, uses `hasUpdates()` |
| `CompleteReservationOfferRequest.java` | convertAll (boolean), participationIds (list), ticketCount, totalPrice |
| `UpdateHangoutRequest.java` | Enhanced with ticketLink, ticketsRequired, discountCode |

## 4. Core Flows

### Creating a Participation

1. **Endpoint:** `POST /hangouts/{hangoutId}/participations`
2. **Authorization:** User must be member of any group associated with hangout
3. **Service:** `ParticipationServiceImpl.createParticipation()`
   - Verifies user access via `hangoutService.verifyUserCanAccessHangout()`
   - Gets user for denormalization (displayName, mainImagePath) from UserCache
   - Creates Participation entity with UUID
   - Saves to repository
   - **Calls pointer sync:** `updatePointersWithParticipationData()`
4. **Repository:** `ParticipationRepositoryImpl.save()` uses Enhanced Client `putItem`
5. **Pointer Sync:** Updates `participationSummary` in all HangoutPointers for associated groups

### Creating a Reservation Offer

1. **Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers`
2. **Authorization:** User must be member of any group associated with hangout
3. **Service:** `ReservationOfferServiceImpl.createOffer()`
   - Verifies user access
   - Gets user for denormalization from UserCache
   - Creates ReservationOffer entity (version starts at null, DynamoDB sets to 1 on first save)
   - Saves to repository
   - **Calls pointer sync:** `updatePointersWithParticipationData()`
4. **Repository:** `ReservationOfferRepositoryImpl.save()` uses Enhanced Client `putItem`
5. **Response:** `ReservationOfferDTO` with denormalized user info

### Claiming a Spot (Atomic Transaction)

**Critical operation with capacity checking and optimistic locking.**

1. **Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot`
2. **Service:** `ReservationOfferServiceImpl.claimSpot()`
3. **Retry Loop:** Up to 5 attempts for version conflicts
4. **Validations:**
   - **CRITICAL:** Throws `IllegalOperationException` if capacity is null (unlimited offers don't use claim-spot)
   - Pre-transaction check: `claimedSpots >= capacity` throws `CapacityExceededException`
5. **Transaction (2 items):**
   - **Item 1:** Update ReservationOffer
     - Increment `claimedSpots` by 1
     - Increment `version` by 1
     - Condition: `claimedSpots < capacity AND version = expectedVersion`
   - **Item 2:** Put Participation (type=CLAIMED_SPOT, reservationOfferId=offerId)
6. **Error Handling:**
   - If transaction fails, refetch offer to check if capacity actually exceeded
   - If capacity exceeded: throw `CapacityExceededException`
   - If version conflict: retry (up to MAX_RETRIES)
7. **On Success:** Calls pointer sync, returns `ParticipationDTO`

### Unclaiming a Spot (Atomic Transaction)

1. **Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot`
2. **Service:** `ReservationOfferServiceImpl.unclaimSpot()`
3. **Find User's Claim:** Queries participations by offerId, filters for userId + CLAIMED_SPOT type
4. **Retry Loop:** Up to 5 attempts for version conflicts
5. **Transaction (2 items):**
   - **Item 1:** Update ReservationOffer (decrement claimedSpots, increment version)
   - **Item 2:** Delete Participation (user's CLAIMED_SPOT)
6. **On Success:** Calls pointer sync

### Completing an Offer (Batched Transactions)

**Marks offer as complete and optionally converts TICKET_NEEDED → TICKET_PURCHASED.**

1. **Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/complete`
2. **Service:** `ReservationOfferServiceImpl.completeOffer()`
3. **Get Participations to Convert:**
   - If `convertAll=true`: All TICKET_NEEDED participations linked to this offer
   - If `convertAll=false`: Specified participationIds (must provide list)
4. **Update Offer First:**
   - Set status=COMPLETED
   - Set completedDate=now
   - Set ticketCount and totalPrice (optional fields)
   - Save via Enhanced Client (version auto-incremented)
5. **Batch Convert Participations:**
   - Split into batches of 90 (DynamoDB 100-item transaction limit)
   - For each batch: `TransactWriteItems` to update type=TICKET_PURCHASED
   - Uses low-level DynamoDB client (not Enhanced Client) for transactions
6. **On Success:** Calls pointer sync, returns `ReservationOfferDTO`

### Updating Hangout Ticket Fields

1. **Endpoint:** `PATCH /hangouts/{hangoutId}`
2. **Request Fields:** ticketLink, ticketsRequired, discountCode (all optional)
3. **Service:** `HangoutServiceImpl.updateHangout()`
   - Checks each field: `if (request.getTicketLink() != null && !request.getTicketLink().equals(hangout.getTicketLink()))`
   - Sets `needsPointerUpdate = true` if any ticket field changes
   - Updates canonical Hangout
   - If `needsPointerUpdate`: calls `updatePointersWithBasicFields(hangout)`
4. **Pointer Sync:** `updatePointersWithBasicFields()` updates ticketLink, ticketsRequired, discountCode on all HangoutPointers
5. **Group Timestamps:** Updates lastHangoutModified for cache invalidation

## 5. Pointer Synchronization Pattern

**Critical for group feed performance.** When participations or offers change, their summary must be denormalized to all HangoutPointers for associated groups.

### When Pointer Sync Happens

After EVERY participation/offer operation:
- Create participation
- Update participation
- Delete participation
- Create offer
- Update offer
- Delete offer
- Complete offer
- Claim spot
- Unclaim spot

### Sync Implementation (in both ParticipationServiceImpl and ReservationOfferServiceImpl)

```java
private void updatePointersWithParticipationData(String hangoutId, String userId) {
    // 1. Get ALL hangout data in one call (single partition query)
    HangoutDetailDTO detail = hangoutService.getHangoutDetail(hangoutId, userId);

    // 2. Get participations and offers (already have denormalized user info)
    List<ParticipationDTO> participations = detail.getParticipations();
    List<ReservationOfferDTO> offers = detail.getReservationOffers();

    // 3. Build ParticipationSummaryDTO
    ParticipationSummaryDTO summary = buildParticipationSummary(participations, offers);

    // 4. Update ALL HangoutPointers (one per associated group)
    for (String groupId : associatedGroups) {
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
            pointer.setParticipationSummary(summary);
            pointer.setTicketLink(hangout.getTicketLink());
            pointer.setTicketsRequired(hangout.getTicketsRequired());
            pointer.setDiscountCode(hangout.getDiscountCode());
        }, "participation/offer data");
    }

    // 5. Update group timestamps for ETag invalidation
    groupTimestampService.updateGroupTimestamps(associatedGroups);
}
```

### Building ParticipationSummaryDTO

```java
private ParticipationSummaryDTO buildParticipationSummary(
    List<ParticipationDTO> participations,
    List<ReservationOfferDTO> offers
) {
    // Group by type
    Map<ParticipationType, List<ParticipationDTO>> byType =
        participations.stream().collect(Collectors.groupingBy(ParticipationDTO::getType));

    // Build deduplicated user lists (user with 2 tickets appears once)
    summary.setUsersNeedingTickets(buildUserList(byType.get(TICKET_NEEDED)));
    summary.setUsersWithTickets(buildUserList(byType.get(TICKET_PURCHASED)));
    summary.setUsersWithClaimedSpots(buildUserList(byType.get(CLAIMED_SPOT)));

    // Count extras (no user list needed)
    summary.setExtraTicketCount(count of TICKET_EXTRA);

    // Include ALL reservation offers
    summary.setReservationOffers(offers);
}
```

### User Deduplication

```java
private List<UserSummary> buildUserList(List<ParticipationDTO> participations) {
    // Uses map to deduplicate by userId
    return participations.stream()
        .collect(Collectors.toMap(
            ParticipationDTO::getUserId,
            p -> new UserSummary(p.getUserId(), p.getDisplayName(), p.getMainImagePath()),
            (existing, replacement) -> existing  // Keep first
        ))
        .values().stream().collect(Collectors.toList());
}
```

## 6. Authorization Rules

**Uniform across all operations:** User must be a member of ANY group associated with the hangout.

This allows any group member to help coordinate tickets/reservations (not restricted to host/owner).

**Implementation:**
- All operations call `hangoutService.verifyUserCanAccessHangout(hangoutId, userId)`
- Throws `UnauthorizedException` if user not in any associated group
- No ownership restrictions (unlike some other features)

**Rationale:** Ticket coordination is collaborative. Any group member can:
- Create participations for themselves or see others' status
- Create/update/complete offers
- Claim/unclaim spots
- Delete participations or offers

## 7. Performance Optimizations

### User Caching (Critical)

**Problem:** Every participation/offer operation fetches users for denormalization (N+1 query problem).

**Solution:** `UserCache` service caches users in memory.

**Usage in Services:**
```java
User user = userCache.getUser(userId)
    .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
```

**Cache Behavior:**
- Implemented in `UserCache.java`
- Reduces user lookup latency from database to in-memory
- Used in both ParticipationServiceImpl and ReservationOfferServiceImpl

### Single Partition Query

**Critical Pattern:** `getHangoutDetail()` fetches ALL hangout data (including participations and offers) in ONE DynamoDB query.

**Implementation in HangoutRepositoryImpl:**
```java
QueryRequest request = QueryRequest.builder()
    .tableName(TABLE_NAME)
    .keyConditionExpression("pk = :pk")
    .expressionAttributeValues(Map.of(
        ":pk", AttributeValue.builder().s("EVENT#{hangoutId}").build()
    ))
    .build();
```

**Items Retrieved:**
- Hangout metadata
- Polls, PollOptions, Votes
- Cars, CarRiders, NeedsRide
- Participations (NEW)
- ReservationOffers (NEW)
- InterestLevels, HangoutAttributes

**Sorting:** Uses `InviterKeyFactory.isParticipation(sk)` and `InviterKeyFactory.isReservationOffer(sk)` to identify items.

### Batched Transactions

**Problem:** DynamoDB transaction limit is 100 items.

**Solution:** Complete-offer operation splits participation updates into batches of 90.

```java
int batchSize = 90;
for (int i = 0; i < participationsToConvert.size(); i += batchSize) {
    List<Participation> batch = participationsToConvert.subList(
        i, Math.min(i + batchSize, participationsToConvert.size())
    );

    // Build TransactWriteItems for this batch
    dynamoDbClient.transactWriteItems(...);
}
```

**Accepts Eventual Consistency:** Batches are not atomic with each other (acceptable tradeoff).

## 8. Database Patterns

### Optimistic Locking

**Used in:** ReservationOffer for claim-spot/unclaim-spot operations.

**Implementation:**
- Field: `private Long version;` with `@DynamoDbVersionAttribute` annotation
- Enhanced Client auto-increments on save
- Manual version checking in transactions

**Transaction Condition Example:**
```java
.conditionExpression("version = :expectedVersion AND claimedSpots < :capacity")
```

**Retry Pattern:**
```java
for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
        // Fetch fresh version
        ReservationOffer offer = repository.findById(...);

        // Build transaction with version check
        TransactWriteItems...

        // Success - exit loop
        return result;
    } catch (TransactionCanceledException e) {
        // Check if capacity or version issue
        if (attempt < MAX_RETRIES) continue;
        throw new RuntimeException("Max retries exceeded");
    }
}
```

### Sort Key Queries

**Find all participations for a hangout:**
```java
QueryConditional conditional = QueryConditional.sortBeginsWith(
    Key.builder()
        .partitionValue("EVENT#{hangoutId}")
        .sortValue("PARTICIPATION#")
        .build()
);
```

**Find participations by offer (in-memory filter):**
```java
List<Participation> allParticipations = findByHangoutId(hangoutId);
List<Participation> filtered = allParticipations.stream()
    .filter(p -> offerId.equals(p.getReservationOfferId()))
    .collect(Collectors.toList());
```

**Rationale:** All participations are in same partition, so in-memory filter after single query is efficient.

## 9. Key Business Rules

### Capacity-Limited Offers

1. **Null capacity = unlimited** (no claim-spot, create participations directly)
2. **Claim-spot requires non-null capacity** (throws `IllegalOperationException` otherwise)
3. **Atomic increment with condition** (prevents over-booking)
4. **Pre-check before transaction** (fail fast for better UX)
5. **Post-transaction verification** (distinguish capacity vs version errors)

### Completing Offers

1. **convertAll=true:** Converts ALL TICKET_NEEDED participations linked to offer
2. **convertAll=false:** Requires participationIds list (validates not empty)
3. **Batching:** Splits into 90-item batches for DynamoDB transaction limits
4. **Optional fields:** ticketCount and totalPrice are optional on completion
5. **Status change:** COLLECTING → COMPLETED (irreversible in current implementation)

### Participation Types

1. **TICKET_NEEDED:** Default for "I want to attend but need a ticket"
2. **TICKET_PURCHASED:** Can be set directly OR via complete-offer conversion
3. **TICKET_EXTRA:** Count only (no user list in summary)
4. **SECTION:** Preference, not tracked in summary (future use)
5. **CLAIMED_SPOT:** Created atomically via claim-spot transaction only

## 10. API Response Patterns

### Individual Hangout Detail

**GET /hangouts/{hangoutId}** returns:
```json
{
  "hangout": { ... },
  "participations": [
    {
      "participationId": "uuid",
      "userId": "uuid",
      "displayName": "Alice",
      "mainImagePath": "users/...",
      "type": "TICKET_NEEDED",
      "section": "Balcony",
      "seat": null,
      "reservationOfferId": "uuid"
    }
  ],
  "reservationOffers": [
    {
      "offerId": "uuid",
      "userId": "uuid",
      "displayName": "Bob",
      "mainImagePath": "users/...",
      "type": "TICKET",
      "status": "COLLECTING",
      "capacity": null,
      "claimedSpots": 0,
      "buyDate": { "textInput": "tomorrow", ... },
      "section": "Floor",
      "remainingSpots": null
    }
  ],
  ...
}
```

### Group Feed Summary

**GET /groups/{groupId}/feed** returns HangoutSummaryDTO with:
```json
{
  "hangoutId": "uuid",
  "title": "Concert",
  "participationSummary": {
    "usersNeedingTickets": [
      { "userId": "uuid", "displayName": "Alice", "mainImagePath": "..." }
    ],
    "usersWithTickets": [
      { "userId": "uuid", "displayName": "Bob", "mainImagePath": "..." }
    ],
    "usersWithClaimedSpots": [
      { "userId": "uuid", "displayName": "Carol", "mainImagePath": "..." }
    ],
    "extraTicketCount": 2,
    "reservationOffers": [
      { /* full ReservationOfferDTO */ }
    ]
  },
  "ticketLink": "https://ticketmaster.com/...",
  "ticketsRequired": true,
  "discountCode": "GROUP20"
}
```

## 11. Error Handling

### Custom Exceptions

| Exception | HTTP Status | When Thrown |
| :--- | :--- | :--- |
| `ParticipationNotFoundException` | 404 | Participation ID not found |
| `ReservationOfferNotFoundException` | 404 | Offer ID not found |
| `CapacityExceededException` | 409 Conflict | Offer is full (claimedSpots >= capacity) |
| `IllegalOperationException` | 400 Bad Request | Claim-spot on unlimited capacity offer |
| `ValidationException` | 400 Bad Request | participationIds required when convertAll=false |
| `UnauthorizedException` | 403 Forbidden | User not in any associated group |

### Exception Handlers (in BaseController)

```java
@ExceptionHandler(CapacityExceededException.class)
public ResponseEntity<ErrorResponse> handleCapacityExceeded(CapacityExceededException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("CAPACITY_EXCEEDED", e.getMessage()));
}
```

## 12. Testing Considerations

### Unit Tests

**Critical test cases:**
1. Claim-spot with capacity exactly at limit (should fail)
2. Unclaim-spot when not claimed (should throw NotFoundException)
3. Complete-offer with convertAll=false but empty participationIds (should throw ValidationException)
4. Claim-spot with null capacity (should throw IllegalOperationException)
5. Batched complete with >100 participations (verify batching)

### Integration Tests

See `RESERVATION_OFFER_INTEGRATION_TESTS.md` for comprehensive integration test scenarios.

## 13. Future Expansion Possibilities

**Documented but NOT implemented:**

### Participation Fields
- `purchasedBy`: User ID who purchased the ticket (for batch purchases)
- `ticketHolder`: User ID for TICKET_EXTRA assigned to specific person

### Features
- Actual ticket storage (QR codes, PDFs)
- Payment integration
- Ticket transfer between users
- Offer reminders (buyDate notifications)

**Do NOT implement these without explicit requirements.**

## 14. Related Documentation

- **DATABASE_ARCHITECTURE_CRITICAL.md** - Pointer pattern and denormalization strategy
- **HANGOUT_CRUD_CONTEXT.md** - Hangout entity and pointer sync patterns
- **DYNAMODB_EXPERT_GUIDE.md** - Single-partition queries and transaction patterns
- **RESERVATION_OFFER_INTEGRATION_TESTS.md** - Integration test scenarios
- **TICKETS_DENORMALIZATION_STRATEGY.md** - Detailed pointer sync design (historical reference)
