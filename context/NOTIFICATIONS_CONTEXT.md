# Context: Notifications

**AUDIENCE:** This document is for developers and AI agents working on the notification system. It assumes familiarity with the `GROUP_CRUD_CONTEXT.md`.

## 1. Overview

The notification system is responsible for sending push and SMS messages to users for events like new hangouts or direct invites. The primary focus is on push notifications to mobile devices (currently iOS only), with SMS being used for auxiliary purposes like phone number verification.

The system is designed to be extensible, with a clear separation between the high-level orchestration service (`NotificationService`) and the specific delivery channel services (`PushNotificationService`, `SmsNotificationService`).

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `NotificationServiceImpl.java` | The main orchestration service. It determines *who* to notify and calls the appropriate channel-specific service. |
| `PushNotificationService.java` | Handles the construction and sending of push notifications to Apple Push Notification Service (APNs). |
| `SmsNotificationService.java` | Handles sending SMS messages via AWS SNS, primarily for phone verification. |
| `DeviceController.java` | Exposes REST endpoints for `/devices` to register and unregister device tokens. |
| `DeviceService.java` | Manages the persistence of device tokens in the `Devices` DynamoDB table. |
| `Device.java` | The `@DynamoDbBean` for a user's device, stored in a separate `Devices` table. |
| `ApnsConfig.java` | Configures the `ApnsClient` using credentials stored in AWS Parameter Store. |
| `SnsConfig.java` | Configures the `SnsClient` for sending SMS messages. |

## 3. Core Flows

### Device Registration

1.  **Endpoint:** `POST /devices`
2.  **Controller:** `DeviceController.registerDevice()` receives a `DeviceRegistrationRequest` containing the device token and platform (`ios` or `android`).
3.  **Service:** `DeviceService.registerDevice()` saves a `Device` record to the `Devices` DynamoDB table. The device token is the partition key.
4.  **Indexing:** The `Devices` table has a `UserIndex` GSI with `userId` as its partition key, allowing for efficient lookup of all devices belonging to a specific user.

### Sending a "New Hangout" Notification

This is the most common notification flow.

1.  **Trigger:** `HangoutServiceImpl` calls `NotificationServiceImpl.notifyNewHangout()` after successfully creating a new hangout.
2.  **User Aggregation:** `NotificationServiceImpl` fetches all members from all groups associated with the hangout and compiles a de-duplicated `Set` of user IDs to notify.
3.  **Device Lookup:** It iterates through the set of user IDs. For each user, it calls `DeviceService.getActiveDevicesForUser()`, which queries the `UserIndex` GSI on the `Devices` table to get a list of their active device tokens.
4.  **Push Delivery:** For each active device, it calls `PushNotificationService.sendNewHangoutNotification()`.
5.  **APNs Communication:** The `PushNotificationService` builds the JSON payload required by Apple and uses the configured `ApnsClient` to send the push notification.

### SMS Verification

1.  **Trigger:** A service (e.g., `AuthService`, not detailed here) calls `SmsNotificationService.sendVerificationCode()` with a phone number and a code.
2.  **Allowlist Check:** The service first checks if the phone number is on a pre-configured `allowlist`. This is a security and cost-control measure for testing.
3.  **Delivery:**
    *   If the number is on the allowlist, the code is simply logged to the console (`[SMS Bypass]`).
    *   If not on the allowlist, the service uses the `SnsClient` to send a real SMS message to the user's phone number.
