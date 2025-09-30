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
5.  **Client-Specific Response:**
    *   **Web Clients (default):** The access token is returned in the JSON body. The refresh token is sent in a secure, `HttpOnly` cookie.
    *   **Mobile Clients (`X-Client-Type: mobile` header):** Both the access token and the refresh token are returned in the JSON body.

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
4.  **Rotation:** If valid, the used refresh token is immediately revoked, and a **new** access token and a **new** refresh token are issued and returned to the client, following the same web/mobile logic as login.
