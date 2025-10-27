# Inviter API Documentation

## Base URLs

### Production
- **API Gateway**: `https://am6c8sp6kh.execute-api.us-west-2.amazonaws.com/prod/` (HTTPS with SSL)
- **Elastic Beanstalk Direct**: `http://inviter-test.eba-meudu6bv.us-west-2.elasticbeanstalk.com` (HTTP only)

### Staging
- **API Gateway**: `https://v7ihwy6uv9.execute-api.us-west-2.amazonaws.com/prod/` (HTTPS with SSL)
- **Elastic Beanstalk Direct**: `http://inviter-staging.eba-pi6d9nbm.us-west-2.elasticbeanstalk.com` (HTTP only)

### Local Development
- **Backend API**: `http://localhost:8080`

## Authentication

### JWT Token Authentication
- **Method**: Bearer Token in Authorization header
- **Format**: `Authorization: Bearer <jwt_token>`
- **Token Subject**: User ID (UUID string)
- **Expiration**: 24 hours (86400 seconds)
- **Algorithm**: HMAC SHA-256

### Test Credentials
```json
{
  "phoneNumber": "+19285251044",
  "password": "mypass2"
}
```

---

## API Endpoints

### 1. Health Check (Public)

#### GET `/`
Returns server status.

**Response (200 OK):**
```
SUCCESS: Inviter API is running on port 5000
```

#### GET `/health`
Returns health status.

**Response (200 OK):**
```
OK
```

---

### 2. Authentication (Public)

#### POST `/auth/register`
Register a new user account.

**Request Body:**
```json
{
  "phoneNumber": "+1234567890",
  "username": "johndoe",
  "displayName": "John Doe",
  "password": "securepassword"
}
```

**Response (201 Created):**
```json
{
  "message": "User registered successfully"
}
```

**Response (409 Conflict):**
```json
{
  "error": "User already exists"
}
```

#### POST `/auth/login`
Login with phone number and password.

**Request Body:**
```json
{
  "phoneNumber": "+1234567890",
  "password": "securepassword"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "Invalid credentials"
}
```

---

### 3. Events (Requires JWT)

#### POST `/events/new`
Create a new event with invites.

**Request Body:**
```json
{
  "name": "Birthday Party",
  "description": "Come celebrate with us!",
  "startTime": "2024-12-25T18:00:00",
  "endTime": "2024-12-25T23:00:00",
  "location": {
    "streetAddress": "123 Main St",
    "city": "San Francisco",
    "state": "CA",
    "postalCode": "94102",
    "country": "USA"
  },
  "visibility": "INVITE_ONLY",
  "mainImagePath": "predefined/birthday.jpg",
  "invites": [
    {
      "phoneNumber": "+1234567890"
    }
  ]
}
```

**Field Details:**
- `visibility`: Enum - `"INVITE_ONLY"`, `"PUBLIC"`, `"ACCEPTED_ONLY"`
- `startTime`/`endTime`: ISO 8601 format (`YYYY-MM-DDTHH:MM:SS`)
- `location`: All fields are strings, all optional
- `mainImagePath`: String path to image (predefined or S3 key)
- `invites`: Array of objects with `phoneNumber` field

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### GET `/events`
Get all events for the authenticated user.

**Response (200 OK):**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Birthday Party",
    "description": "Come celebrate with us!",
    "startTime": "2024-12-25T18:00:00",
    "endTime": "2024-12-25T23:00:00",
    "location": {
      "streetAddress": "123 Main St",
      "city": "San Francisco",
      "state": "CA",
      "postalCode": "94102",
      "country": "USA"
    },
    "visibility": "INVITE_ONLY",
    "mainImagePath": "predefined/birthday.jpg",
    "version": 1,
    "associatedGroups": [],
    "carpoolEnabled": false
  }
]
```

#### GET `/events/{id}`
Get a specific event by ID.

**Path Parameters:**
- `id`: UUID of the event

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Birthday Party",
  "description": "Come celebrate with us!",
  "startTime": "2024-12-25T18:00:00",
  "endTime": "2024-12-25T23:00:00",
  "location": {
    "streetAddress": "123 Main St",
    "city": "San Francisco",
    "state": "CA",
    "postalCode": "94102",
    "country": "USA"
  },
  "visibility": "INVITE_ONLY",
  "mainImagePath": "predefined/birthday.jpg",
  "version": 1,
  "associatedGroups": [],
  "carpoolEnabled": false
}
```

**Response (404 Not Found):** Event not found or user not authorized.

#### PUT `/events/{id}`
Update an event (host only).

**Path Parameters:**
- `id`: UUID of the event

**Request Body (all fields optional):**
```json
{
  "name": "Updated Party Name",
  "description": "Updated description",
  "startTime": "2024-12-25T19:00:00",
  "endTime": "2024-12-26T01:00:00",
  "location": {
    "streetAddress": "456 Oak Ave",
    "city": "San Francisco",
    "state": "CA",
    "postalCode": "94103",
    "country": "USA"
  },
  "visibility": "PUBLIC",
  "mainImagePath": "events/user123/custom-image.jpg"
}
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Updated Party Name",
  "description": "Updated description",
  "startTime": "2024-12-25T19:00:00",
  "endTime": "2024-12-26T01:00:00",
  "location": {
    "streetAddress": "456 Oak Ave",
    "city": "San Francisco",
    "state": "CA",
    "postalCode": "94103",
    "country": "USA"
  },
  "visibility": "PUBLIC",
  "mainImagePath": "events/user123/custom-image.jpg",
  "version": 2,
  "associatedGroups": [],
  "carpoolEnabled": false
}
```

**Response (403 Forbidden):** User is not a host of the event.
**Response (404 Not Found):** Event not found.

#### DELETE `/events/{id}`
Delete an event (host only).

**Path Parameters:**
- `id`: UUID of the event

**Response (200 OK):**
```json
{
  "message": "Event deleted successfully"
}
```

**Response (403 Forbidden):** User is not a host of the event.
**Response (404 Not Found):** Event not found.

---

### 4. Invites (Requires JWT)

#### GET `/events/{eventId}/invites`
Get all invites for an event.

**Path Parameters:**
- `eventId`: UUID of the event

**Authorization:** User must be invited to the event OR be a host.

**Response (200 OK):**
```json
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440000",
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "770e8400-e29b-41d4-a716-446655440000",
    "userPhoneNumber": "+1234567890",
    "username": "johndoe",
    "displayName": "John Doe",
    "type": "GUEST",
    "response": "GOING",
    "eventPassed": false
  }
]
```

**Field Details:**
- `type`: Enum - `"HOST"`, `"GUEST"`
- `response`: Enum - `"GOING"`, `"NOT_GOING"`, `"MAYBE"`, `"NOT_RESPONDED"`

#### POST `/events/{eventId}/invites`
Add an invite to an event (host only).

**Path Parameters:**
- `eventId`: UUID of the event

**Request Body:**
```json
{
  "phoneNumber": "+1234567890"
}
```

**Response (201 Created):**
```json
{
  "inviteId": "660e8400-e29b-41d4-a716-446655440000",
  "message": "Invite added successfully"
}
```

**Response (409 Conflict):**
```json
{
  "error": "User is already invited to this event"
}
```

#### PUT `/events/{eventId}/invites/{inviteId}`
Update invite response.

**Path Parameters:**
- `eventId`: UUID of the event
- `inviteId`: UUID of the invite

**Authorization:** Users can only update their own invite responses.

**Request Body:**
```json
{
  "response": "GOING"
}
```

**Response (200 OK):**
```json
{
  "inviteId": "660e8400-e29b-41d4-a716-446655440000",
  "response": "GOING",
  "message": "Invite response updated successfully"
}
```

#### DELETE `/events/{eventId}/invites/{inviteId}`
Remove an invite from an event (host only).

**Path Parameters:**
- `eventId`: UUID of the event
- `inviteId`: UUID of the invite

**Response (200 OK):**
```json
{
  "message": "Invite removed successfully"
}
```

---

### 5. Profile (Requires JWT)

#### GET `/profile`
Get current user's profile.

**Response (200 OK):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440000",
  "phoneNumber": "+1234567890",
  "username": "johndoe",
  "displayName": "John Doe",
  "password": null
}
```

**Note:** Password is always null in responses for security.

#### PUT `/profile`
Update user's display name.

**Request Body:**
```json
{
  "displayName": "John Smith"
}
```

**Response (200 OK):**
```json
{
  "message": "Profile updated successfully",
  "displayName": "John Smith"
}
```

#### PUT `/profile/password`
Change user's password.

**Request Body:**
```json
{
  "currentPassword": "oldpassword",
  "newPassword": "newsecurepassword"
}
```

**Response (200 OK):**
```json
{
  "message": "Password changed successfully"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Current password is incorrect"
}
```

#### DELETE `/profile`
Delete user account permanently.

**Response (200 OK):**
```json
{
  "message": "Account deleted successfully"
}
```

**Note:** This will also delete all associated invites, devices, and events where the user is the sole host.

---

### 6. Images

#### GET `/images/predefined` (Public)
Get all predefined images available for events.

**Response (200 OK):**
```json
[
  {
    "key": "predefined/birthday.jpg",
    "path": "https://inviter-event-images-871070087012.s3.us-west-2.amazonaws.com/predefined/birthday.jpg",
    "displayName": "Birthday"
  },
  {
    "key": "predefined/party.jpg",
    "path": "https://inviter-event-images-871070087012.s3.us-west-2.amazonaws.com/predefined/party.jpg",
    "displayName": "Party"
  }
]
```

#### POST `/images/upload-url` (Requires JWT)
Generate presigned S3 upload URL for custom images.

**Request Body:**
```json
{
  "key": "events/770e8400-e29b-41d4-a716-446655440000/1640995200000_abc123_photo.jpg",
  "contentType": "image/jpeg"
}
```

**Key Format:** `events/{userId}/{timestamp}_{randomId}_{filename}`

**Response (200 OK):**
```json
{
  "uploadUrl": "https://inviter-event-images-871070087012.s3.us-west-2.amazonaws.com/events/770e8400-e29b-41d4-a716-446655440000/1640995200000_abc123_photo.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...",
  "key": "events/770e8400-e29b-41d4-a716-446655440000/1640995200000_abc123_photo.jpg"
}
```

**Usage:**
1. Call this endpoint to get a presigned URL
2. Use the `uploadUrl` to directly upload the image file to S3 via PUT request
3. Use the returned `key` as the `mainImagePath` when creating/updating events

---

### 7. Groups (Requires JWT)

#### POST `/groups`
Create a new group.

**Request Body:**
```json
{
  "groupName": "LGBTQ+ Friends",
  "isPublic": false
}
```

**Field Details:**
- `groupName`: String (1-100 characters, required)
- `isPublic`: Boolean (required) - Whether group is public or private

**Response (201 Created):**
```json
{
  "groupId": "990e8400-e29b-41d4-a716-446655440000",
  "groupName": "LGBTQ+ Friends",
  "isPublic": false,
  "userRole": "ADMIN",
  "joinedAt": "2024-01-15T10:30:00Z",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### GET `/groups`
Get all groups for the authenticated user.

**Response (200 OK):**
```json
[
  {
    "groupId": "990e8400-e29b-41d4-a716-446655440000",
    "groupName": "LGBTQ+ Friends",
    "isPublic": false,
    "userRole": "ADMIN",
    "joinedAt": "2024-01-15T10:30:00Z",
    "createdAt": "2024-01-15T10:30:00Z"
  }
]
```

**Field Details:**
- `userRole`: String - `"ADMIN"` or `"MEMBER"`
- `joinedAt`: ISO 8601 timestamp when user joined group
- `createdAt`: ISO 8601 timestamp when group was created

#### GET `/groups/{groupId}`
Get specific group details.

**Path Parameters:**
- `groupId`: UUID of the group

**Response (200 OK):**
```json
{
  "groupId": "990e8400-e29b-41d4-a716-446655440000",
  "groupName": "LGBTQ+ Friends",
  "isPublic": false,
  "userRole": "MEMBER",
  "joinedAt": "2024-01-15T10:30:00Z",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### DELETE `/groups/{groupId}`
Delete a group (admin only).

**Path Parameters:**
- `groupId`: UUID of the group

**Response (204 No Content):** Empty response.

#### POST `/groups/{groupId}/members`
Add a member to a group.

**Path Parameters:**
- `groupId`: UUID of the group

**Request Body:**
```json
{
  "userId": "770e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200 OK):** Empty response with status 200.

#### GET `/groups/{groupId}/members`
Get all members of a group.

**Path Parameters:**
- `groupId`: UUID of the group

**Response (200 OK):**
```json
[
  {
    "userId": "770e8400-e29b-41d4-a716-446655440000",
    "userName": "john_doe",
    "role": "ADMIN",
    "joinedAt": "2024-01-15T10:30:00Z"
  },
  {
    "userId": "880e8400-e29b-41d4-a716-446655440000",
    "userName": "jane_smith",
    "role": "MEMBER",
    "joinedAt": "2024-01-16T14:20:00Z"
  }
]
```

#### DELETE `/groups/{groupId}/members/{userId}`
Remove a member from a group.

**Path Parameters:**
- `groupId`: UUID of the group
- `userId`: UUID of the user to remove

**Authorization:** Admins can remove any member, members can remove themselves.

**Response (204 No Content):** Empty response.

#### GET `/groups/{groupId}/feed`
Get group feed showing hangouts organized by status.

**Path Parameters:**
- `groupId`: UUID of the group

**Response (200 OK):**
```json
{
  "groupId": "990e8400-e29b-41d4-a716-446655440000",
  "withDay": [
    {
      "hangoutId": "880e8400-e29b-41d4-a716-446655440000",
      "title": "Movie Night",
      "status": "SCHEDULED",
      "hangoutTime": "2024-12-25T19:00:00Z",
      "locationName": "Cinema Downtown",
      "participantCount": 8
    }
  ],
  "needsDay": [
    {
      "hangoutId": "881e8400-e29b-41d4-a716-446655440000",
      "title": "Game Night",
      "status": "PLANNING",
      "hangoutTime": null,
      "locationName": null,
      "participantCount": 3
    }
  ]
}
```

**Field Details:**
- `withDay`: Array of hangouts with scheduled times
- `needsDay`: Array of hangouts that need scheduling
- `hangoutTime`: ISO 8601 timestamp or null
- `status`: String representing hangout status
- `participantCount`: Integer count of participants

#### POST `/groups/{groupId}/invite-code`
Generate or retrieve shareable invite code for a group (idempotent).

**Path Parameters:**
- `groupId`: UUID of the group

**Authorization:** User must be a member of the group.

**Response (200 OK):**
```json
{
  "inviteCode": "abc123xy",
  "shareUrl": "https://d1713f2ygzp5es.cloudfront.net/join-group/abc123xy"
}
```

**Field Details:**
- `inviteCode`: String - 8-character alphanumeric code (lowercase)
- `shareUrl`: String - Complete shareable URL for Universal Links/deep linking
- Calling this endpoint multiple times returns the same code (idempotent)

#### GET `/groups/invite/{inviteCode}` (Public)
Preview group information before joining (no authentication required).

**Path Parameters:**
- `inviteCode`: String - The invite code to preview

**Response (200 OK) - Public Group:**
```json
{
  "isPrivate": false,
  "groupName": "Hiking Buddies",
  "mainImagePath": "groups/group123/image.jpg"
}
```

**Response (200 OK) - Private Group:**
```json
{
  "isPrivate": true
}
```

**Response (404 Not Found):**
```json
{
  "error": "Invalid invite code: abc123xy"
}
```

**Field Details:**
- `isPrivate`: Boolean - If true, group details are hidden for privacy
- `groupName`: String - Group name (only present for public groups)
- `mainImagePath`: String - Main image path (only present for public groups)

**Privacy:**
- Public endpoint - accessible without authentication
- For private groups, only the `isPrivate` flag is returned to prevent leaking group information
- Null fields are omitted from the response (using @JsonInclude annotation)
- Frontend should show generic message like "You've been invited to a private group"
- Full group details revealed only after authenticated join

**Security:**
- Does not include groupId - only minimal info needed for preview
- User receives groupId after successfully joining via POST /groups/invite/join

#### POST `/groups/invite/join`
Join a group using an invite code.

**Request Body:**
```json
{
  "inviteCode": "abc123xy"
}
```

**Response (200 OK):**
```json
{
  "groupId": "990e8400-e29b-41d4-a716-446655440000",
  "groupName": "Hiking Buddies",
  "isPublic": false,
  "userRole": "MEMBER",
  "joinedAt": "2024-01-15T10:30:00Z",
  "createdAt": "2024-01-15T10:30:00Z",
  "mainImagePath": "groups/group123/image.jpg",
  "backgroundImagePath": "groups/group123/bg.jpg"
}
```

**Response (404 Not Found):**
```json
{
  "error": "Invalid invite code: abc123xy"
}
```

**Field Details:**
- If user is already a member, returns existing membership info (idempotent)
- New members are added with `MEMBER` role
- Returns full GroupDTO with user's membership details

---

### 8. Devices (Requires JWT)

#### POST `/devices`
Register device for push notifications.

**Request Body:**
```json
{
  "token": "fcm_token_or_apns_token_here",
  "platform": "ios"
}
```

**Field Details:**
- `token`: String - FCM token (Android) or APNS token (iOS)
- `platform`: String - `"ios"` or `"android"`

**Response (200 OK):**
```json
{
  "message": "Device registered successfully"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Invalid platform. Must be 'ios' or 'android'"
}
```

---

### 9. Polls (Requires JWT)

#### POST `/events/{eventId}/polls`
Create a poll for an event.

**Path Parameters:**
- `eventId`: UUID of the event

**Request Body:**
```json
{
  "title": "Where should we meet?",
  "options": ["Coffee Shop", "Park", "Library"]
}
```

**Response (201 Created):**
```json
{
  "pollId": "aa0e8400-e29b-41d4-a716-446655440000",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Where should we meet?",
  "options": ["Coffee Shop", "Park", "Library"],
  "createdBy": "770e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

#### GET `/events/{eventId}/polls`
Get all polls for an event.

**Path Parameters:**
- `eventId`: UUID of the event

**Response (200 OK):**
```json
[
  {
    "pollId": "aa0e8400-e29b-41d4-a716-446655440000",
    "title": "Where should we meet?",
    "options": [
      {
        "option": "Coffee Shop",
        "votes": 3
      },
      {
        "option": "Park", 
        "votes": 1
      },
      {
        "option": "Library",
        "votes": 0
      }
    ],
    "totalVotes": 4,
    "userVote": "Coffee Shop"
  }
]
```

#### POST `/events/{eventId}/polls/{pollId}/vote`
Vote on a poll option.

**Path Parameters:**
- `eventId`: UUID of the event
- `pollId`: UUID of the poll

**Request Body:**
```json
{
  "option": "Coffee Shop"
}
```

**Response (200 OK):**
```json
{
  "message": "Vote recorded successfully"
}
```

---

### 10. Carpool (Requires JWT)

#### POST `/events/{eventId}/carpool/cars`
Offer a car for an event.

**Path Parameters:**
- `eventId`: UUID of the event

**Request Body:**
```json
{
  "totalCapacity": 4,
  "departureLocation": "Downtown Station",
  "departureTime": "2024-12-25T17:30:00",
  "returnTime": "2024-12-25T23:30:00",
  "notes": "No smoking, pets welcome"
}
```

**Response (201 Created):**
```json
{
  "carId": "bb0e8400-e29b-41d4-a716-446655440000",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "driverId": "770e8400-e29b-41d4-a716-446655440000",
  "totalCapacity": 4,
  "availableSpots": 3,
  "departureLocation": "Downtown Station",
  "departureTime": "2024-12-25T17:30:00",
  "returnTime": "2024-12-25T23:30:00",
  "notes": "No smoking, pets welcome"
}
```

#### GET `/events/{eventId}/carpool/cars`
Get all cars offered for an event.

**Path Parameters:**
- `eventId`: UUID of the event

**Response (200 OK):**
```json
[
  {
    "carId": "bb0e8400-e29b-41d4-a716-446655440000",
    "driverName": "John Doe",
    "totalCapacity": 4,
    "availableSpots": 2,
    "departureLocation": "Downtown Station",
    "departureTime": "2024-12-25T17:30:00",
    "returnTime": "2024-12-25T23:30:00",
    "riders": [
      {
        "userId": "880e8400-e29b-41d4-a716-446655440000",
        "userName": "Jane Smith",
        "joinedAt": "2024-01-15T12:00:00Z"
      }
    ]
  }
]
```

#### POST `/events/{eventId}/carpool/cars/{carId}/join`
Join a car as a rider.

**Path Parameters:**
- `eventId`: UUID of the event
- `carId`: UUID of the car

**Response (200 OK):**
```json
{
  "message": "Successfully joined the car"
}
```

#### DELETE `/events/{eventId}/carpool/cars/{carId}/leave`
Leave a car (remove yourself as rider).

**Path Parameters:**
- `eventId`: UUID of the event
- `carId`: UUID of the car

**Response (200 OK):**
```json
{
  "message": "Successfully left the car"
}
```

---

### 11. New Hangouts API (Requires JWT)

#### POST `/hangouts`
Create a new hangout.

**Request Body:**
```json
{
  "name": "Movie Night",
  "description": "Let's watch the new Marvel movie!",
  "startTime": "2024-12-25T19:00:00",
  "endTime": "2024-12-25T22:00:00",
  "location": {
    "streetAddress": "123 Cinema Blvd",
    "city": "Los Angeles",
    "state": "CA",
    "postalCode": "90210",
    "country": "USA"
  },
  "visibility": "INVITE_ONLY",
  "mainImagePath": "predefined/movie.jpg",
  "carpoolEnabled": true
}
```

**Response (201 Created):**
```json
{
  "hangoutId": "880e8400-e29b-41d4-a716-446655440000",
  "name": "Movie Night",
  "description": "Let's watch the new Marvel movie!",
  "startTime": "2024-12-25T19:00:00",
  "endTime": "2024-12-25T22:00:00",
  "location": {
    "streetAddress": "123 Cinema Blvd",
    "city": "Los Angeles",
    "state": "CA",
    "postalCode": "90210",
    "country": "USA"
  },
  "visibility": "INVITE_ONLY",
  "mainImagePath": "predefined/movie.jpg",
  "version": 1,
  "associatedGroups": [],
  "carpoolEnabled": true
}
```

#### GET `/hangouts/{hangoutId}`
Get detailed hangout information including polls, cars, and attendance.

**Path Parameters:**
- `hangoutId`: UUID of the hangout

**Response (200 OK):**
```json
{
  "hangout": {
    "hangoutId": "880e8400-e29b-41d4-a716-446655440000",
    "name": "Movie Night",
    "description": "Let's watch the new Marvel movie!",
    "startTime": "2024-12-25T19:00:00",
    "endTime": "2024-12-25T22:00:00",
    "location": {
      "streetAddress": "123 Cinema Blvd",
      "city": "Los Angeles",
      "state": "CA",
      "postalCode": "90210",
      "country": "USA"
    },
    "visibility": "INVITE_ONLY",
    "mainImagePath": "predefined/movie.jpg",
    "version": 1,
    "associatedGroups": [],
    "carpoolEnabled": true
  },
  "polls": [],
  "cars": [],
  "attendance": []
}
```

#### PATCH `/hangouts/{hangoutId}`
Update hangout details (host only).

**Path Parameters:**
- `hangoutId`: UUID of the hangout

**Request Body (all fields optional):**
```json
{
  "name": "Updated Movie Night",
  "description": "Updated description",
  "carpoolEnabled": false
}
```

**Response (200 OK):** Empty response with status 200.

#### DELETE `/hangouts/{hangoutId}`
Delete hangout (host only).

**Path Parameters:**
- `hangoutId`: UUID of the hangout

**Response (204 No Content):** Empty response with status 204.

---

### 12. Legacy Event Detail API (Requires JWT)

#### GET `/events/{eventId}/detail`
Get comprehensive event details including polls, cars, and attendance.

**Path Parameters:**
- `eventId`: UUID of the event

**Response (200 OK):**
```json
{
  "event": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Birthday Party",
    "description": "Come celebrate with us!",
    "startTime": "2024-12-25T18:00:00",
    "endTime": "2024-12-25T23:00:00",
    "location": {
      "streetAddress": "123 Main St",
      "city": "San Francisco",
      "state": "CA",
      "postalCode": "94102",
      "country": "USA"
    },
    "visibility": "INVITE_ONLY",
    "mainImagePath": "predefined/birthday.jpg",
    "version": 1,
    "associatedGroups": ["990e8400-e29b-41d4-a716-446655440000"],
    "carpoolEnabled": true
  },
  "polls": [
    {
      "pollId": "aa0e8400-e29b-41d4-a716-446655440000",
      "title": "What cake flavor?",
      "options": [
        {"option": "Chocolate", "votes": 5},
        {"option": "Vanilla", "votes": 2}
      ],
      "totalVotes": 7,
      "userVote": "Chocolate"
    }
  ],
  "cars": [
    {
      "carId": "bb0e8400-e29b-41d4-a716-446655440000",
      "driverName": "John Doe",
      "totalCapacity": 4,
      "availableSpots": 2,
      "departureLocation": "Downtown Station",
      "departureTime": "2024-12-25T17:30:00",
      "riders": [
        {
          "userId": "880e8400-e29b-41d4-a716-446655440000",
          "userName": "Jane Smith",
          "joinedAt": "2024-01-15T12:00:00Z"
        }
      ]
    }
  ],
  "attendance": [
    {
      "userId": "770e8400-e29b-41d4-a716-446655440000",
      "userName": "john_doe",
      "response": "GOING",
      "type": "HOST"
    }
  ]
}
```

#### PATCH `/{eventId}`
Update legacy event properties.

**Path Parameters:**
- `eventId`: UUID of the event

**Request Body (all fields optional):**
```json
{
  "name": "Updated Party Name",
  "description": "Updated description",
  "associatedGroups": ["990e8400-e29b-41d4-a716-446655440000"]
}
```

**Response (200 OK):** Empty response with status 200.

#### POST `/{eventId}/groups`
Associate event with groups.

**Path Parameters:**
- `eventId`: UUID of the event

**Request Body:**
```json
{
  "groupIds": ["990e8400-e29b-41d4-a716-446655440000", "991e8400-e29b-41d4-a716-446655440000"]
}
```

**Response (200 OK):** Empty response with status 200.

#### DELETE `/{eventId}/groups`
Disassociate event from groups.

**Path Parameters:**
- `eventId`: UUID of the event

**Request Body:**
```json
{
  "groupIds": ["990e8400-e29b-41d4-a716-446655440000"]
}
```

**Response (200 OK):** Empty response with status 200.

---

## Error Responses

### Standard HTTP Status Codes
- **200 OK**: Request successful
- **201 Created**: Resource created successfully
- **204 No Content**: Request successful, no content returned
- **400 Bad Request**: Invalid request format or data
- **401 Unauthorized**: Missing or invalid JWT token
- **403 Forbidden**: User lacks permission for this resource
- **404 Not Found**: Resource not found or user not authorized
- **409 Conflict**: Resource already exists or conflict with current state
- **500 Internal Server Error**: Server error

### Error Response Format
All error responses follow this format:
```json
{
  "error": "Error message describing what went wrong"
}
```

---

## Data Types Reference

### Enums

#### EventVisibility
- `"INVITE_ONLY"`: Only invited users can see the event
- `"PUBLIC"`: All users can see the event
- `"ACCEPTED_ONLY"`: Only users who accepted invites can see details

#### InviteType
- `"HOST"`: User is a host of the event (can modify event/invites)
- `"GUEST"`: User is invited as a guest

#### InviteResponse
- `"GOING"`: User will attend
- `"NOT_GOING"`: User will not attend
- `"MAYBE"`: User is unsure about attending
- `"NOT_RESPONDED"`: User has not responded yet

### Date/Time Format
**LocalDateTime format** (used in events): `YYYY-MM-DDTHH:MM:SS`
Example: `"2024-12-25T18:00:00"`

**Instant format** (used in groups/polls/cars): `YYYY-MM-DDTHH:MM:SSZ`
Example: `"2024-01-15T10:30:00Z"`

### UUID Format
All IDs are UUID strings in the format: `550e8400-e29b-41d4-a716-446655440000`

### Address Object
```json
{
  "streetAddress": "string (optional)",
  "city": "string (optional)",
  "state": "string (optional)",
  "postalCode": "string (optional)",
  "country": "string (optional)"
}
```

---

## Integration Notes

### CORS Configuration
The API includes CORS headers for these origins:
- `http://localhost:3000` (AngularJS development)
- `http://localhost:4200` (Angular 19 development)
- `http://localhost:8080` (Swagger UI)
- `https://d3lm7si4v7xvcj.cloudfront.net` (Production web app)
- `https://api.inviter.app` (Custom domain)

### Image Handling
1. **Predefined Images**: Use the `/images/predefined` endpoint to get available options
2. **Custom Uploads**: 
   - Get presigned URL from `/images/upload-url`
   - Upload directly to S3 using the presigned URL
   - Use the returned key in event creation/update

### Phone Number Format
Phone numbers should include country code (e.g., `+1234567890`)

### JWT Token Storage
- **Web Apps**: Store in localStorage with automatic inclusion via HTTP interceptors
- **iOS App**: Store in Keychain with automatic header injection
- **Expiration**: Tokens expire after 24 hours, implement automatic logout

### Rate Limiting
No explicit rate limiting is currently implemented, but API Gateway provides built-in throttling.

---

## API Testing

### Swagger UI
Available at: `http://localhost:8080/swagger-ui.html` (local development)

### Test Sequence
1. Register or login to get JWT token
2. Use token in Authorization header for protected endpoints
3. Create events, manage invites, update profile as needed

### Test Data
Use the provided test credentials:
- Phone: `+19285251044`
- Password: `mypass2`
- Username: `jeana`
- Display Name: `Jeana`