# Group Feed API Documentation

## GET /groups/{groupId}/feed

Retrieves a chronologically ordered feed of hangouts for a specific group. Returns both scheduled hangouts (with dates) and unscheduled ideas (needs day). All data is loaded from denormalized pointer records for optimal performance.

### Authentication
**Required**: JWT Bearer token

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `groupId` | String (UUID) | Yes | The unique identifier of the group |

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `limit` | Integer | No | None | Maximum number of hangouts to return per page (minimum: 1) |
| `startingAfter` | String | No | None | Pagination cursor for fetching future events (next page) |
| `endingBefore` | String | No | None | Pagination cursor for fetching past events (previous page) |

### Authorization Rules
- User must be a member of the group
- Returns `403 Forbidden` if user is not a group member
- Returns `404 Not Found` if group does not exist

### Response Structure

```typescript
{
  groupId: string,           // The group ID
  withDay: FeedItem[],       // Scheduled hangouts and series (chronologically ordered)
  needsDay: HangoutSummary[], // Unscheduled ideas
  nextPageToken?: string,    // Cursor for next page (future events)
  previousPageToken?: string // Cursor for previous page (past events)
}
```

### Hangout Summary Fields

Each hangout in `withDay` and `needsDay` contains:

| Field | Type | Description |
|-------|------|-------------|
| `hangoutId` | String (UUID) | Unique hangout identifier |
| `title` | String | Hangout title |
| `description` | String | Hangout description |
| `status` | String | Hangout status |
| `type` | String | Always "hangout" for individual events |
| `visibility` | String | "INVITE_ONLY" or "PUBLIC" |
| `carpoolEnabled` | Boolean | Whether carpooling is enabled |
| `timeInfo` | TimeInfo | Fuzzy time information for display |
| `location` | Address | Location details |
| `participantCount` | Integer | Number of participants |
| `mainImagePath` | String | S3 path to hangout image |
| `startTimestamp` | Long | Start time (epoch milliseconds) |
| `endTimestamp` | Long | End time (epoch milliseconds) |
| `seriesId` | String | Series ID if part of recurring series |
| `polls` | PollWithOptions[] | Nested poll data with vote counts |
| `cars` | CarWithRiders[] | Nested carpool data with riders |
| `needsRide` | NeedsRide[] | Users who need rides |
| `attributes` | HangoutAttribute[] | Custom attributes |
| `interestLevels` | InterestLevel[] | **Attendance/RSVP data** |

### InterestLevel Object

Each interest level contains:

| Field | Type | Description |
|-------|------|-------------|
| `eventId` | String (UUID) | The hangout ID |
| `userId` | String (UUID) | User who set the interest level |
| `userName` | String | Denormalized user display name |
| `status` | String | "GOING", "INTERESTED", or "NOT_GOING" |
| `notes` | String | Optional user notes about attendance |
| `mainImagePath` | String | Denormalized user profile image path |

### Sample Response

```json
{
  "groupId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "withDay": [
    {
      "hangoutId": "h1h2h3h4-h5h6-h7h8-h9h0-h1h2h3h4h5h6",
      "title": "Cocktails",
      "description": "Let's explore Seattle's best cocktail bars",
      "status": "CONFIRMED",
      "type": "hangout",
      "visibility": "INVITE_ONLY",
      "carpoolEnabled": true,
      "timeInfo": {
        "type": "SPECIFIC",
        "specificTime": "2025-10-25T19:00:00Z",
        "timeDescription": "Saturday at 7 PM"
      },
      "location": {
        "name": "The Alley",
        "address": "123 Main St",
        "city": "Seattle",
        "state": "WA",
        "zipCode": "98101",
        "latitude": 47.6062,
        "longitude": -122.3321
      },
      "participantCount": 4,
      "mainImagePath": "events/user123/cocktails.jpg",
      "startTimestamp": 1729882800000,
      "endTimestamp": 1729893600000,
      "seriesId": null,
      "polls": [
        {
          "pollId": "p1p2p3p4-p5p6-p7p8-p9p0-p1p2p3p4p5p6",
          "question": "Which bar first?",
          "allowMultiple": false,
          "userHasVoted": true,
          "options": [
            {
              "optionId": "o1o2o3o4-o5o6-o7o8-o9o0-o1o2o3o4o5o6",
              "optionText": "The Alley",
              "voteCount": 3,
              "userVoted": true
            },
            {
              "optionId": "o2o2o3o4-o5o6-o7o8-o9o0-o1o2o3o4o5o6",
              "optionText": "Little Tin Apothecary",
              "voteCount": 1,
              "userVoted": false
            }
          ]
        }
      ],
      "cars": [
        {
          "carId": "c1c2c3c4-c5c6-c7c8-c9c0-c1c2c3c4c5c6",
          "driverId": "u1u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
          "driverName": "Alex",
          "availableSeats": 4,
          "departureLocation": "Capitol Hill",
          "riders": [
            {
              "riderId": "u2u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
              "riderName": "Jordan"
            }
          ]
        }
      ],
      "needsRide": [
        {
          "userId": "u3u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
          "userName": "Casey",
          "pickupLocation": "Fremont"
        }
      ],
      "attributes": [
        {
          "attributeId": "a1a2a3a4-a5a6-a7a8-a9a0-a1a2a3a4a5a6",
          "attributeName": "Dress Code",
          "stringValue": "Smart Casual"
        }
      ],
      "interestLevels": [
        {
          "eventId": "h1h2h3h4-h5h6-h7h8-h9h0-h1h2h3h4h5h6",
          "userId": "u1u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
          "userName": "Alex Chen",
          "status": "GOING",
          "notes": "Can't wait!",
          "mainImagePath": "users/u1u2/profile.jpg"
        },
        {
          "eventId": "h1h2h3h4-h5h6-h7h8-h9h0-h1h2h3h4h5h6",
          "userId": "u2u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
          "userName": "Jordan Smith",
          "status": "GOING",
          "notes": null,
          "mainImagePath": "users/u2u2/profile.jpg"
        },
        {
          "eventId": "h1h2h3h4-h5h6-h7h8-h9h0-h1h2h3h4h5h6",
          "userId": "u3u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
          "userName": "Casey Park",
          "status": "INTERESTED",
          "notes": "Might be late",
          "mainImagePath": null
        },
        {
          "eventId": "h1h2h3h4-h5h6-h7h8-h9h0-h1h2h3h4h5h6",
          "userId": "u4u2u3u4-u5u6-u7u8-u9u0-u1u2u3u4u5u6",
          "userName": "Sam Lee",
          "status": "NOT_GOING",
          "notes": "Out of town",
          "mainImagePath": "users/u4u2/profile.jpg"
        }
      ]
    }
  ],
  "needsDay": [
    {
      "hangoutId": "h2h2h3h4-h5h6-h7h8-h9h0-h1h2h3h4h5h6",
      "title": "Hiking Trip",
      "description": "TBD - pick a date!",
      "status": "PLANNING",
      "type": "hangout",
      "visibility": "INVITE_ONLY",
      "carpoolEnabled": false,
      "timeInfo": null,
      "location": null,
      "participantCount": 0,
      "mainImagePath": "predefined/hiking.jpg",
      "startTimestamp": null,
      "endTimestamp": null,
      "seriesId": null,
      "polls": [],
      "cars": [],
      "needsRide": [],
      "attributes": [],
      "interestLevels": []
    }
  ],
  "nextPageToken": "eyJsYXN0U3RhcnRUaW1lc3RhbXAiOjE3Mjk4OTM2MDAwMDB9",
  "previousPageToken": null
}
```

### Response Codes

| Code | Description |
|------|-------------|
| `200 OK` | Successfully retrieved group feed |
| `400 Bad Request` | Invalid groupId format or query parameters |
| `401 Unauthorized` | Missing or invalid JWT token |
| `403 Forbidden` | User is not a member of the group |
| `404 Not Found` | Group does not exist |

### Performance Notes

- **Single Query**: All data loaded from denormalized pointer records (no N+1 queries)
- **Efficient Pagination**: Uses DynamoDB query with limit and start keys
- **Chronological Ordering**: `withDay` items sorted by `startTimestamp`
- **Complete Data**: Includes all related data (polls, carpools, attributes, attendance) without additional lookups

### Usage Examples

#### Basic Request
```bash
curl -X GET "https://api.inviter.app/groups/a1b2c3d4-e5f6-7890-abcd-ef1234567890/feed" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### With Pagination
```bash
curl -X GET "https://api.inviter.app/groups/a1b2c3d4-e5f6-7890-abcd-ef1234567890/feed?limit=10&startingAfter=eyJsYXN0..." \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Client Integration Notes

1. **Display Attendance**: Use `interestLevels` to show who's going, interested, or not going
2. **User Status**: Filter `interestLevels` by current user's ID to show their RSVP status
3. **Participant Counts**: Use `participantCount` for quick counts (derived from interest levels)
4. **Profile Images**: Display user avatars using denormalized `mainImagePath` from interest levels
5. **Empty Lists**: All array fields return `[]` if empty, never `null`

### Related Endpoints

- `POST /hangouts/{hangoutId}/interest` - Set user's interest level
- `DELETE /hangouts/{hangoutId}/interest` - Remove user's interest level
- `GET /hangouts/{hangoutId}` - Get full hangout details (use sparingly, prefer feed for lists)
