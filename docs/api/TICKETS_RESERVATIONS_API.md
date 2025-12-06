# Ticket Coordination & Reservations API

**Version:** 1.0
**Base URL:** `https://api.inviter.app` (or your configured base URL)
**Authentication:** All endpoints require JWT Bearer token in Authorization header

## Overview

The Ticket Coordination & Reservations API enables users to coordinate ticket purchases and venue reservations for hangouts. Users can indicate ticket needs, confirm purchases, record seating information, offer batch ticket purchases, and manage capacity-limited reservations.

## Authorization

**All endpoints require:** User must be a member of ANY group associated with the hangout.

This allows any group member to help coordinate tickets and reservations collaboratively.

## Participation Types

| Type | Description |
| :--- | :--- |
| `TICKET_NEEDED` | User needs a ticket |
| `TICKET_PURCHASED` | User has purchased a ticket |
| `TICKET_EXTRA` | User has extra tickets available |
| `SECTION` | User's seating section preference |
| `CLAIMED_SPOT` | User claimed a spot in a capacity-limited reservation |

## Reservation Offer Types

| Type | Description |
| :--- | :--- |
| `TICKET` | Batch ticket purchase offer |
| `RESERVATION` | Venue/experience reservation (e.g., karaoke room) |

## Reservation Offer Status

| Status | Description |
| :--- | :--- |
| `COLLECTING` | Default state, accepting commitments |
| `COMPLETED` | Offer fulfilled, tickets purchased or reservation made |
| `CANCELLED` | Offer was cancelled |

---

## Hangout Ticket Fields

### Update Hangout Ticket Information

Updates ticket-related metadata for a hangout.

**Endpoint:** `PATCH /hangouts/{hangoutId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID

**Request Body:**
```json
{
  "ticketLink": "https://ticketmaster.com/event/123",
  "ticketsRequired": true,
  "discountCode": "GROUP20"
}
```

**Request Fields:**
- `ticketLink` (string, optional) - URL to ticket purchase page
- `ticketsRequired` (boolean, optional) - Are tickets mandatory to attend?
- `discountCode` (string, optional) - Discount code for ticket purchase

**Success Response:** `204 No Content`

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not authorized to edit hangout |
| 404 | HANGOUT_NOT_FOUND | Hangout does not exist |

---

## Participations

### List Participations

Retrieves all participations for a hangout.

**Endpoint:** `GET /hangouts/{hangoutId}/participations`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID

**Success Response:** `200 OK`
```json
[
  {
    "participationId": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
    "displayName": "Alice Smith",
    "mainImagePath": "users/7a9b2c3d.../profile.jpg",
    "type": "TICKET_NEEDED",
    "section": "Balcony",
    "seat": null,
    "reservationOfferId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "createdAt": "2025-11-24T10:30:00Z",
    "updatedAt": "2025-11-24T10:30:00Z"
  }
]
```

**Response Fields:**
- `participationId` (string, UUID) - Unique participation ID
- `userId` (string, UUID) - User this participation is for
- `displayName` (string) - User's display name (denormalized)
- `mainImagePath` (string) - User's profile image path (denormalized)
- `type` (string) - Participation type (see table above)
- `section` (string, nullable) - Seating section
- `seat` (string, nullable) - Specific seat number
- `reservationOfferId` (string, UUID, nullable) - Linked reservation offer
- `createdAt` (string, ISO 8601) - Creation timestamp
- `updatedAt` (string, ISO 8601) - Last update timestamp

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | HANGOUT_NOT_FOUND | Hangout does not exist |

---

### Create Participation

Creates a new participation record.

**Endpoint:** `POST /hangouts/{hangoutId}/participations`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID

**Request Body:**
```json
{
  "type": "TICKET_NEEDED",
  "section": "Floor",
  "seat": "A12",
  "reservationOfferId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"
}
```

**Request Fields:**
- `type` (string, **required**) - Participation type
- `section` (string, optional) - Seating section
- `seat` (string, optional) - Specific seat number
- `reservationOfferId` (string, UUID, optional) - Link to a reservation offer

**Success Response:** `201 Created`
```json
{
  "participationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Alice Smith",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "TICKET_NEEDED",
  "section": "Floor",
  "seat": "A12",
  "reservationOfferId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "createdAt": "2025-11-24T10:30:00Z",
  "updatedAt": "2025-11-24T10:30:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 400 | VALIDATION_ERROR | Missing required field or invalid value |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | HANGOUT_NOT_FOUND | Hangout does not exist |

---

### Get Participation

Retrieves a specific participation by ID.

**Endpoint:** `GET /hangouts/{hangoutId}/participations/{participationId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `participationId` (string, UUID) - The participation ID

**Success Response:** `200 OK`
```json
{
  "participationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Alice Smith",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "TICKET_NEEDED",
  "section": "Floor",
  "seat": "A12",
  "reservationOfferId": null,
  "createdAt": "2025-11-24T10:30:00Z",
  "updatedAt": "2025-11-24T10:30:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | PARTICIPATION_NOT_FOUND | Participation does not exist |

---

### Update Participation

Updates an existing participation. Any group member can update any participation.

**Endpoint:** `PUT /hangouts/{hangoutId}/participations/{participationId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `participationId` (string, UUID) - The participation ID

**Request Body:**
```json
{
  "type": "TICKET_PURCHASED",
  "section": "Balcony",
  "seat": "B15"
}
```

**Request Fields (all optional):**
- `type` (string) - Update participation type
- `section` (string) - Update seating section
- `seat` (string) - Update seat number

**Success Response:** `200 OK`
```json
{
  "participationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Alice Smith",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "TICKET_PURCHASED",
  "section": "Balcony",
  "seat": "B15",
  "reservationOfferId": null,
  "createdAt": "2025-11-24T10:30:00Z",
  "updatedAt": "2025-11-24T11:45:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 400 | VALIDATION_ERROR | No updates provided or invalid value |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | PARTICIPATION_NOT_FOUND | Participation does not exist |

---

### Delete Participation

Deletes a participation. Any group member can delete any participation.

**Endpoint:** `DELETE /hangouts/{hangoutId}/participations/{participationId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `participationId` (string, UUID) - The participation ID

**Success Response:** `204 No Content`

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |

**Note:** This operation is idempotent - succeeds even if participation doesn't exist.

---

## Reservation Offers

### List Reservation Offers

Retrieves all reservation offers for a hangout.

**Endpoint:** `GET /hangouts/{hangoutId}/reservation-offers`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID

**Success Response:** `200 OK`
```json
[
  {
    "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
    "displayName": "Bob Johnson",
    "mainImagePath": "users/7a9b2c3d.../profile.jpg",
    "type": "TICKET",
    "status": "COLLECTING",
    "buyDate": {
      "textInput": "tomorrow",
      "periodGranularity": "DAY",
      "periodStart": "2025-11-25T00:00:00Z"
    },
    "section": "Floor",
    "capacity": null,
    "claimedSpots": 0,
    "remainingSpots": null,
    "completedDate": null,
    "ticketCount": null,
    "totalPrice": null,
    "createdAt": "2025-11-24T10:00:00Z",
    "updatedAt": "2025-11-24T10:00:00Z"
  }
]
```

**Response Fields:**
- `offerId` (string, UUID) - Unique offer ID
- `userId` (string, UUID) - User who created the offer
- `displayName` (string) - Creator's display name (denormalized)
- `mainImagePath` (string) - Creator's profile image (denormalized)
- `type` (string) - TICKET or RESERVATION
- `status` (string) - COLLECTING, COMPLETED, or CANCELLED
- `buyDate` (object, nullable) - Deadline for commitments (TimeInfo format)
- `section` (string, nullable) - Target seating section
- `capacity` (integer, nullable) - Max spots (null = unlimited)
- `claimedSpots` (integer) - Current claimed count
- `remainingSpots` (integer, nullable) - Spots remaining (null if unlimited)
- `completedDate` (string, ISO 8601, nullable) - When offer was completed
- `ticketCount` (integer, nullable) - Number of tickets purchased
- `totalPrice` (number, nullable) - Total cost
- `createdAt` (string, ISO 8601) - Creation timestamp
- `updatedAt` (string, ISO 8601) - Last update timestamp

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | HANGOUT_NOT_FOUND | Hangout does not exist |

---

### Create Reservation Offer

Creates a new reservation offer.

**Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID

**Request Body:**
```json
{
  "type": "RESERVATION",
  "buyDate": {
    "textInput": "Thursday 5pm",
    "periodGranularity": "HOUR"
  },
  "section": "Karaoke Room A",
  "capacity": 8,
  "status": "COLLECTING"
}
```

**Request Fields:**
- `type` (string, **required**) - TICKET or RESERVATION
- `buyDate` (object, optional) - Deadline as TimeInfo object
  - `textInput` (string) - Human-readable time (e.g., "tomorrow", "Thursday 5pm")
  - `periodGranularity` (string) - DAY, HOUR, MINUTE, etc.
- `section` (string, optional) - Target section or room name
- `capacity` (integer, optional) - Max spots (null or omit for unlimited)
- `status` (string, optional) - Initial status (defaults to COLLECTING)

**Success Response:** `201 Created`
```json
{
  "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Bob Johnson",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "RESERVATION",
  "status": "COLLECTING",
  "buyDate": {
    "textInput": "Thursday 5pm",
    "periodGranularity": "HOUR",
    "periodStart": "2025-11-28T17:00:00Z"
  },
  "section": "Karaoke Room A",
  "capacity": 8,
  "claimedSpots": 0,
  "remainingSpots": 8,
  "completedDate": null,
  "ticketCount": null,
  "totalPrice": null,
  "createdAt": "2025-11-24T10:00:00Z",
  "updatedAt": "2025-11-24T10:00:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 400 | VALIDATION_ERROR | Missing required field or invalid value |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | HANGOUT_NOT_FOUND | Hangout does not exist |

---

### Get Reservation Offer

Retrieves a specific reservation offer by ID.

**Endpoint:** `GET /hangouts/{hangoutId}/reservation-offers/{offerId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `offerId` (string, UUID) - The offer ID

**Success Response:** `200 OK`
```json
{
  "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Bob Johnson",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "TICKET",
  "status": "COLLECTING",
  "buyDate": null,
  "section": "Floor",
  "capacity": null,
  "claimedSpots": 0,
  "remainingSpots": null,
  "completedDate": null,
  "ticketCount": null,
  "totalPrice": null,
  "createdAt": "2025-11-24T10:00:00Z",
  "updatedAt": "2025-11-24T10:00:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | OFFER_NOT_FOUND | Reservation offer does not exist |

---

### Update Reservation Offer

Updates an existing reservation offer. Any group member can update any offer.

**Endpoint:** `PUT /hangouts/{hangoutId}/reservation-offers/{offerId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `offerId` (string, UUID) - The offer ID

**Request Body:**
```json
{
  "buyDate": {
    "textInput": "tomorrow at 3pm",
    "periodGranularity": "HOUR"
  },
  "section": "VIP Section",
  "capacity": 10,
  "status": "COLLECTING"
}
```

**Request Fields (all optional):**
- `buyDate` (object) - Update deadline
- `section` (string) - Update section
- `capacity` (integer) - Update capacity
- `status` (string) - Update status

**Success Response:** `200 OK`
```json
{
  "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Bob Johnson",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "TICKET",
  "status": "COLLECTING",
  "buyDate": {
    "textInput": "tomorrow at 3pm",
    "periodGranularity": "HOUR",
    "periodStart": "2025-11-25T15:00:00Z"
  },
  "section": "VIP Section",
  "capacity": 10,
  "claimedSpots": 0,
  "remainingSpots": 10,
  "completedDate": null,
  "ticketCount": null,
  "totalPrice": null,
  "createdAt": "2025-11-24T10:00:00Z",
  "updatedAt": "2025-11-24T11:30:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 400 | VALIDATION_ERROR | No updates provided or invalid value |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | OFFER_NOT_FOUND | Reservation offer does not exist |

---

### Delete Reservation Offer

Deletes a reservation offer. Any group member can delete any offer.

**Endpoint:** `DELETE /hangouts/{hangoutId}/reservation-offers/{offerId}`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `offerId` (string, UUID) - The offer ID

**Success Response:** `204 No Content`

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |

**Note:** This operation is idempotent - succeeds even if offer doesn't exist.

---

### Complete Reservation Offer

Marks an offer as completed and optionally converts TICKET_NEEDED participations to TICKET_PURCHASED.

**Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/complete`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `offerId` (string, UUID) - The offer ID

**Request Body:**
```json
{
  "convertAll": true,
  "ticketCount": 12,
  "totalPrice": 480.00
}
```

**Request Fields:**
- `convertAll` (boolean, **required**) - Convert all TICKET_NEEDED or only specified ones?
- `participationIds` (array of UUID strings) - Required if `convertAll=false`
- `ticketCount` (integer, optional) - Number of tickets purchased
- `totalPrice` (number, optional) - Total cost

**Success Response:** `200 OK`
```json
{
  "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Bob Johnson",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "TICKET",
  "status": "COMPLETED",
  "buyDate": null,
  "section": "Floor",
  "capacity": null,
  "claimedSpots": 0,
  "remainingSpots": null,
  "completedDate": "2025-11-24T12:00:00Z",
  "ticketCount": 12,
  "totalPrice": 480.00,
  "createdAt": "2025-11-24T10:00:00Z",
  "updatedAt": "2025-11-24T12:00:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 400 | VALIDATION_ERROR | participationIds required when convertAll=false |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | OFFER_NOT_FOUND | Reservation offer does not exist |

---

### Claim a Spot

Atomically claims a spot in a capacity-limited reservation offer. Creates a CLAIMED_SPOT participation and increments claimedSpots.

**Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `offerId` (string, UUID) - The offer ID

**Request Body:** None

**Success Response:** `201 Created`
```json
{
  "participationId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
  "displayName": "Alice Smith",
  "mainImagePath": "users/7a9b2c3d.../profile.jpg",
  "type": "CLAIMED_SPOT",
  "section": null,
  "seat": null,
  "reservationOfferId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "createdAt": "2025-11-24T10:30:00Z",
  "updatedAt": "2025-11-24T10:30:00Z"
}
```

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 400 | ILLEGAL_OPERATION | Offer has unlimited capacity (null) - cannot claim spots |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | OFFER_NOT_FOUND | Reservation offer does not exist |
| 409 | CAPACITY_EXCEEDED | Offer is full (all spots claimed) |

**Note:** This operation uses optimistic locking with automatic retry (up to 5 attempts) to handle concurrent claims.

---

### Unclaim a Spot

Atomically unclaims a spot in a capacity-limited reservation offer. Deletes the user's CLAIMED_SPOT participation and decrements claimedSpots.

**Endpoint:** `POST /hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot`

**Path Parameters:**
- `hangoutId` (string, UUID) - The hangout ID
- `offerId` (string, UUID) - The offer ID

**Request Body:** None

**Success Response:** `204 No Content`

**Error Responses:**

| Status | Error Code | Description |
| :--- | :--- | :--- |
| 403 | UNAUTHORIZED | User not member of any associated group |
| 404 | NOT_FOUND | User has not claimed a spot in this reservation |
| 404 | OFFER_NOT_FOUND | Reservation offer does not exist |

**Note:** This operation uses optimistic locking with automatic retry (up to 5 attempts) to handle concurrent unclaims.

---

## Enhanced Hangout Detail Response

When retrieving hangout details via `GET /hangouts/{hangoutId}`, the response now includes participation and reservation offer data:

**Success Response:** `200 OK`
```json
{
  "hangout": {
    "hangoutId": "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e",
    "title": "Concert at Madison Square Garden",
    "ticketLink": "https://ticketmaster.com/event/123",
    "ticketsRequired": true,
    "discountCode": "GROUP20",
    ...
  },
  "participations": [
    {
      "participationId": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
      "displayName": "Alice Smith",
      "mainImagePath": "users/7a9b2c3d.../profile.jpg",
      "type": "TICKET_NEEDED",
      "section": "Floor",
      "seat": null,
      "reservationOfferId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "createdAt": "2025-11-24T10:30:00Z",
      "updatedAt": "2025-11-24T10:30:00Z"
    }
  ],
  "reservationOffers": [
    {
      "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
      "userId": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
      "displayName": "Bob Johnson",
      "mainImagePath": "users/8b9c0d1e.../profile.jpg",
      "type": "TICKET",
      "status": "COLLECTING",
      "buyDate": null,
      "section": "Floor",
      "capacity": null,
      "claimedSpots": 0,
      "remainingSpots": null,
      "completedDate": null,
      "ticketCount": null,
      "totalPrice": null,
      "createdAt": "2025-11-24T10:00:00Z",
      "updatedAt": "2025-11-24T10:00:00Z"
    }
  ],
  ...
}
```

---

## Enhanced Group Feed Response

When retrieving group feed via `GET /groups/{groupId}/feed`, each hangout summary now includes participation data:

**Success Response:** `200 OK`
```json
{
  "groupId": "c2d3e4f5-a6b7-8c9d-0e1f-2a3b4c5d6e7f",
  "withDay": [
    {
      "hangoutId": "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e",
      "title": "Concert at Madison Square Garden",
      "participationSummary": {
        "usersNeedingTickets": [
          {
            "userId": "7a9b2c3d-4e5f-6a7b-8c9d-0e1f2a3b4c5d",
            "displayName": "Alice Smith",
            "mainImagePath": "users/7a9b2c3d.../profile.jpg"
          },
          {
            "userId": "9d0e1f2a-3b4c-5d6e-7f8a-9b0c1d2e3f4a",
            "displayName": "Carol White",
            "mainImagePath": "users/9d0e1f2a.../profile.jpg"
          }
        ],
        "usersWithTickets": [
          {
            "userId": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
            "displayName": "Bob Johnson",
            "mainImagePath": "users/8b9c0d1e.../profile.jpg"
          }
        ],
        "usersWithClaimedSpots": [
          {
            "userId": "0e1f2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b",
            "displayName": "Dave Brown",
            "mainImagePath": "users/0e1f2a3b.../profile.jpg"
          }
        ],
        "extraTicketCount": 2,
        "reservationOffers": [
          {
            "offerId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
            "userId": "8b9c0d1e-2f3a-4b5c-6d7e-8f9a0b1c2d3e",
            "displayName": "Bob Johnson",
            "mainImagePath": "users/8b9c0d1e.../profile.jpg",
            "type": "TICKET",
            "status": "COLLECTING",
            "capacity": null,
            "claimedSpots": 0,
            "remainingSpots": null,
            ...
          }
        ]
      },
      "ticketLink": "https://ticketmaster.com/event/123",
      "ticketsRequired": true,
      "discountCode": "GROUP20",
      ...
    }
  ],
  "needsDay": [],
  "nextPageToken": null,
  "previousPageToken": null
}
```

**participationSummary Fields:**
- `usersNeedingTickets` - Array of UserSummary objects with type=TICKET_NEEDED (deduplicated by userId)
- `usersWithTickets` - Array of UserSummary objects with type=TICKET_PURCHASED (deduplicated by userId)
- `usersWithClaimedSpots` - Array of UserSummary objects with type=CLAIMED_SPOT (deduplicated by userId)
- `extraTicketCount` - Count of TICKET_EXTRA participations (no user list)
- `reservationOffers` - Array of all reservation offers (not filtered by status)

**Note:** Users appear only once in each list even if they have multiple participations of the same type.

---

## Common Error Response Format

All error responses follow this format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error description",
  "timestamp": "2025-11-24T10:30:00Z"
}
```

## HTTP Status Code Summary

| Status | Usage |
| :--- | :--- |
| 200 OK | Successful GET or PUT request |
| 201 Created | Successful POST request creating a resource |
| 204 No Content | Successful DELETE or PATCH request with no body |
| 400 Bad Request | Invalid request data or missing required fields |
| 403 Forbidden | User not authorized to access this resource |
| 404 Not Found | Resource does not exist |
| 409 Conflict | Resource state prevents operation (e.g., capacity exceeded) |

---

## Rate Limiting

*To be documented based on deployment configuration.*

## Changelog

**Version 1.0** (2025-11-24)
- Initial release of Ticket Coordination & Reservations API
