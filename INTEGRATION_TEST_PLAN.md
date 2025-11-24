# Integration Test Plan - Inviter Backend API

## Overview
This document outlines integration test cases for all API endpoints, including special considerations for external dependencies (DynamoDB, S3, Twilio, SNS, external APIs).

**Test Priority Levels:**
- **HIGH**: Core functionality, critical user paths, authentication, authorization
- **MEDIUM**: Important features but less frequently used
- **LOW**: Edge cases, nice-to-have validations, rarely-used features

**Excluded from plan:**
- Legacy `/events` APIs (deprecated, use `/hangouts` instead)
- Hiking APIs (not yet finalized)
- Legacy invite APIs under `/events/{eventId}/invites` (use group-based flows)

## Test Framework Recommendations

### Core Framework
- **TestContainers + LocalStack** for AWS services (DynamoDB, S3, SNS)
- **WireMock** for external HTTP APIs (Twilio, event parsing)
- **Spring Boot Test** with `@SpringBootTest` for full application context
- **RestAssured** or **MockMvc** for HTTP testing

### Key Principles
1. **Avoid test brittleness**: Test behavior, not implementation details
2. **Isolation**: Each test should be independent and repeatable
3. **Realistic data**: Use production-like data structures
4. **Clear assertions**: Test one concept per test case
5. **Performance**: Integration tests should complete in <30s per suite

---

## 1. Authentication APIs (`/auth`) - **HIGH PRIORITY**

**External Dependencies**: DynamoDB (Users, RefreshTokens), Twilio (SMS verification), Rate limiting cache

### POST /auth/register - HIGH
**Test Cases:**
- ✓ New user registration with valid phone/password creates UNVERIFIED user
- ✓ Registration sends SMS verification code via Twilio
- ✓ Duplicate registration for ACTIVE user returns 409 CONFLICT
- ✓ Registration for UNVERIFIED user updates account and resends code
- ✓ Registration for phone-only user (created via group invite) converts to full account
- ✓ Registration without SMS failure still creates user (degraded mode)
- ✓ Invalid phone format rejected
- ✓ Weak password rejected

**Special Considerations:**
- Mock Twilio API to avoid SMS charges and test SMS failures
- Test backward compatibility with null accountStatus (treated as ACTIVE)
- Verify creationDate set on successful registration

### POST /auth/login
**Test Cases:**
- ✓ Successful login with valid credentials returns access + refresh tokens
- ✓ Login for UNVERIFIED user returns 403 FORBIDDEN
- ✓ Invalid credentials return 401 UNAUTHORIZED
- ✓ Mobile client (X-Client-Type: mobile) receives refresh token in JSON
- ✓ Web client receives refresh token in HttpOnly cookie
- ✓ RefreshToken record created in DynamoDB with correct hashes
- ✓ Device ID and IP address captured in refresh token record
- ✓ Null accountStatus treated as ACTIVE (backward compatibility)

**Special Considerations:**
- Test both mobile and web client flows
- Verify cookie security attributes (HttpOnly, Secure, SameSite)
- Test with existing and null accountStatus values

### POST /auth/refresh
**Test Cases:**
- ✓ Valid refresh token rotates to new access + refresh tokens
- ✓ Old refresh token invalidated after rotation
- ✓ Invalid/expired refresh token returns 401 UNAUTHORIZED
- ✓ Mobile vs web client token delivery (JSON vs cookie)
- ✓ Token rotation updates device metadata (IP, user agent)
- ✓ Suspicious activity (IP change) handled correctly

**Special Considerations:**
- Test token rotation race conditions (parallel requests with same token)
- Verify old token becomes invalid immediately after rotation

### POST /auth/logout
**Test Cases:**
- ✓ Logout revokes specific refresh token
- ✓ Web client cookie cleared
- ✓ Mobile client requires refresh token in body
- ✓ Logout without token fails gracefully
- ✓ Access token still valid until expiration (stateless JWT)

### POST /auth/logout-all
**Test Cases:**
- ✓ Revokes all refresh tokens for user across all devices
- ✓ Requires valid access token
- ✓ Other users' tokens unaffected

### POST /auth/resend-code
**Test Cases:**
- ✓ Resends verification code for UNVERIFIED account
- ✓ Rate limiting prevents abuse (429 TOO_MANY_REQUESTS)
- ✓ Returns 404 if account doesn't exist
- ✓ Returns 409 if account already verified
- ✓ SMS failure logged but doesn't fail request

**Special Considerations:**
- Mock Twilio to test SMS success/failure scenarios
- Test rate limiting cache (Redis/in-memory)
- Verify no account enumeration (timing attacks)

### POST /auth/verify
**Test Cases:**
- ✓ Valid code activates account (UNVERIFIED → ACTIVE)
- ✓ Invalid code returns 400 with INVALID_CODE error
- ✓ Expired code returns 400 with VERIFICATION_CODE_EXPIRED
- ✓ Rate limiting prevents brute force attacks
- ✓ Verification code consumed after use

**Special Considerations:**
- Test code expiration logic
- Verify rate limiting per phone number

### POST /auth/request-password-reset
**Test Cases:**
- ✓ Valid phone number sends SMS reset code
- ✓ Non-existent account still returns 200 (prevent enumeration)
- ✓ Rate limiting applied per phone number
- ✓ PasswordResetRequest created with expiration

**Special Considerations:**
- Mock Twilio SMS
- Verify timing-safe responses (no enumeration)

### POST /auth/verify-reset-code
**Test Cases:**
- ✓ Valid code returns reset token with 15-minute expiration
- ✓ Invalid code returns 400 INVALID_CODE
- ✓ Expired request returns 400 INVALID_RESET_REQUEST
- ✓ Rate limiting prevents brute force

### POST /auth/reset-password
**Test Cases:**
- ✓ Valid reset token updates password
- ✓ Auto-login: Returns access + refresh tokens
- ✓ All existing sessions revoked (logout-all behavior)
- ✓ Invalid/expired token returns 400 INVALID_RESET_TOKEN
- ✓ Mobile vs web token delivery
- ✓ Reset token consumed after use

**Special Considerations:**
- Verify all refresh tokens revoked
- Test auto-login token generation

---

## 2. Profile APIs (`/profile`) - **HIGH PRIORITY**

**External Dependencies**: DynamoDB (Users)

### GET /profile
**Test Cases:**
- ✓ Returns user profile without password field
- ✓ Requires valid JWT token
- ✓ Returns 404 if user deleted
- ✓ Returns all user attributes (displayName, username, phoneNumber, mainImagePath, etc.)

### PUT /profile
**Test Cases:**
- ✓ Updates displayName successfully
- ✓ Updates mainImagePath (S3 key)
- ✓ Partial updates work (only provided fields changed)
- ✓ Returns updated profile data
- ✓ Optimistic locking prevents concurrent update conflicts

**Special Considerations:**
- Test with existing S3 images and verify path validation
- Test concurrent updates with optimistic locking

### PUT /profile/password
**Test Cases:**
- ✓ Changes password with valid current password
- ✓ Rejects incorrect current password
- ✓ Validates new password strength
- ✓ Old password no longer works after change
- ✓ Existing sessions remain valid (no logout)

### DELETE /profile
**Test Cases:**
- ✓ Deletes user account
- ✓ Cascades to delete: devices, refresh tokens, invites, group memberships
- ✓ Events where user is sole host are deleted
- ✓ Events with multiple hosts keep other hosts
- ✓ User removed from group memberships
- ✓ Deleted user cannot login

**Special Considerations:**
- Verify cascade deletes in DynamoDB
- Test with user as sole host vs co-host

---

## 3. Group APIs (`/groups`) - **HIGH PRIORITY**

**External Dependencies**: DynamoDB (Groups, GroupMemberships, Hangouts), Rate limiting cache

### POST /groups
**Test Cases:**
- ✓ Creates group with creator as owner
- ✓ Returns group DTO with all fields
- ✓ GroupMembership created for creator
- ✓ Validates groupName required
- ✓ Optional fields: description, mainImagePath

### GET /groups
**Test Cases:**
- ✓ Returns all groups user is member of
- ✓ Uses efficient GSI query (no N+1)
- ✓ Returns empty list if no memberships
- ✓ Includes group metadata and role

### GET /groups/{groupId}
**Test Cases:**
- ✓ Returns group details for member
- ✓ Returns 403 if user not a member
- ✓ Returns 404 if group doesn't exist
- ✓ Validates UUID format

### PATCH /groups/{groupId}
**Test Cases:**
- ✓ Updates groupName by owner/admin
- ✓ Updates description, mainImagePath
- ✓ Partial updates work
- ✓ Non-owner/admin returns 403
- ✓ Optimistic locking prevents conflicts
- ✓ Empty update returns 400

### DELETE /groups/{groupId}
**Test Cases:**
- ✓ Owner can delete group
- ✓ Cascades to delete: memberships, invite codes, hangouts, idea lists
- ✓ Non-owner returns 403
- ✓ Returns 204 NO_CONTENT on success

**Special Considerations:**
- Verify all related entities deleted (memberships, hangouts, etc.)
- Test with large group (many members/hangouts)

### POST /groups/{groupId}/members
**Test Cases:**
- ✓ Owner/admin adds member by userId
- ✓ Owner/admin adds member by phoneNumber (creates shadow user)
- ✓ Duplicate member returns 409 or idempotent success
- ✓ Non-owner/admin returns 403

### DELETE /groups/{groupId}/members/{userId}
**Test Cases:**
- ✓ Owner/admin removes member
- ✓ Member removed from all group entities
- ✓ Cannot remove last owner
- ✓ Non-owner/admin returns 403

### POST /groups/{groupId}/leave
**Test Cases:**
- ✓ Member can leave group
- ✓ Last owner cannot leave (must delete group or transfer ownership)
- ✓ User removed from all group entities
- ✓ Returns 204 NO_CONTENT

### GET /groups/{groupId}/members
**Test Cases:**
- ✓ Returns all members with roles
- ✓ Member must be in group
- ✓ Includes user details (displayName, etc.)

### GET /groups/{groupId}/feed
**Test Cases:**
- ✓ Returns chronological and no-day hangouts
- ✓ Supports pagination (limit, startingAfter, endingBefore)
- ✓ ETag support: 304 NOT_MODIFIED if unchanged
- ✓ ETag format: "{groupId}-{lastModifiedMillis}"
- ✓ Cache-Control: no-cache, must-revalidate
- ✓ Member-only access (403 for non-members)

**Special Considerations:**
- Test ETag with group feed changes
- Verify cheap ETag check (2 RCUs) vs expensive feed query
- Test with large feeds and pagination

### GET /groups/{groupId}/feed-items
**Test Cases:**
- ✓ Returns paginated feed items with cursor
- ✓ Respects limit (1-50)
- ✓ Returns nextToken for pagination
- ✓ Member-only access

### POST /groups/{groupId}/invite-code
**Test Cases:**
- ✓ Owner/admin generates unique invite code
- ✓ Code stored as separate entity (PK: inviteCode, SK: groupId)
- ✓ Returns code and expiration
- ✓ Non-owner/admin returns 403

### GET /groups/invite/{inviteCode}
**Test Cases:**
- ✓ Returns group preview (name, description, member count)
- ✓ Rate limited by IP + inviteCode (429 on abuse)
- ✓ Returns 404 for invalid/expired code
- ✓ Public endpoint (no JWT required)

**Special Considerations:**
- Mock rate limiting cache
- Test with expired invite codes

### POST /groups/invite/join
**Test Cases:**
- ✓ Authenticated user joins group via code
- ✓ Creates GroupMembership with MEMBER role
- ✓ Returns full group DTO
- ✓ Invalid code returns 404
- ✓ Already a member is idempotent

---

## 4. Hangout APIs (`/hangouts`, `/events`) - **HIGH PRIORITY**

**External Dependencies**: DynamoDB (Hangouts, GroupHangouts, UserAttendance, Polls, Cars), S3 (images)

### POST /hangouts
**Test Cases:**
- ✓ Creates hangout with required fields (title, associatedGroups)
- ✓ Optional fields: description, startTime, endTime, location, fuzzyTime, imageUrl
- ✓ GroupHangout records created for each associated group
- ✓ Creator added to hosts
- ✓ Returns full Hangout object
- ✓ Validates user is member of associated groups

### GET /hangouts/{hangoutId}
**Test Cases:**
- ✓ Returns HangoutDetailDTO with polls, cars, attendance
- ✓ Efficient query: 1 hangout + batch polls + batch cars + batch attendance
- ✓ User must be in associated group or invited
- ✓ Returns 403 for non-members
- ✓ Returns 404 if hangout doesn't exist

**Special Considerations:**
- Verify batch queries are efficient (no N+1)
- Test with hangout containing many polls/cars

### PATCH /hangouts/{hangoutId}
**Test Cases:**
- ✓ Host updates title, description, times, location, fuzzyTime, imageUrl
- ✓ Partial updates work
- ✓ Non-host returns 403
- ✓ Updates lastModified timestamp
- ✓ Updates group feed (lastHangoutModified)

### DELETE /hangouts/{hangoutId}
**Test Cases:**
- ✓ Host deletes hangout
- ✓ Cascades: GroupHangouts, UserAttendance, Polls, Cars, Attributes
- ✓ Non-host returns 403
- ✓ Returns 204 NO_CONTENT

**Special Considerations:**
- Verify all child entities deleted
- Test with hangout in event series

### PUT /hangouts/{hangoutId}/interest
**Test Cases:**
- ✓ Sets user interest (GOING, MAYBE, NOT_GOING)
- ✓ Creates/updates UserAttendance record
- ✓ Member-only access
- ✓ Returns 200 OK

### DELETE /hangouts/{hangoutId}/interest
**Test Cases:**
- ✓ Removes user attendance record
- ✓ Idempotent (200 even if no record)
- ✓ Returns 204 NO_CONTENT

### PATCH /{eventId} (Legacy)
**Test Cases:**
- ✓ Updates title (multi-step pointer update)
- ✓ Updates description
- ✓ Updates associatedGroups (creates/deletes GroupHangout pointers)
- ✓ Host-only access

### POST /{eventId}/groups (Legacy)
**Test Cases:**
- ✓ Associates hangout with additional groups
- ✓ Creates GroupHangout pointers
- ✓ User must be member of target groups

### DELETE /{eventId}/groups (Legacy)
**Test Cases:**
- ✓ Disassociates hangout from groups
- ✓ Deletes GroupHangout pointers
- ✓ Cannot remove all groups

---

## 5. Poll APIs (`/hangouts/{eventId}/polls`) - **HIGH PRIORITY**

**External Dependencies**: DynamoDB (Polls, PollOptions, Votes)

### POST /hangouts/{eventId}/polls
**Test Cases:**
- ✓ Creates poll with title and options
- ✓ Creates PollOption records for each option
- ✓ Returns Poll with options
- ✓ Host-only access
- ✓ Validates at least 2 options
- ✓ Empty options list handled (poll created without options)

### GET /hangouts/{eventId}/polls
**Test Cases:**
- ✓ Returns all polls for event with options
- ✓ Batch query for efficiency
- ✓ Member access required

### GET /hangouts/{eventId}/polls/{pollId}
**Test Cases:**
- ✓ Returns poll detail with options and votes
- ✓ Vote counts aggregated per option
- ✓ Shows user's vote if any
- ✓ Member access required

### POST /hangouts/{eventId}/polls/{pollId}/vote
**Test Cases:**
- ✓ Casts vote for single-choice poll
- ✓ Updates vote if already voted (replaces previous)
- ✓ Multi-vote polls allow multiple options
- ✓ Returns Vote record
- ✓ Member access required

### DELETE /hangouts/{eventId}/polls/{pollId}/vote
**Test Cases:**
- ✓ Removes user's vote
- ✓ Supports removing specific option (multi-vote polls)
- ✓ Idempotent (204 even if no vote)

### POST /hangouts/{eventId}/polls/{pollId}/options
**Test Cases:**
- ✓ Host adds new option to existing poll
- ✓ Returns PollOption
- ✓ Option text validated (non-empty)

### DELETE /hangouts/{eventId}/polls/{pollId}/options/{optionId}
**Test Cases:**
- ✓ Host deletes poll option
- ✓ Cascades to delete votes for that option
- ✓ Cannot delete if only 1-2 options remain

### DELETE /hangouts/{eventId}/polls/{pollId}
**Test Cases:**
- ✓ Host deletes poll
- ✓ Cascades: options, votes
- ✓ Returns 204 NO_CONTENT

**Special Considerations:**
- Test with large number of votes (aggregation performance)
- Verify cascade deletes

---

## 6. Carpool APIs (`/events/{eventId}/carpool`) - **MEDIUM PRIORITY**

**External Dependencies**: DynamoDB (Cars, CarRiders, NeedsRide)

### POST /events/{eventId}/carpool/cars - MEDIUM
**Test Cases:**
- ✓ Offers car with totalCapacity
- ✓ Optional: departureLocation, departureTime, notes
- ✓ Returns Car record
- ✓ Member access required
- ✓ One car per user per event

### GET /events/{eventId}/carpool/cars - MEDIUM
**Test Cases:**
- ✓ Returns all cars with rider details
- ✓ Shows available seats (totalCapacity - riders)
- ✓ Batch query riders efficiently

### POST /events/{eventId}/carpool/cars/{driverId}/reserve
**Test Cases:**
- ✓ Reserves seat for user
- ✓ Decrements available seats
- ✓ Returns 409 if car full
- ✓ User cannot reserve if already has seat
- ✓ User cannot reserve own car

### DELETE /events/{eventId}/carpool/cars/{driverId}/reserve
**Test Cases:**
- ✓ Releases user's seat
- ✓ Increments available seats
- ✓ Idempotent (204 even if no reservation)

### GET /events/{eventId}/carpool/cars/{driverId}
**Test Cases:**
- ✓ Returns car detail with all riders
- ✓ Shows driver info

### PUT /events/{eventId}/carpool/cars/{driverId}
**Test Cases:**
- ✓ Driver updates car details (capacity, location, time, notes)
- ✓ Cannot reduce capacity below current riders
- ✓ Non-driver returns 403

### DELETE /events/{eventId}/carpool/cars/{driverId}
**Test Cases:**
- ✓ Driver cancels car offer
- ✓ Cascades to delete all CarRider records
- ✓ Non-driver returns 403

### GET /events/{eventId}/carpool/riderequests
**Test Cases:**
- ✓ Returns all users who need rides
- ✓ Member access required

### POST /events/{eventId}/carpool/riderequests
**Test Cases:**
- ✓ Signals user needs ride
- ✓ Optional: pickupLocation, notes
- ✓ One request per user per event

### DELETE /events/{eventId}/carpool/riderequests
**Test Cases:**
- ✓ Cancels ride request
- ✓ Idempotent (204 even if no request)

**Special Considerations:**
- Test seat capacity edge cases (exactly full, over capacity)
- Test with many riders

---

## 7. Series APIs (`/series`) - **MEDIUM PRIORITY**

**External Dependencies**: DynamoDB (EventSeries, Hangouts)

### POST /series
**Test Cases:**
- ✓ Converts existing hangout to series
- ✓ Creates new member hangout
- ✓ Both hangouts linked to series
- ✓ Returns EventSeriesDTO
- ✓ Host-only access on initial hangout

### GET /series/{seriesId}
**Test Cases:**
- ✓ Returns series detail with all hangout details
- ✓ Batch query all members
- ✓ Member must have access to series

### POST /series/{seriesId}/hangouts
**Test Cases:**
- ✓ Adds new hangout to series
- ✓ Hangout linked to series
- ✓ Returns updated series
- ✓ Host access required

### DELETE /series/{seriesId}/hangouts/{hangoutId}
**Test Cases:**
- ✓ Unlinks hangout from series (doesn't delete hangout)
- ✓ Hangout remains standalone
- ✓ Cannot unlink if only 1 member left (must delete series)

### PUT /series/{seriesId}
**Test Cases:**
- ✓ Updates series name, description
- ✓ Optimistic locking with version
- ✓ Host access required

### DELETE /series/{seriesId}
**Test Cases:**
- ✓ Deletes series and all member hangouts (cascade)
- ✓ Host access required
- ✓ Atomic operation

**Special Considerations:**
- Test with large series (many hangouts)
- Verify cascade deletes

---

## 8. Place APIs (`/places`) - **MEDIUM PRIORITY**

**External Dependencies**: DynamoDB (Places - both UserPlace and GroupPlace)

### GET /places
**Test Cases:**
- ✓ Returns user places if userId param
- ✓ Returns group places if groupId param
- ✓ Returns both if both params
- ✓ Filters archived places (ownerArchived=true)
- ✓ User can only see own places
- ✓ User must be group member for group places

### POST /places
**Test Cases:**
- ✓ Creates user place (owner: {type: "user", id: userId})
- ✓ Creates group place (owner: {type: "group", id: groupId})
- ✓ Required: nickname, location (lat/lng), ownerType, createdBy
- ✓ Optional: address, note, category, placeId, imageUrl
- ✓ Returns PlaceDto with generated placeId
- ✓ User must be group admin for group places

### PUT /places/{placeId}
**Test Cases:**
- ✓ Updates user place (requires userId param)
- ✓ Updates group place (requires groupId param)
- ✓ Partial updates work
- ✓ User can only update own places
- ✓ Group admin can update group places

### DELETE /places/{placeId}
**Test Cases:**
- ✓ Archives place (sets ownerArchived=true)
- ✓ Idempotent (200 even if already archived)
- ✓ User can only delete own places
- ✓ Group admin can delete group places

**Special Considerations:**
- Test dual ownership model (user vs group)
- Verify authorization for group places

---

## 9. Calendar APIs (`/calendar`) - **MEDIUM PRIORITY**

**External Dependencies**: DynamoDB (GroupMemberships - subscriptionToken field), ICS generation

### POST /calendar/subscriptions/{groupId}
**Test Cases:**
- ✓ Generates unique subscription token
- ✓ Stores in GroupMembership.subscriptionToken
- ✓ Returns CalendarSubscriptionResponse with feed URL
- ✓ User must be group member

### GET /calendar/subscriptions
**Test Cases:**
- ✓ Returns all user's calendar subscriptions
- ✓ Includes group info and feed URLs

### DELETE /calendar/subscriptions/{groupId}
**Test Cases:**
- ✓ Removes subscription token from membership
- ✓ Invalidates feed URL
- ✓ Idempotent (204 even if no token)

### GET /calendar/feed/{groupId}/{token}
**Test Cases:**
- ✓ Returns ICS calendar feed (text/calendar)
- ✓ Includes all group hangouts
- ✓ ETag support for caching
- ✓ Cache-Control headers for CloudFront
- ✓ Returns 304 NOT_MODIFIED if unchanged
- ✓ Returns 403 for invalid token
- ✓ Public endpoint (no JWT)

**Special Considerations:**
- Test ICS format compliance
- Verify ETag calculation (based on lastHangoutModified)
- Test with large calendars (many events)
- Mock CloudFront behavior

---

## 10. Device APIs (`/devices`) - **MEDIUM PRIORITY**

**External Dependencies**: DynamoDB (Devices), SNS (push notifications)

### POST /devices
**Test Cases:**
- ✓ Registers iOS device token
- ✓ Registers Android device token
- ✓ Updates existing device (idempotent)
- ✓ Invalid platform returns 400
- ✓ JWT required

### DELETE /devices
**Test Cases:**
- ✓ Removes device token
- ✓ Idempotent (200 even if not found)
- ✓ JWT required

**Special Considerations:**
- Mock SNS platform endpoint registration
- Test with duplicate tokens

---

## 11. Image APIs (`/images`) - **HIGH PRIORITY**

**External Dependencies**: S3

### GET /images/predefined
**Test Cases:**
- ✓ Returns list of predefined image paths
- ✓ Queries S3 predefined/ prefix
- ✓ Returns URLs and keys
- ✓ Public endpoint

### POST /images/upload-url
**Test Cases:**
- ✓ Generates presigned S3 upload URL
- ✓ Validates content type
- ✓ URL expires in 15 minutes
- ✓ Path includes userId: events/{userId}/{filename}
- ✓ JWT required

**Special Considerations:**
- Mock S3 presigned URL generation
- Verify URL expiration
- Test with LocalStack S3

---

## 12. Idea List APIs (`/groups/{groupId}/idea-lists`) - **LOW PRIORITY**

**External Dependencies**: DynamoDB (IdeaLists, Ideas)

### GET /groups/{groupId}/idea-lists
**Test Cases:**
- ✓ Returns all idea lists for group with ideas
- ✓ Member access required

### GET /groups/{groupId}/idea-lists/{listId}
**Test Cases:**
- ✓ Returns single idea list with all ideas
- ✓ Member access required

### POST /groups/{groupId}/idea-lists
**Test Cases:**
- ✓ Creates empty idea list
- ✓ Required: listName
- ✓ Optional: category, note

### PUT /groups/{groupId}/idea-lists/{listId}
**Test Cases:**
- ✓ Updates list name, category, note
- ✓ Member access required

### DELETE /groups/{groupId}/idea-lists/{listId}
**Test Cases:**
- ✓ Deletes list and all ideas (cascade)
- ✓ Member access required

### POST /groups/{groupId}/idea-lists/{listId}/ideas
**Test Cases:**
- ✓ Adds idea to list
- ✓ Required: ideaText
- ✓ Optional: category, note, url

### PATCH /groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}
**Test Cases:**
- ✓ Updates idea text, category, note, url
- ✓ Partial updates work

### DELETE /groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}
**Test Cases:**
- ✓ Deletes idea
- ✓ Idempotent (204)

---

## 13. Hangout Attribute APIs (`/hangouts/{hangoutId}/attributes`) - **LOW PRIORITY**

**External Dependencies**: DynamoDB (HangoutAttributes)

### POST /hangouts/{hangoutId}/attributes
**Test Cases:**
- ✓ Creates custom attribute (key-value pair)
- ✓ Returns HangoutAttributeDTO with attributeId
- ✓ Member access required

### PUT /hangouts/{hangoutId}/attributes/{attributeId}
**Test Cases:**
- ✓ Updates attribute name and/or value
- ✓ Idempotent PUT semantics

### DELETE /hangouts/{hangoutId}/attributes/{attributeId}
**Test Cases:**
- ✓ Deletes attribute
- ✓ Idempotent (200 even if doesn't exist)

---

## 14. External Event APIs (`/external`) - **LOW PRIORITY**

**External Dependencies**: External website parsing (Jsoup), Schema.org data

### POST /external/parse
**Test Cases:**
- ✓ Parses schema.org Event data from URL
- ✓ Returns ParsedEventDetailsDto (title, description, location, times)
- ✓ Returns 404 if no schema data found
- ✓ Returns 400 for invalid/unsafe URLs
- ✓ Returns 503 for network errors
- ✓ Returns 422 for unparseable data

**Special Considerations:**
- Mock HTTP client responses
- Test with various schema.org formats
- Test XSS/injection protection
- Test timeout handling

---

## 15. Time Options APIs (`/hangouts/time-options`) - **LOW PRIORITY**

**External Dependencies**: None

### GET /hangouts/time-options - LOW
**Test Cases:**
- ✓ Returns fuzzy time options array
- ✓ Includes: exact, morning, afternoon, evening, night, day, weekend

---

## Test Infrastructure Setup

### LocalStack Configuration
```yaml
services:
  - dynamodb
  - s3
  - sns
```

### DynamoDB Tables to Create
- Users (PK: id, GSI: PhoneNumberIndex)
- Groups (PK: groupId)
- GroupMemberships (PK: groupId#userId, GSI: UserIndex)
- Hangouts (PK: hangoutId)
- GroupHangouts (PK: groupId, SK: hangoutId, GSI: HangoutIndex)
- UserAttendance (PK: hangoutId#userId)
- Polls (PK: pollId, GSI: EventIndex)
- PollOptions (PK: optionId, GSI: PollIndex)
- Votes (PK: voteId, GSI: PollIndex, GSI: UserIndex)
- Cars (PK: eventId#driverId)
- CarRiders (PK: eventId#driverId#riderId)
- NeedsRide (PK: eventId#userId)
- Places (PK: ownerType#ownerId, SK: placeId)
- EventSeries (PK: seriesId)
- IdeaLists (PK: listId, GSI: GroupIndex)
- Ideas (PK: ideaId, GSI: ListIndex)
- HangoutAttributes (PK: hangoutId, SK: attributeId)
- Devices (PK: token, GSI: UserIndex)
- RefreshTokens (PK: tokenHash, GSI: UserIndex)
- InviteCodes (PK: inviteCode, SK: groupId)

### WireMock Stubs Needed
- Twilio SMS API (success, failure, rate limit)
- External event parsing (various schema.org formats)

### Test Data Fixtures
- Sample users (verified, unverified, shadow)
- Sample groups with various member roles
- Sample hangouts (past, future, all-day, fuzzy time)
- Sample polls (single-choice, multi-choice, with votes)
- Sample carpool offers
- Sample places (user and group)

### Performance Benchmarks
- Group feed query: <200ms for 100 hangouts
- Hangout detail: <150ms (1 hangout + batch children)
- User groups list: <100ms (GSI query)
- Calendar feed: <500ms for 50 events

---

## Anti-Patterns to Avoid

1. **Fragile timestamp assertions**: Use time ranges, not exact equality
2. **Order-dependent tests**: Don't assume result order unless explicitly sorted
3. **Hard-coded UUIDs**: Generate UUIDs per test for isolation
4. **Testing implementation**: Test behavior, not internal method calls
5. **Shared mutable state**: Clean database between tests
6. **Testing everything in one case**: One concept per test
7. **Ignoring edge cases**: Empty lists, null values, boundary conditions
8. **Not testing authorization**: Every endpoint needs authz tests
9. **Skipping cascade deletes**: Verify child entities removed
10. **Not mocking external APIs**: Always mock Twilio, external parsers, etc.

---

## Continuous Integration

### Test Execution Strategy
1. **Fast feedback loop**: Unit tests first (<5s)
2. **Integration tests**: Run in parallel suites (~30s total)
3. **Smoke tests**: Critical paths only for PR checks
4. **Full suite**: Pre-merge and nightly builds

### Test Grouping
- **Auth Suite (HIGH)**: All `/auth` endpoints (~5s)
- **Group Suite (HIGH)**: All `/groups` endpoints (~8s)
- **Hangout Suite (HIGH)**: All `/hangouts`, `/polls` (~10s)
- **Carpool Suite (MEDIUM)**: All `/carpool` endpoints (~4s)
- **Misc Suite (MEDIUM/LOW)**: Images, devices, places, calendar, idea lists, attributes (~6s)

---

## Summary Statistics

**Total API Endpoints**: 80 (excludes deprecated legacy APIs and unfinalized hiking)
**Total Test Cases**: ~375
**External Dependencies**: 5 (DynamoDB, S3, SNS, Twilio, Schema.org)
**Estimated Test Execution Time**: <40s (with parallel execution)

**Priority Breakdown:**
- **HIGH Priority**: 60 endpoints (~240 test cases) - Auth, Profile, Groups, Hangouts, Polls, Images
- **MEDIUM Priority**: 15 endpoints (~100 test cases) - Carpool, Series, Places, Calendar, Devices
- **LOW Priority**: 5 endpoints (~35 test cases) - Idea Lists, Hangout Attributes, External Parsing, Time Options
