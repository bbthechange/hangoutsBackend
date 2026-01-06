# Context: Authentication

**AUDIENCE:** This document is for developers and AI agents working on the authentication and authorization system.

## 1. Overview

The authentication system is based on JSON Web Tokens (JWT) with a refresh token rotation strategy. It distinguishes between web and mobile clients to handle token storage securely. The core flow involves registration with phone number verification, login to obtain tokens, and a JWT filter to authenticate subsequent API requests.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `AuthController.java` | Exposes REST endpoints for `/auth/register`, `/auth/login`, `/auth/refresh`, and `/auth/logout`. |
| `SecurityConfig.java` | Configures Spring Security, defines public vs. protected routes, and sets up the JWT filter chain. |
| `JwtAuthenticationFilter.java` | A servlet filter that intercepts every request to validate the `Authorization` header's Bearer token. |
| `JwtService.java` | A utility service for creating and validating JWT access tokens. |
| `RefreshTokenRotationService.java` | Handles the logic for exchanging a valid refresh token for a new pair of access and refresh tokens. |
| `UserService.java` | Manages user creation, retrieval, and password changes. |
| `User.java` | The `@DynamoDbBean` for a user, stored in the `Users` table. |
| `RefreshToken.java` | The `@DynamoDbBean` for a stored refresh token, stored in the `RefreshToken` table. |

## 3. Core Flows

### Registration & Verification

1.  **Endpoint:** `POST /auth/register`
2.  A `User` object is sent with a phone number and password.
3.  A new user is created in the `Users` table with an `accountStatus` of `UNVERIFIED`.
4.  An SMS with a verification code is sent to the user's phone number.
5.  **Endpoint:** `POST /auth/verify`
6.  The user sends their phone number and the code. If the code is valid, the `accountStatus` is updated to `ACTIVE`.

### Login & Token Issuance

1.  **Endpoint:** `POST /auth/login`
2.  The user submits their phone number and password.
3.  After validating credentials and ensuring the account is `ACTIVE`, the system generates two tokens:
    *   A short-lived JWT **access token** (30 minutes).
    *   A long-lived, single-use **refresh token**.
4.  The refresh token is hashed and stored in the `RefreshToken` DynamoDB table.
5.  **Response Includes User Profile:** The login response includes the complete user profile object (with password set to null) to eliminate the need for a separate `/profile` API call. This optimization:
    *   Reduces login flow from 2 network calls to 1
    *   Saves 1 DynamoDB read per login (user already fetched during authentication)
    *   Improves client performance and reduces backend costs
6.  **Client-Specific Response:**
    *   **Web Clients (default):** The access token and user object are returned in the JSON body. The refresh token is sent in a secure, `HttpOnly` cookie.
    *   **Mobile Clients (`X-Client-Type: mobile` header):** The access token, refresh token, and user object are all returned in the JSON body.

### Authenticating API Requests

1.  For any request to a protected endpoint, the client must include the access token in the `Authorization` header: `Authorization: Bearer <token>`.
2.  The `JwtAuthenticationFilter` runs before the controller.
3.  It extracts and validates the token using `JwtService`.
4.  If the token is valid, the user's ID is extracted and placed in the `SecurityContext`, authenticating the request. The user ID is also added to the `HttpServletRequest` as an attribute for easy access in controllers.
5.  If the token is missing or invalid, the filter chain proceeds without an authentication context, and the `JwtAuthenticationEntryPoint` returns a `401 Unauthorized` error.

### Refreshing Tokens

1.  **Endpoint:** `POST /auth/refresh`
2.  When the access token expires, the client calls this endpoint, sending its refresh token (either from the cookie or the stored JSON value).
3.  The `RefreshTokenRotationService` validates the refresh token against the stored hash in the database.
4.  **Differentiated Rotation by Client Type:**
    *   **Mobile Clients (`X-Client-Type: mobile`):** No rotation occurs. The same refresh token is returned with a new access token. This prevents unexpected logouts when the app is backgrounded during refresh operations.
    *   **Web Clients (default):** Token rotation occurs with a **5-minute grace period**. The old token is marked as superseded (not deleted) and remains valid for 5 minutes to handle network issues and race conditions.

### Refresh Token Grace Period (Web Only)

When a web client refreshes tokens:
1.  A new refresh token is generated and saved.
2.  The old token's `supersededAt` timestamp is set (marks it as superseded).
3.  The old token remains usable for 5 minutes (configurable via `refresh.token.web.grace-period-seconds`).
4.  After 5 minutes, attempts to use the superseded token return `401 Unauthorized`.
5.  Superseded tokens are automatically cleaned up by DynamoDB TTL (30-day expiry).

### Rate Limiting

The refresh endpoint has per-user rate limiting:
*   **Limit:** 10 requests per minute per user
*   **Response:** `429 Too Many Requests` when exceeded
*   **Implementation:** Uses Caffeine cache in `RateLimitingService`

### Audit Logging

All refresh attempts are logged for security monitoring:
```
// Success:
Token refresh: user={userId} clientType={mobile|web} ip={ip} tokenAge={minutes}min success=true

// Failure:
Token refresh failed: reason={reason} ip={ip}
```

**Security:** Raw token values are never logged.

## 4. Security Design Rationale

### Why Mobile Tokens Don't Rotate

| Aspect | Justification |
| :--- | :--- |
| **Secure Storage** | iOS Keychain provides hardware-backed security |
| **App Sandboxing** | Token extraction requires device compromise |
| **No XSS Risk** | No browser context to exploit |
| **Industry Practice** | Google, Apple, Slack use long-lived mobile tokens |
| **Reliability** | Prevents logout when app backgrounded during refresh |

### Why Web Tokens Rotate with Grace Period

| Aspect | Justification |
| :--- | :--- |
| **XSS Mitigation** | Rotation limits token lifetime if compromised |
| **HttpOnly Cookies** | Primary defense against XSS |
| **Grace Period** | Handles network issues without security degradation |
| **5-Minute Window** | Short enough to limit exposure, long enough for retries |

## 5. RefreshToken Model

| Field | Type | Purpose |
| :--- | :--- | :--- |
| `tokenId` | String | UUID primary key |
| `userId` | String | User this token belongs to |
| `tokenHash` | String | SHA-256 hash for GSI lookup |
| `securityHash` | String | BCrypt hash for validation |
| `expiryDate` | Long | Unix timestamp (30 days from creation), also TTL |
| `deviceId` | String | Optional device binding |
| `ipAddress` | String | Optional IP binding |
| `supersededAt` | Long | Epoch seconds when replaced (null = active) |

### Key Methods

```java
boolean isExpired()                          // Check if past 30-day expiry
boolean isSuperseded()                       // Check if supersededAt is set
boolean isWithinGracePeriod(gracePeriodSec)  // Check if still usable after supersede
```

## 6. Security Event Revocation

These events invalidate ALL tokens for a user (regardless of client type):

*   **Password change** - `rotationService.revokeAllUserTokens(userId)`
*   **Explicit logout-all** - User-initiated session termination
*   **Suspicious activity** - Automated security response
*   **Account compromise** - Manual admin action
