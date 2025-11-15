# Test Plan: Add User Profile to Login Response

## Feature Description
Include user profile data in the login response to eliminate the need for a separate `/profile` API call after login.

## Changes Made
- Modified `AuthController.login()` to include a `user` object in the response (lines 176-187)
- User object contains all profile fields except password (set to null for security)

## Test Scenarios

### 1. Successful Login - Mobile Client
**Given:** Valid credentials and X-Client-Type: mobile header
**When:** POST /auth/login
**Then:**
- Response status: 200 OK
- Response contains: accessToken, expiresIn, tokenType, refreshToken
- Response contains: user object with all profile fields
- user.password is null
- user.id, phoneNumber, username, displayName match database
- user.accountStatus is ACTIVE
- user.creationDate is present

### 2. Successful Login - Web Client
**Given:** Valid credentials without X-Client-Type header
**When:** POST /auth/login
**Then:**
- Response status: 200 OK
- Response contains: accessToken, expiresIn, tokenType, user
- Response does NOT contain refreshToken in JSON (it's in cookie)
- user object contains all profile fields
- user.password is null

### 3. User Profile Completeness
**Given:** User with all optional fields populated (displayName, mainImagePath)
**When:** POST /auth/login
**Then:**
- user.displayName is included in response
- user.mainImagePath is included in response
- All fields match what GET /profile would return

### 4. User Profile with Null Fields
**Given:** User with null displayName and mainImagePath
**When:** POST /auth/login
**Then:**
- user object still present in response
- Null fields are represented as null in JSON
- No errors occur

### 5. Security - Password Never Exposed
**Given:** User with password hash in database
**When:** POST /auth/login
**Then:**
- user.password is explicitly null
- Password hash is never exposed in response

### 6. Failed Login - Invalid Credentials
**Given:** Invalid credentials
**When:** POST /auth/login
**Then:**
- Response status: 401 UNAUTHORIZED
- No user object in response
- No tokens in response

### 7. Failed Login - Unverified Account
**Given:** Valid credentials but accountStatus = UNVERIFIED
**When:** POST /auth/login
**Then:**
- Response status: 403 FORBIDDEN
- No user object in response
- Error message about verification

## Affected Endpoints
- POST /auth/login (modified)
- GET /profile (unchanged, still works for re-fetching profile)

## Backward Compatibility
- Web clients that ignore the new `user` field will continue working
- Mobile clients can choose to use the new `user` field or continue calling /profile
- No breaking changes to existing response structure

## Performance Impact
- Backend: Zero additional cost (user already fetched)
- Network: Saves 1 HTTP round-trip for clients using new field
- DynamoDB: Reduces total reads from 2 to 1 per login flow
