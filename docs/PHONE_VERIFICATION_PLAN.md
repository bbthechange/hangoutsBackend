# Phone Number Verification Implementation Plan

## 1. Overview & Goals

This document outlines the plan to implement a phone number verification system for new user sign-ups.

**Goals:**
1.  **Enhance Security:** Ensure that the user signing up has control over the phone number they provide.
2.  **Improve Data Quality:** Reduce the number of invalid or incorrect phone numbers in the user database.
3.  **Establish Foundation:** Create a reusable service for sending SMS that can be leveraged for future features like password resets or notifications.

---

## 2. Proposed User & System Flow

1.  **Sign-up:** A new user submits their phone number and other required details to the sign-up endpoint.
2.  **Account Creation:** The system creates a user record in the database but sets their status to `UNVERIFIED`.
3.  **Code Generation:** A short, random, numeric one-time password (OTP), hereafter "verification code," is generated (e.g., "123456").
4.  **Code Storage:** The verification code, the user's phone number, and an expiration timestamp (e.g., 15 minutes from creation) are stored in a new `VerificationCodes` DynamoDB table. This table will have a Time To Live (TTL) policy on the expiration attribute to handle automatic cleanup.
5.  **SMS Dispatch:** The system sends the verification code to the user's phone number via SMS.
6.  **Login Restriction:** If the `UNVERIFIED` user attempts to log in, the API will return an error indicating their account is not yet active.
7.  **Verification UI:** The client application will direct the user to a screen where they can enter the verification code.
8.  **Verification API Call:** The user submits the code to a new `/auth/verify` endpoint.
9.  **Validation:** The backend verifies that the submitted code matches the one in the database for that phone number and has not expired.
10. **Activation:** If the code is valid, the user's status is updated from `UNVERIFIED` to `ACTIVE`. The entry in the `VerificationCodes` table is deleted.
11. **Login:** The user can now log in successfully.

---

## 3. Technical Implementation Details

### 3.1. Data Model Changes

*   **`User` Table:**
    *   An attribute for user status will be added/updated (e.g., `accountStatus`). It will support the values `UNVERIFIED` and `ACTIVE`.

*   **New `VerificationCodes` Table (DynamoDB):**
    *   **`phoneNumber` (Partition Key, String):** The phone number the code was sent to.
    *   **`hashedCode` (String):** A SHA-256 hash of the generated verification code.
    *   **`failedAttempts` (Number):** A counter for incorrect verification attempts.
    *   **`expiresAt` (Number):** A Unix timestamp representing when the code expires. This attribute will be configured as the TTL key for automatic cleanup of expired records. TTL will act as a background garbage collector for database hygiene, not as the primary mechanism for real-time validation.

### 3.2. API Changes

This section describes the API contract. The `AuthController` will be a thin layer responsible for handling HTTP requests, input validation, and mapping service layer outcomes to the appropriate HTTP responses. The business logic described within each endpoint's `Functionality` subsection will be implemented in the new `AccountService`.

*   **`POST /auth/register`:**
    *   **Functionality:** The `AccountService` will create a new user with an `UNVERIFIED` status. It will then generate a 6-digit verification code, hash it (SHA-256), and store it in the `VerificationCodes` table before triggering the `SmsNotificationService` to send the raw code to the user.
    *   **Error Responses:**
        *   `409 Conflict`: `{ "error": "ACCOUNT_ALREADY_EXISTS", "message": "A user with this phone number is already registered and verified." }`
        *   `400 Bad Request`: `{ "error": "INVALID_INPUT", "message": "Invalid phone number format or password does not meet requirements." }`

*   **`POST /auth/login`:**
    *   **Functionality:** The existing authentication manager will be modified to check the user's `accountStatus` upon login. If the status is `UNVERIFIED`, the login attempt will be rejected.
    *   **Error Responses:**
        *   `403 Forbidden`: `{ "error": "ACCOUNT_NOT_VERIFIED", "message": "This account is not verified. Please complete the verification process." }`
        *   `401 Unauthorized`: `{ "error": "INVALID_CREDENTIALS", "message": "Invalid phone number or password." }`

*   **`POST /auth/verify` (New Endpoint):**
    *   **Request Body:** `{ "phoneNumber": "+15551234567", "code": "123456" }`
    *   **Functionality:** The `AccountService` will implement the full verification and brute-force protection logic:
        1.  Queries for a record in the `VerificationCodes` table by `phoneNumber`.
        2.  If **no record is found**, OR if a **record is found but is expired** (`currentTime > expiresAt`), the API returns a `VERIFICATION_CODE_EXPIRED` error.
        3.  If a record is found and is not expired, the API hashes the `submittedCode` and compares it to the `hashedCode` from the database.
        4.  If the hashes **do not match**, it increments the `failedAttempts` counter. If the counter now exceeds **10**, the record is deleted from the database, and a `VERIFICATION_CODE_EXPIRED` error is returned. Otherwise, a generic `INVALID_CODE` error is returned.
        5.  If the hashes match, the user's `accountStatus` is updated to `ACTIVE`, the verification record is deleted, and a success response is returned.
    *   **Error Responses:**
        *   `429 Too Many Requests`: `{ "error": "TOO_MANY_REQUESTS", "message": "You have made too many verification attempts. Please try again later." }`
        *   `400 Bad Request`: `{ "error": "VERIFICATION_CODE_EXPIRED", "message": "The verification code has expired. Please request a new one." }`
        *   `400 Bad Request`: `{ "error": "INVALID_CODE", "message": "The verification code is incorrect." }`

*   **`POST /auth/resend-code` (New Endpoint):**
    *   **Request Body:** `{ "phoneNumber": "+15551234567" }`
    *   **Functionality:** The `AccountService` will first verify that the phone number belongs to an existing user with an `accountStatus` of `UNVERIFIED`. It will then generate a new code and store its hash, replacing the previous entry, before triggering the `SmsNotificationService`.
    *   **Error Responses:**
        *   `429 Too Many Requests`: `{ "error": "TOO_MANY_REQUESTS", "message": "You have requested too many codes. Please try again later." }`
        *   `404 Not Found`: `{ "error": "ACCOUNT_NOT_FOUND", "message": "No account found for this phone number." }`
        *   `409 Conflict`: `{ "error": "ACCOUNT_ALREADY_VERIFIED", "message": "This account has already been verified." }`

### 3.3. Service Layer Architecture

To ensure separation of concerns, the core business logic will reside in a new `AccountService`.

*   **`AccountService` (New):** This service will orchestrate the entire account verification flow. It will use the `UserRepository` for data access and the `SmsNotificationService` for sending messages. Its methods will contain the detailed logic for registration, verification (including hash comparison and failed attempt counting), and resending codes.
*   **`SmsNotificationService` (New):** A dedicated service responsible only for interacting with the AWS SNS client to send SMS messages.
*   **`RateLimitingService` (New):** A service responsible for the in-memory rate-limiting checks.

### 3.4. Code and SMS Formatting

*   **Code Format:** Verification codes will be **6-digit numeric codes**.
*   **SMS Message Content:** To improve security and user experience (including mobile autofill), the SMS message should follow a standard format:
    > `Your Inviter code is 123456. Do not share it with anyone. This code expires in 15 minutes.`


### 3.5. Rate Limiting

For the initial implementation, a simple in-memory strategy will be used for the `POST /auth/resend-code` and `POST /auth/verify` endpoints.

*   **Strategy:** Each server instance will independently track request counts in memory via the `RateLimitingService`. A library such as Google's Guava Cache is recommended for this.
*   **Proposed Limits:**
    *   For `POST /auth/resend-code`: No more than **1 request per 60 seconds** and **5 requests per 1 hour**.
    *   For `POST /auth/verify`: A more generous limit of **20 requests per 1 hour**.
*   **Metrics & Monitoring:**
    *   When a request is blocked due to exceeding the limit, the application will publish a custom metric to Amazon CloudWatch.
    *   **Metric Name:** `RateLimitExceeded`
    *   **Dimensions:** `endpoint=/auth/resend-code` or `endpoint=/auth/verify`
    *   This metric is critical for creating alarms to detect potential abuse or widespread SMS delivery issues.
*   **Known Trade-offs:**
    *   **Imprecise Limits:** The true effective rate limit is the configured limit multiplied by the number of running server instances.
    *   **Volatility:** Rate limiting data will be lost whenever an instance restarts. This approach is a baseline for preventing trivial abuse, not a perfectly precise defense.

### 3.6. SMS Service Integration

This section details the specific steps required to integrate with AWS Simple Notification Service (SNS) for sending SMS messages.

*   **Dependency:** The AWS SDK for Java v2 dependency for SNS must be added to the `build.gradle` file:
    ```groovy
    implementation 'software.amazon.awssdk:sns:2.28.29'
    ```

*   **Client Implementation:**
    1.  **`SnsClient` Bean:** A Spring `@Configuration` class will be created to provide a singleton `SnsClient` bean. This client is the main interface to the SNS service and will be configured with the appropriate AWS Region from the application properties.
    2.  **`SmsNotificationService`:** This service will inject the `SnsClient` bean.
    3.  **Sending Logic:** The service will contain a method like `sendSms(String phoneNumber, String message)` which constructs a `PublishRequest` object. The key parameters on this request are:
        *   `phoneNumber(phoneNumber)`: The destination phone number in E.164 format.
        *   `message(message)`: The UTF-8 encoded message content.
        *   `messageAttributes()`: A map of attributes, most importantly setting the `AWS.SNS.SMS.SMSType` to `Transactional` to ensure high deliverability.
    4.  The method then calls `snsClient.publish(request)` and should include error handling for AWS exceptions (e.g., `InvalidParameterException`).

*   **Critical AWS Console Considerations:**
    *   **SMS Sandbox:** By default, all new AWS accounts are in the SMS sandbox and can only send messages to pre-verified phone numbers. To go live, a support ticket must be filed with AWS to request production access for the account.
    *   **Sender ID / Phone Number:** To send SMS messages in countries like the US, you must first lease a dedicated phone number (e.g., a 10-digit long code) from the AWS Pinpoint console. This number is then available for use by SNS. This is a required, paid resource that must be provisioned before the feature can work.
    *   **User Opt-Outs:** AWS automatically manages user opt-outs. If a user replies "STOP" to an SMS, their number is added to a global opt-out list, and future `publish` attempts to that number will fail. The application should handle this failure gracefully by logging it, not treating it as a critical system error.

---

## 4. Testing and Environment Strategy

A bypass mechanism is required to facilitate easy testing in non-production environments without needing real phone numbers or incurring SMS costs.

### 4.1. The Allowlist Solution

*   A new configuration property, `inviter.sms.allowlist`, will be introduced.
*   The `SmsNotificationService` will contain logic that checks if a phone number exists in this allowlist.
    *   **If YES:** The service will **not** call AWS SNS. Instead, it will generate a code and write it directly to the application logs (e.g., `INFO: [SMS Bypass] Verification code for +19995550001 is 123456`). The code is still saved to the database as normal.
    *   **If NO:** The service proceeds with the production logic and sends a real SMS via AWS SNS.

### 4.2. Environment-Specific Configuration

*   **`application-dev.properties` / `application-staging.properties`:**
    *   `inviter.sms.allowlist=+19995550001,+19995550002,+19995550003`
    *   Developers and QA will use these numbers and check logs to get codes.

*   **`application-prod.properties`:**
    *   `inviter.sms.allowlist=`
    *   The list will be **empty** by default to ensure all real users are verified via SMS.

---

## 5. Production Testing Workflow

*   **Primary Method (True End-to-End Test):**
    1.  Ensure the production `inviter.sms.allowlist` is empty.
    2.  Use a real, non-allowlisted phone number (e.g., a personal or Google Voice number) to sign up.
    3.  This will trigger a real SMS message through AWS SNS, confirming the entire pipeline is functional.

*   **Secondary Method (Admin Bypass):**
    1.  For administrative purposes, a specific number can be temporarily added to the production `inviter.sms.allowlist`.
    2.  Signing up with this number will log the code instead of sending an SMS, providing a backdoor for account creation without testing the SMS pipeline itself.

*   **Acquiring Test Numbers:**
    *   **Recommended:** Use Google Voice for 1-2 free, stable numbers. For a larger pool, use a paid service like Twilio to rent numbers cheaply.
    *   **Not Recommended:** Avoid public/disposable phone number websites due to extreme security risks.
