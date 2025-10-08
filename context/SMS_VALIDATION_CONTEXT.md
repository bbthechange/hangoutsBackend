# Context: SMS Verification and Validation

**AUDIENCE:** This document is for developers and AI agents working on the SMS-based phone number verification system.

## 1. Overview

The SMS verification system uses a **strategy pattern** to support multiple SMS providers (AWS SNS and Twilio Verify API). The system handles:
- Sending verification codes to phone numbers
- Validating submitted codes against stored or managed verification state
- Managing verification expiration and rate limiting
- Updating user account status upon successful verification

**Current Default Provider:** Twilio Verify API (configurable via properties)

**Key Design Decision:** Both AWS and Twilio implementations are maintained to allow easy switching between providers without code changes. Twilio is preferred due to no A2P 10DLC registration requirements and better international support.

## 2. Key Files & Classes

### Core Interfaces & Services

| File | Purpose |
| :--- | :--- |
| `SmsValidationService.java` | **Interface** defining the contract for SMS verification implementations. |
| `AwsSmsValidationService.java` | AWS SNS implementation - generates codes, hashes them, stores in DynamoDB, sends via SNS. |
| `TwilioSmsValidationService.java` | Twilio Verify API implementation - delegates code generation and storage to Twilio. |
| `AccountService.java` | Orchestrates user account operations and delegates SMS verification to `SmsValidationService`. Updates user status to ACTIVE on successful verification. |
| `SmsNotificationService.java` | Low-level AWS SNS client wrapper for sending SMS messages (used by AWS implementation only). |

### Configuration

| File | Purpose |
| :--- | :--- |
| `SmsValidationConfig.java` | Spring configuration that selects provider based on `inviter.sms.provider` property. |
| `application.properties` | Default configuration with Twilio as provider. |
| `application-dev.properties` | Development configuration with dummy Twilio credentials. |
| `application-prod.properties` | Production configuration reading from environment variables. |
| `application-test.properties` | Test configuration with dummy credentials for unit tests. |

### Data Models (AWS Implementation Only)

| File | Purpose |
| :--- | :--- |
| `VerificationCode.java` | DynamoDB entity storing hashed verification codes with expiration and failed attempt tracking. |
| `VerificationCodeRepository.java` | Repository interface for DynamoDB operations on verification codes. |
| `VerificationResult.java` | Value object returned by verification operations with status (SUCCESS, CODE_EXPIRED, INVALID_CODE). |

### Controllers

| File | Purpose |
| :--- | :--- |
| `AuthController.java` | REST endpoints for `/auth/register`, `/auth/verify`, `/auth/resend-code`. Uses `AccountService` for verification. |

## 3. Architecture & Design Patterns

### Strategy Pattern

```
                  SmsValidationService (Interface)
                           ↑
                           |
         +-----------------+-----------------+
         |                                   |
    AwsSmsValidationService          TwilioSmsValidationService
         |                                   |
         +--> SmsNotificationService        +--> Twilio SDK
         +--> VerificationCodeRepository
```

**Benefits:**
- Easy provider switching via configuration
- No code changes to switch between AWS and Twilio
- Each implementation encapsulates its own logic
- Can A/B test providers or use different providers per environment

### Dependency Flow

```
AuthController
    └── AccountService
            ├── SmsValidationService (interface)
            │       ├── AwsSmsValidationService
            │       │       ├── SmsNotificationService → AWS SNS
            │       │       └── VerificationCodeRepository → DynamoDB
            │       └── TwilioSmsValidationService → Twilio Verify API
            └── UserRepository → DynamoDB Users table
```

## 4. Core Flows

### 4.1 Registration with Verification (Standard Flow)

**Endpoint:** `POST /auth/register`

```
Client                AuthController        AccountService       SmsValidationService      Provider (Twilio/AWS)
  |                         |                      |                      |                          |
  |-- Register Request ---->|                      |                      |                          |
  |                         |-- Save User -------->|                      |                          |
  |                         |   (UNVERIFIED)       |                      |                          |
  |                         |                      |                      |                          |
  |                         |-- sendVerificationCode()                    |                          |
  |                         |                      |--- sendVerificationCode(phoneNumber) ---------->|
  |                         |                      |                      |                          |
  |                         |                      |                      |<--- Code Generated ------|
  |                         |                      |                      |     & SMS Sent           |
  |                         |                      |<------ void ---------|                          |
  |                         |                      |                      |                          |
  |<-- 201 Created ---------|                      |                      |                          |
  |    "Check phone"        |                      |                      |                          |
  |                         |                      |                      |                          |
  (User receives SMS with code)                    |                      |                          |
```

**Flow Details:**
1. User submits phone, username, displayName, password
2. `AuthController` checks if user already exists and is ACTIVE (conflict)
3. New user created with `accountStatus = UNVERIFIED`
4. `AccountService.sendVerificationCode()` delegates to `SmsValidationService`
5. **AWS Path:** Generates 6-digit code, hashes with SHA-256, stores in DynamoDB, sends via SNS
6. **Twilio Path:** Calls `Verification.creator(serviceSid, phoneNumber, "sms").create()`, Twilio handles everything
7. User receives SMS with verification code

### 4.2 Code Verification

**Endpoint:** `POST /auth/verify`

```
Client                AuthController        AccountService       SmsValidationService      UserRepository
  |                         |                      |                      |                      |
  |-- Verify Request ------>|                      |                      |                      |
  |   {phone, code}         |                      |                      |                      |
  |                         |-- verifyCode() ----->|                      |                      |
  |                         |                      |--- verifyCode(phone, code) -------------->  |
  |                         |                      |                      |                      |
  |                         |                      |<--- SUCCESS/INVALID/EXPIRED --------------|
  |                         |                      |                      |                      |
  |                         |                      |-- (if SUCCESS) ----->|                      |
  |                         |                      |   findByPhoneNumber()|                      |
  |                         |                      |<--- User ------------|                      |
  |                         |                      |                      |                      |
  |                         |                      |-- update status ---->|                      |
  |                         |                      |   to ACTIVE          |                      |
  |                         |                      |   save(user) ------->|                      |
  |                         |                      |                      |                      |
  |                         |<-- VerificationResult|                      |                      |
  |<-- 200 OK / 400 Error --|                      |                      |                      |
```

**Flow Details:**
1. User submits phone number and code
2. `AccountService.verifyCode()` delegates to `SmsValidationService.verifyCode()`
3. **AWS Path:**
   - Retrieves `VerificationCode` from DynamoDB by phone number
   - Returns `CODE_EXPIRED` if not found or expired
   - Hashes submitted code and compares to stored hash
   - Increments `failedAttempts` on mismatch, returns `INVALID_CODE`
   - Deletes verification record after 10+ failed attempts (returns `CODE_EXPIRED`)
   - On success: deletes record and returns `SUCCESS`
4. **Twilio Path:**
   - Calls `VerificationCheck.creator(serviceSid).setTo(phone).setCode(code).create()`
   - Returns `SUCCESS` if Twilio status is "approved"
   - Returns `CODE_EXPIRED` for 404 (not found) or 429 (rate limit)
   - Returns `INVALID_CODE` for other failures
5. If verification succeeds, `AccountService` updates user's `accountStatus` to `ACTIVE`
6. User can now login

### 4.3 Resend Verification Code

**Endpoint:** `POST /auth/resend-code`

```
Client                AuthController        AccountService       UserRepository      SmsValidationService
  |                         |                      |                      |                      |
  |-- Resend Request ------>|                      |                      |                      |
  |   {phoneNumber}         |                      |                      |                      |
  |                         |-- Check rate limit ->|                      |                      |
  |                         |   (RateLimitingService)                     |                      |
  |                         |                      |                      |                      |
  |                         |-- sendVerificationCodeWithAccountCheck() -->|                      |
  |                         |                      |-- findByPhoneNumber()                       |
  |                         |                      |<--- User or Empty ---|                      |
  |                         |                      |                      |                      |
  |                         |                      |-- (if not found) --> throw AccountNotFoundException
  |                         |                      |-- (if ACTIVE) -----> throw AccountAlreadyVerifiedException
  |                         |                      |-- (if UNVERIFIED) -->|                      |
  |                         |                      |                      sendVerificationCode()>|
  |                         |                      |                      |                      |
  |<-- 200 OK / Error ------|                      |                      |                      |
```

**Flow Details:**
1. User requests new code (forgot/didn't receive original)
2. Rate limiting checked first (prevents spam)
3. `AccountService.sendVerificationCodeWithAccountCheck()` validates:
   - User exists (throws `AccountNotFoundException` if not)
   - User is UNVERIFIED (throws `AccountAlreadyVerifiedException` if ACTIVE)
4. If valid, sends new verification code
5. **AWS Path:** Old code is overwritten in DynamoDB (same phone number = same primary key)
6. **Twilio Path:** New verification started (Twilio manages state)

## 5. Implementation Details

### 5.1 SmsValidationService Interface

```java
public interface SmsValidationService {
    /**
     * Sends a verification code to the phone number.
     * Implementation generates or requests code, stores state, and sends SMS.
     */
    void sendVerificationCode(String phoneNumber);

    /**
     * Verifies a submitted code against stored/managed state.
     * Returns SUCCESS, CODE_EXPIRED, or INVALID_CODE.
     */
    VerificationResult verifyCode(String phoneNumber, String submittedCode);
}
```

### 5.2 AWS SNS Implementation

**Class:** `AwsSmsValidationService`

**Dependencies:**
- `VerificationCodeRepository` - DynamoDB operations
- `SmsNotificationService` - AWS SNS client wrapper
- `@Value("${app.bypass-phone-verification}")` - Testing bypass flag

**Key Constants:**
- `CODE_EXPIRY_MINUTES = 15` - Verification codes expire after 15 minutes
- `MAX_FAILED_ATTEMPTS = 10` - After 10 wrong attempts, code is deleted

**sendVerificationCode() Logic:**
1. Generate random 6-digit code (100000-999999) using `SecureRandom`
2. Hash code with SHA-256, produce 64-character hex string
3. Calculate expiration: `Instant.now() + 15 minutes` as Unix epoch seconds
4. Create `VerificationCode(phoneNumber, hashedCode, expiresAt)` with `failedAttempts = 0`
5. Save to DynamoDB (overwrites any existing code for this phone number)
6. Call `smsNotificationService.sendVerificationCode(phoneNumber, rawCode)` to send SMS

**verifyCode() Logic:**
1. Query DynamoDB for `VerificationCode` by phone number
2. If not found → return `CODE_EXPIRED`
3. Check if `currentTime > expiresAt` → delete record, return `CODE_EXPIRED`
4. Hash submitted code with SHA-256
5. Compare hashed submitted code to stored `hashedCode`
   - **Bypass:** If `bypassPhoneVerification=true`, skip comparison (always match)
6. If mismatch:
   - Increment `failedAttempts`
   - If `failedAttempts > 10` → delete record, return `CODE_EXPIRED`
   - Otherwise save updated count, return `INVALID_CODE`
7. If match → delete record, return `SUCCESS`

**Security Features:**
- Codes are hashed with SHA-256 before storage (never plaintext in DB)
- Rate limiting via failed attempts counter
- Automatic expiration after 15 minutes
- One-time use (deleted on success or max failures)

**Database Schema (VerificationCode):**
```
Table: VerificationCode
PK: phoneNumber (String)
Attributes:
  - hashedCode: String (64-char SHA-256 hex)
  - failedAttempts: Integer (default 0)
  - expiresAt: Long (Unix epoch seconds)
```

### 5.3 Twilio Verify Implementation

**Class:** `TwilioSmsValidationService`

**Dependencies:**
- `@Value("${twilio.account-sid}")` - Twilio account identifier
- `@Value("${twilio.auth-token}")` - Twilio API authentication token
- `@Value("${twilio.verify-service-sid}")` - Twilio Verify service identifier (starts with "VA")
- Twilio SDK (`com.twilio.sdk:twilio:10.6.3`)

**Constructor Logic:**
- Calls `Twilio.init(accountSid, authToken)` to initialize SDK globally
- Stores `verifyServiceSid` for API calls

**sendVerificationCode() Logic:**
1. Call Twilio API: `Verification.creator(verifyServiceSid, phoneNumber, "sms").create()`
2. Twilio generates random code (default 4-10 digits, configurable in service)
3. Twilio stores verification state with TTL (default 10 minutes, configurable)
4. Twilio sends SMS to phone number
5. On success: log verification SID and status ("pending")
6. On `ApiException`: log error and throw `RuntimeException` with message
7. On other exceptions: log and throw generic `RuntimeException`

**verifyCode() Logic:**
1. Call Twilio API: `VerificationCheck.creator(verifyServiceSid).setTo(phoneNumber).setCode(code).create()`
2. Twilio checks code against stored verification state
3. Map Twilio response to `VerificationResult`:
   - Status "approved" → `SUCCESS`
   - Status "pending" or "canceled" → `INVALID_CODE`
   - `ApiException` with status 404 → `CODE_EXPIRED` (verification not found or expired)
   - `ApiException` with status 429 → `CODE_EXPIRED` (too many attempts, Twilio rate limit)
   - Other `ApiException` → `INVALID_CODE`
   - Generic exception → `INVALID_CODE`
4. Twilio automatically handles:
   - Code expiration (configurable, default 10 min)
   - Rate limiting (configurable, default 5 attempts)
   - One-time use (verification consumed on success)

**Advantages over AWS:**
- No local code generation/storage/hashing logic needed
- No DynamoDB table required for verification state
- Twilio handles expiration, rate limiting, attempt tracking
- No A2P 10DLC campaign registration required
- Better international SMS delivery and compliance
- Built-in fraud detection and abuse prevention

**Disadvantages:**
- External dependency on Twilio service availability
- Costs per verification (vs. AWS SNS per-SMS pricing)
- Less control over code format and expiration rules
- Requires Twilio account and Verify service setup

**Twilio Verify Service Configuration:**

Service created at: https://console.twilio.com/us1/develop/verify/services

Configurable settings:
- **Friendly Name** - Appears in SMS (e.g., "Your {AppName} code is...")
- **Code Length** - 4-10 digits (Twilio generates)
- **Code Expiration** - Default 10 minutes, max 15 minutes
- **Max Attempts** - Default 5, configurable
- **Rate Limits** - Twilio's abuse prevention
- **Channels** - SMS, Voice, Email, WhatsApp (we use "sms")

### 5.4 Configuration & Provider Selection

**Class:** `SmsValidationConfig`

**How It Works:**
Uses Spring's `@ConditionalOnProperty` to create beans based on `inviter.sms.provider`:

```java
// Creates Twilio bean when provider=twilio OR property missing (default)
@ConditionalOnProperty(name = "inviter.sms.provider", havingValue = "twilio", matchIfMissing = true)

// Creates AWS bean ONLY when provider=aws
@ConditionalOnProperty(name = "inviter.sms.provider", havingValue = "aws")
```

**Credential Management:**

The configuration now uses **AWS Parameter Store** for production/staging credentials:

```java
@Bean
@ConditionalOnProperty(name = "inviter.sms.provider", havingValue = "twilio", matchIfMissing = true)
public SmsValidationService twilioSmsValidationService(
        @Value("${twilio.account-sid-parameter-name:}") String accountSidParameterName,
        @Value("${twilio.account-sid:}") String accountSidDirect,
        @Value("${twilio.auth-token-parameter-name:}") String authTokenParameterName,
        @Value("${twilio.auth-token:}") String authTokenDirect,
        @Value("${twilio.verify-service-sid-parameter-name:}") String serviceSidParameterName,
        @Value("${twilio.verify-service-sid:}") String serviceSidDirect) {

    // If parameter name provided, retrieve from AWS Parameter Store
    // Otherwise use direct value (for dev/test)
    String accountSid = accountSidParameterName.isEmpty()
        ? accountSidDirect
        : retrieveFromParameterStore(accountSidParameterName);

    // Same logic for auth token and service SID...
}

private String retrieveFromParameterStore(String parameterName) {
    try (SsmClient ssmClient = SsmClient.builder()
            .region(Region.of(awsRegion))
            .build()) {

        GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(true)
                .build();

        return ssmClient.getParameter(request).parameter().value();
    }
}
```

**Property Hierarchy:**

| Environment | Provider | Credential Source |
| :--- | :--- | :--- |
| **Default (all)** | `twilio` | `application.properties` (profile-specific overrides) |
| **Development** | `twilio` | Direct values or env vars (dummy defaults) |
| **Test (unit)** | `twilio` | Direct dummy values in properties |
| **Staging** | `twilio` | **AWS Parameter Store** (parameter names in properties) |
| **Production** | `twilio` | **AWS Parameter Store** (parameter names in properties) |

**Production/Staging Configuration (AWS Parameter Store):**

Properties specify **parameter names** instead of values:
```properties
# application-prod.properties & application-staging.properties
twilio.account-sid-parameter-name=/inviter/twilio/account-sid
twilio.auth-token-parameter-name=/inviter/twilio/auth-token
twilio.verify-service-sid-parameter-name=/inviter/twilio/verify-service-sid
```

**AWS Parameter Store Setup:**

Create these parameters in AWS Systems Manager Parameter Store:
```bash
# Account SID (String type)
aws ssm put-parameter \
  --name "/inviter/twilio/account-sid" \
  --value "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  --type String

# Auth Token (SecureString type for encryption)
aws ssm put-parameter \
  --name "/inviter/twilio/auth-token" \
  --value "your-auth-token" \
  --type SecureString

# Verify Service SID (String type)
aws ssm put-parameter \
  --name "/inviter/twilio/verify-service-sid" \
  --value "VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" \
  --type String
```

**Development Configuration (Direct Values):**

For local development, use environment variables or fallback to test values:
```properties
# application-dev.properties
twilio.account-sid=${TWILIO_ACCOUNT_SID:test-account-sid}
twilio.auth-token=${TWILIO_AUTH_TOKEN:test-auth-token}
twilio.verify-service-sid=${TWILIO_VERIFY_SERVICE_SID:test-verify-service-sid}
```

**Development Override:**
```bash
# Use real Twilio credentials locally
export TWILIO_ACCOUNT_SID="AC..."
export TWILIO_AUTH_TOKEN="..."
export TWILIO_VERIFY_SERVICE_SID="VA..."
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### 5.5 AccountService Responsibilities

After refactoring, `AccountService` has these responsibilities:

**What AccountService DOES:**
1. **User Account Management** - Creates/updates users in UserRepository
2. **Verification Orchestration** - Delegates to SmsValidationService, coordinates with user status
3. **Business Logic** - Enforces rules (can't verify already-active account, must exist to resend)
4. **Status Updates** - Changes user from UNVERIFIED to ACTIVE on successful verification

**What AccountService DOES NOT DO:**
- Generate verification codes (delegated to strategy)
- Store/retrieve verification state (delegated to strategy)
- Send SMS messages (delegated to strategy)
- Validate code format or expiration (delegated to strategy)

**Key Methods:**

```java
public void sendVerificationCode(String phoneNumber) {
    // Simple delegation, no logic
    smsValidationService.sendVerificationCode(phoneNumber);
}

public void sendVerificationCodeWithAccountCheck(String phoneNumber) {
    // Business logic: verify user exists and is UNVERIFIED
    Optional<User> user = userRepository.findByPhoneNumber(phoneNumber);
    if (user.isEmpty()) throw new AccountNotFoundException();
    if (user.get().getAccountStatus() == ACTIVE) throw new AccountAlreadyVerifiedException();
    sendVerificationCode(phoneNumber);
}

public VerificationResult verifyCode(String phoneNumber, String code) {
    // Delegate verification to strategy
    VerificationResult result = smsValidationService.verifyCode(phoneNumber, code);

    // AccountService's responsibility: update user status on success
    if (result.isSuccess()) {
        Optional<User> user = userRepository.findByPhoneNumber(phoneNumber);
        if (user.isPresent()) {
            user.get().setAccountStatus(AccountStatus.ACTIVE);
            userRepository.save(user.get());
        }
    }

    return result;
}
```

## 6. Testing Strategy

### 6.1 Unit Tests

**Test Plans Available:**
- `TEST_PLAN_AWS_SMS_VALIDATION_SERVICE.md` - 22 tests for AWS implementation
- `TEST_PLAN_TWILIO_SMS_VALIDATION_SERVICE.md` - 16 tests for Twilio implementation
- `TEST_PLAN_ACCOUNT_SERVICE_REFACTOR.md` - 10 tests for refactored AccountService
- `TEST_PLAN_SMS_VALIDATION_CONFIG.md` - 11 tests for configuration

**Key Testing Principles:**
1. **Test behavior, not implementation** - Tests verify what methods do, not how
2. **Mock external dependencies** - Twilio SDK, AWS services, repositories
3. **No DynamoDB local** - All repository interactions are mocked
4. **Test isolation** - Each test runs independently with fresh mocks

### 6.2 Integration Testing

**Manual Testing with Twilio:**
1. Set environment variables with real Twilio credentials
2. Start server: `./gradlew bootRun --args='--spring.profiles.active=dev'`
3. Register with real phone number: `POST /auth/register`
4. Receive SMS code from Twilio
5. Verify code: `POST /auth/verify`
6. Confirm user status updated to ACTIVE

**Testing with AWS (if needed):**
1. Override provider: `export SMS_PROVIDER=aws`
2. Start server with AWS profile
3. Ensure AWS credentials configured
4. Same registration flow, SMS sent via AWS SNS

### 6.3 Error Scenarios to Test

**Rate Limiting:**
- Multiple resend-code requests within short time → 429 Too Many Requests

**Invalid Codes:**
- Wrong code → 400 Bad Request with INVALID_CODE
- Expired code → 400 Bad Request with VERIFICATION_CODE_EXPIRED
- Code for different phone number → INVALID_CODE

**Account States:**
- Register already verified account → 409 Conflict
- Resend code for verified account → 409 Conflict
- Verify non-existent account → Still returns success (code validated, user just not found)

**Provider Failures:**
- Twilio API down → Registration fails with 500
- AWS SNS unavailable → Registration fails with 500
- Invalid Twilio credentials → Startup fails

## 7. API Endpoints

### POST /auth/register

**Purpose:** Register new user and send verification code

**Request Body:**
```json
{
  "phoneNumber": "+19285251044",
  "username": "testuser",
  "displayName": "Test User",
  "password": "mypassword"
}
```

**Success Response (201 Created):**
```json
{
  "message": "User registered successfully. Please check your phone for a verification code."
}
```

**Error Responses:**
- `409 Conflict` - Account already exists and is verified
  ```json
  {
    "error": "ACCOUNT_ALREADY_EXISTS",
    "message": "A user with this phone number is already registered and verified."
  }
  ```
- `500 Internal Server Error` - SMS sending failed

**Side Effects:**
- User created with `accountStatus = UNVERIFIED`
- Verification code sent via SMS (AWS or Twilio)
- Verification state created (DynamoDB for AWS, Twilio service for Twilio)

### POST /auth/verify

**Purpose:** Verify phone number with code received via SMS

**Request Body:**
```json
{
  "phoneNumber": "+19285251044",
  "code": "123456"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Account verified successfully"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid code
  ```json
  {
    "error": "INVALID_CODE",
    "message": "The verification code is incorrect."
  }
  ```
- `400 Bad Request` - Expired code
  ```json
  {
    "error": "VERIFICATION_CODE_EXPIRED",
    "message": "The verification code has expired. Please request a new one."
  }
  ```
- `429 Too Many Requests` - Rate limit exceeded
  ```json
  {
    "error": "TOO_MANY_REQUESTS",
    "message": "You have made too many verification attempts. Please try again later."
  }
  ```

**Side Effects:**
- On success: User `accountStatus` updated to `ACTIVE`
- Verification state deleted (DynamoDB for AWS, Twilio marks as used)
- On failure: Failed attempt counter incremented (AWS only)

### POST /auth/resend-code

**Purpose:** Request a new verification code (user didn't receive or lost original)

**Request Body:**
```json
{
  "phoneNumber": "+19285251044"
}
```

**Success Response (200 OK):**
```json
{
  "message": "Verification code sent successfully"
}
```

**Error Responses:**
- `404 Not Found` - Account doesn't exist
  ```json
  {
    "error": "ACCOUNT_NOT_FOUND",
    "message": "No account found for this phone number."
  }
  ```
- `409 Conflict` - Account already verified
  ```json
  {
    "error": "ACCOUNT_ALREADY_VERIFIED",
    "message": "This account has already been verified."
  }
  ```
- `429 Too Many Requests` - Rate limit exceeded
  ```json
  {
    "error": "TOO_MANY_REQUESTS",
    "message": "You have requested too many codes. Please try again later."
  }
  ```

**Side Effects:**
- New verification code sent via SMS
- Old verification state replaced with new one (AWS) or new verification started (Twilio)

## 8. Security Considerations

### 8.1 Code Storage Security (AWS Implementation)

- **Never store plaintext codes** - Always SHA-256 hashed before DB storage
- **One-time use** - Codes deleted immediately after successful verification
- **Secure random generation** - Uses `SecureRandom` for unpredictable codes
- **Failed attempt tracking** - After 10 failures, code is invalidated

### 8.2 Rate Limiting

**Implementation:** `RateLimitingService` with Caffeine cache

**Limits:**
- `/auth/verify`: Limited attempts per phone number per time window
- `/auth/resend-code`: Limited requests per phone number per time window

**Purpose:**
- Prevent brute force attacks on verification codes
- Prevent SMS spam/abuse
- Reduce costs from excessive SMS sending

### 8.3 Bypass Flag (Development Only)

**Property:** `app.bypass-phone-verification=true`

**Effect (AWS only):**
- Skips hash comparison in `verifyCode()`
- Any code value will succeed
- Used for local development without sending real SMS

**Security:**
- MUST be `false` in production
- Only affects AWS implementation
- Twilio implementation ignores this flag (always validates via API)

### 8.4 Credential Management

**Twilio Credentials:**
- **Account SID** - Public identifier, safe to log (starts with "AC")
- **Auth Token** - SECRET, never log or expose
- **Verify Service SID** - Public identifier for verify service (starts with "VA")

**Storage Strategy:**

| Environment | Storage Method |
| :--- | :--- |
| **Production/Staging** | AWS Parameter Store with parameter names in properties |
| **Development** | Direct values from environment variables or dummy defaults |
| **Tests** | Dummy values in properties (services mocked) |

**How It Works:**

`SmsValidationConfig` supports two patterns:
1. **Parameter Store (prod/staging):** Properties specify parameter names (e.g., `twilio.account-sid-parameter-name=/inviter/twilio/account-sid`). Config retrieves values from AWS Systems Manager Parameter Store at startup.
2. **Direct values (dev/test):** Properties specify actual values or env vars (e.g., `twilio.account-sid=${TWILIO_ACCOUNT_SID:test-account-sid}`). Config uses values directly.

The bean initialization checks if `*-parameter-name` properties are provided. If yes, it retrieves from Parameter Store. If no, it uses direct values.

**Benefits of Parameter Store approach:**
- Credentials encrypted at rest (SecureString type)
- IAM-controlled access (EC2/ECS roles need ssm:GetParameter permission)
- No credentials in environment variables or code
- Audit logging via CloudTrail

**Security Notes:**
- Auth token stored as SecureString in Parameter Store (encrypted)
- Requires IAM permissions for ssm:GetParameter and kms:Decrypt
- Never commit real credentials to git (only parameter names in properties)

## 9. Migration & Rollback Strategy

### 9.1 Current State
- **Default Provider:** Twilio (as of implementation date)
- **Alternate Provider:** AWS SNS (available but not default)
- **Both implementations:** Fully functional and tested

### 9.2 Switching Providers

**To switch from Twilio to AWS:**
```properties
# In application.properties or environment variable
inviter.sms.provider=aws
```

**No code changes needed** - configuration-driven

### 9.3 Provider-Specific Considerations

**If using AWS:**
- Ensure `VerificationCode` DynamoDB table exists (auto-created on startup)
- Configure AWS credentials (default credential chain)
- Consider A2P 10DLC registration requirements
- SMS allowlist for development testing

**If using Twilio:**
- Create Verify service in Twilio Console
- Set environment variables (account SID, auth token, service SID)
- No database table required
- Trial accounts require verified phone numbers

### 9.4 Removing AWS Implementation

**If Twilio proves reliable and you want to remove AWS:**

Files to delete:
- `AwsSmsValidationService.java`
- `SmsNotificationService.java`
- `SnsConfig.java`
- `VerificationCode.java`
- `VerificationCodeRepository.java`
- `VerificationCodeRepositoryImpl.java`
- Related test files

Configuration to remove:
- `inviter.sms.provider` property (Twilio becomes only option)
- AWS SNS bean configuration
- SMS allowlist properties

## 10. Troubleshooting

### Common Issues

**Problem: "Failed to send verification code via Twilio"**
- **Cause:** Invalid Twilio credentials or service SID
- **Solution:** Verify environment variables, check Twilio Console for service status

**Problem: Verification codes not received**
- **AWS:** Check AWS SNS sending limits, verify phone number format
- **Twilio:** Check Twilio Console logs, verify phone is verified (trial accounts)
- **Both:** Ensure phone number in E.164 format (+1XXXXXXXXXX)

**Problem: All codes return INVALID_CODE**
- **AWS:** Check if `bypassPhoneVerification` is set correctly
- **Twilio:** Verify service SID is correct, check Twilio Console for errors
- **Both:** Check server logs for exceptions

**Problem: Context fails to start**
- **Cause:** Missing required configuration properties
- **Solution:** Ensure all Twilio properties set if provider=twilio

**Problem: Unit tests failing**
- **Cause:** Missing dummy Twilio credentials in test properties
- **Solution:** Verify `application-test.properties` has dummy values

### Logging

**Enable debug logging:**
```properties
logging.level.com.bbthechange.inviter.service.TwilioSmsValidationService=DEBUG
logging.level.com.bbthechange.inviter.service.AwsSmsValidationService=DEBUG
logging.level.com.bbthechange.inviter.service.AccountService=DEBUG
```

**Key log messages:**

Twilio success:
```
Started Twilio verification for +1XXX: SID VExxxxx, status pending
Twilio verification check for +1XXX status: approved
```

AWS success:
```
Verification code saved for phone number: +1XXX
Verification code sent for phone number: +1XXX
Verification code successfully verified for phone number: +1XXX
```

Errors:
```
Failed to start Twilio verification for +1XXX: [error message]
Too many failed attempts for phone number: +1XXX, deleting verification code
```

## 11. Future Enhancements

**Potential Improvements:**
1. **Multiple channels** - Support voice calls, WhatsApp (Twilio supports)
2. **Custom code length** - Make configurable (currently 6 digits for AWS, Twilio default for Twilio)
3. **Custom expiration** - Make configurable (currently 15 min AWS, 10 min Twilio default)
4. **Analytics** - Track verification success rates, time-to-verify, failure reasons
5. **Localization** - Multi-language SMS messages
6. **Fallback provider** - Try Twilio, fall back to AWS on failure
7. **Provider selection per region** - Use different providers for different countries

**Known Limitations:**
- No email verification option
- No verification status endpoint (can't check if code is pending)
- No way to cancel in-flight verification
- Rate limiting is basic (could use more sophisticated algorithm)
