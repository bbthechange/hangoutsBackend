# Context: Password Reset

**AUDIENCE:** This document is for developers and AI agents working on the password reset feature.

## 1. Overview

The password reset system allows users to reset forgotten passwords via SMS verification. It uses a three-step flow: request reset → verify SMS code → reset password with auto-login. The system reuses the existing Twilio SMS infrastructure and implements strong security controls including rate limiting, one-time tokens, and account enumeration prevention.

**Key Design Decision:** Uses Twilio for SMS verification (code generation/validation handled by Twilio), with DynamoDB tracking reset request state and intent.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `PasswordResetService.java` | Orchestrates the 3-step password reset flow. Main business logic. |
| `PasswordResetRequestRepository.java` | DynamoDB operations for password reset tracking with GSI lookups. |
| `PasswordResetRequest.java` | DynamoDB entity tracking reset state (userId PK, phone/email GSIs, TTL). |
| `ResetMethod.java` | Enum: `PHONE` or `EMAIL` (email support not yet implemented). |
| `JwtService.java` | Generates and validates 15-minute password reset tokens (type="password_reset" claim). |
| `RateLimitingService.java` | Enforces 1 request/hour per phone, 10 verify attempts/hour. |
| `AuthController.java` | Three endpoints: `/request-password-reset`, `/verify-reset-code`, `/reset-password`. |

## 3. Core Flows

### Step 1: Request Password Reset

**Endpoint:** `POST /auth/request-password-reset`

1. Client sends phone number
2. Rate limiting checked (1 request per hour per phone)
3. User lookup by phone number
4. **If user not found or not ACTIVE:** Return generic success message (prevents enumeration)
5. **If user found and ACTIVE:**
   - Create `PasswordResetRequest` (userId PK, method=PHONE, codeVerified=false, tokenUsed=false, TTL=1 day)
   - Overwrite any existing reset request (same userId PK)
   - Send SMS verification code via `SmsValidationService` (Twilio)
6. Always return: `"If an account exists for this number, you'll receive a password reset code via SMS."`

### Step 2: Verify SMS Code

**Endpoint:** `POST /auth/verify-reset-code`

1. Client sends phone number + 6-digit SMS code
2. Rate limiting checked (10 attempts per hour per phone)
3. Lookup reset request by phone number (via PhoneNumberIndex GSI)
4. Verify method is PHONE
5. Validate SMS code with Twilio via `SmsValidationService.verifyCode()`
6. **If code valid:**
   - Mark reset request `codeVerified=true`
   - Generate JWT reset token (15-min expiration, type="password_reset" claim)
   - Return reset token to client
7. **If code invalid/expired:** Throw `InvalidCodeException`

### Step 3: Reset Password with Auto-Login

**Endpoint:** `POST /auth/reset-password`

1. Client sends reset token + new password
2. Validate JWT token (signature, expiration, type="password_reset")
3. Extract userId from token
4. Lookup reset request by userId (PK lookup)
5. Verify request state: `codeVerified=true` and `tokenUsed=false`
6. Update user password (BCrypt hashed via `PasswordService`)
7. Mark reset request `tokenUsed=true` (prevents replay)
8. **Security cleanup:** Revoke all user's refresh tokens via `RefreshTokenRotationService.revokeAllUserTokens()`
9. **Auto-login:** Generate new access token + refresh token
10. Return tokens to client (same web/mobile format as login)

## 4. Database Schema

**Table:** `PasswordResetRequest`

| Attribute | Type | Description |
| :--- | :--- | :--- |
| `userId` | String (PK) | User requesting reset. Ensures one active reset per user. |
| `phoneNumber` | String (GSI) | For phone-based lookups in step 2. |
| `email` | String (GSI) | For future email-based resets. Currently null. |
| `method` | ResetMethod | PHONE or EMAIL. |
| `codeVerified` | Boolean | Whether SMS/email code has been verified. |
| `tokenUsed` | Boolean | Whether reset token has been used. |
| `ipAddress` | String | IP that initiated reset (security logging). |
| `ttl` | Long | Unix timestamp. DynamoDB auto-deletes after 1 day. |

**GSIs:**
- `PhoneNumberIndex` on `phoneNumber` - Used in verify step
- `EmailIndex` on `email` - For future email support

**TTL:** Configured on `ttl` attribute. Records auto-delete after 1 day.

## 5. Security Features

### Account Enumeration Prevention
- All reset requests return same generic success message
- No indication whether account exists
- Same response time regardless of account existence

### Rate Limiting
```java
// In RateLimitingService
isPasswordResetRequestAllowed(phoneNumber)  // 1 request per hour
isPasswordResetVerifyAllowed(phoneNumber)   // 10 attempts per hour
```

**Why different limits:** Request limit prevents SMS spam. Verify limit prevents brute force but allows legitimate retries.

**Important:** Rate limits are per-host. With 2 production hosts, effective limit is 2x (2 requests/hour, 20 verifies/hour).

### Token Security
- **15-minute expiration** - Short window for exploitation
- **Type claim validation** - Prevents access token abuse via `type="password_reset"` claim check
- **One-time use** - `tokenUsed` flag prevents replay attacks
- **Signature validation** - Standard JWT signature verification

### Session Security
- All refresh tokens revoked on password change (forces logout all devices)
- Auto-login issues new tokens after successful reset
- Password hashed with BCrypt before storage

### SMS Verification
- Twilio handles code generation, storage, expiration (10 min default)
- Twilio enforces attempt limits (5 attempts default, configurable)
- No codes stored in application database

## 6. API Endpoints

### POST /auth/request-password-reset

**Request:**
```json
{
  "phoneNumber": "+19285251044"
}
```

**Success Response (200 OK):**
```json
{
  "message": "If an account exists for this number, you'll receive a password reset code via SMS."
}
```

**Rate Limit Response (429 TOO_MANY_REQUESTS):**
```json
{
  "error": "TOO_MANY_REQUESTS",
  "message": "Please wait before requesting another password reset."
}
```

### POST /auth/verify-reset-code

**Request:**
```json
{
  "phoneNumber": "+19285251044",
  "code": "847362"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Code verified successfully",
  "resetToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 900
}
```

**Error Responses:**
- `400 BAD_REQUEST` - Invalid/expired code or no reset request
- `429 TOO_MANY_REQUESTS` - Too many verification attempts

### POST /auth/reset-password

**Request:**
```json
{
  "resetToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "newPassword": "MyNewSecurePassword123!"
}
```

**Success Response (200 OK) - Mobile:**
```json
{
  "message": "Password successfully reset",
  "accessToken": "eyJhbGci...",
  "refreshToken": "...",
  "expiresIn": 1800,
  "tokenType": "Bearer"
}
```

**Success Response (200 OK) - Web:**
Same as above, but `refreshToken` omitted from body and sent as HttpOnly cookie.

**Error Responses:**
- `400 BAD_REQUEST` - Invalid token, already used, or invalid request state

## 7. JWT Reset Token Structure

```javascript
{
  "sub": "user-uuid",           // User ID
  "type": "password_reset",     // CRITICAL: Distinguishes from access tokens
  "iat": 1234567890,            // Issued at
  "exp": 1234568790             // Expires 15 minutes after iat
}
```

**Validation in JwtService:**
```java
isPasswordResetTokenValid(token)       // Checks signature, expiration, type claim
extractUserIdFromResetToken(token)     // Validates then extracts userId
```

## 8. Integration with Existing Systems

### Reuses SmsValidationService
```java
// Same service used for registration verification
smsValidationService.sendVerificationCode(phoneNumber);
smsValidationService.verifyCode(phoneNumber, code);
```

**Implication:** Reset codes and registration codes are indistinguishable at the Twilio level. The `PasswordResetRequest` table tracks the intent.

### Reuses PasswordService
```java
// Same BCrypt hashing as registration
passwordService.encryptPassword(newPassword);
```

**No additional password validation** - Reuses whatever validation exists for registration.

### Reuses RefreshTokenRotationService
```java
// Force logout all devices on password change
refreshTokenRotationService.revokeAllUserTokens(userId);
```

## 9. Common Issues & Troubleshooting

### Issue: User doesn't receive SMS
**Causes:**
- Phone number format incorrect (must be E.164: +1XXXXXXXXXX)
- Twilio service down
- User has test phone number (check logs for `[TEST MODE]`)

**Debug:**
- Check Twilio console for delivery status
- Verify phone number format
- Check rate limiting logs

### Issue: "Reset token already used"
**Cause:** User or attacker trying to reuse token after password change

**Expected behavior:** This is correct - tokens are single-use. User must restart flow.

### Issue: "No password reset requested for this number"
**Causes:**
- User entered wrong phone number in verify step
- Reset request expired/deleted (TTL > 1 day or manual delete)
- User requested reset from different phone number

**Fix:** User must restart flow with `/request-password-reset`

### Issue: Rate limit hit immediately
**Cause:** Multiple hosts share rate limit key, but limits are per-host

**Expected behavior:** With 2 hosts, effective limit is 2x configured limit. This is acceptable trade-off for stateless rate limiting.

## 10. Future Enhancements

### Email-Based Password Reset
**Status:** Database schema ready, not implemented

**Required changes:**
1. Add email field to User model
2. Implement email verification service
3. Add method=EMAIL support in `PasswordResetService`
4. Create separate endpoints or detect reset method from request

**No database migration needed** - EmailIndex GSI already exists.

### Multi-Factor Reset Verification
Add additional verification step (e.g., security questions, backup codes) for high-value accounts.

### Password Reset Audit Log
Currently logs to application logs. Consider dedicated audit table for compliance.

### Custom Reset Token Expiration
Currently hardcoded to 15 minutes. Could make configurable per user/account type.

## 11. Test Coverage

See `TEST_PLAN_PASSWORD_RESET.md` for comprehensive test plan (80+ tests).

**Critical test scenarios:**
- Account enumeration prevention
- Rate limit enforcement
- Token replay prevention
- SMS code expiration
- One-time token use
- Auto-login after reset
- Session revocation

## 12. Deployment Considerations

### DynamoDB Table Creation
Table auto-creates on application startup via `DynamoDBTableInitializer`.

**Manual verification needed:**
```bash
aws dynamodb describe-table --table-name PasswordResetRequest
aws dynamodb describe-time-to-live --table-name PasswordResetRequest
```

TTL may need manual enablement in some environments (check `DynamoDBTableInitializer` logs).

### Environment Variables
No new environment variables required. Uses existing:
- Twilio credentials (via `SmsValidationService`)
- JWT secret (via `JwtService`)

### Rate Limiting Cache
Uses Caffeine in-memory cache. **Not shared across hosts.** Each host has independent rate limit tracking.

**Implication:** With N hosts, effective rate limit is N × configured limit.

### Security Configuration
No changes to `SecurityConfig` required. All endpoints are public (no JWT required for password reset).
