# Context: Notifications

**AUDIENCE:** This document is for developers and AI agents working on the notification system. It assumes familiarity with the `GROUP_CRUD_CONTEXT.md`.

## 1. Overview

The notification system is responsible for sending push and SMS messages to users for events like new hangouts, group membership changes, direct invites, and scheduled reminders. Push notifications are sent to mobile devices on both iOS (via APNs) and Android (via FCM), with SMS being used for auxiliary purposes like phone number verification.

The system is designed to be extensible, with a clear separation between the high-level orchestration service (`NotificationService`) and the specific delivery channel services (`PushNotificationService`, `FcmNotificationService`, `SmsNotificationService`).

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `NotificationServiceImpl.java` | The main orchestration service. It determines *who* to notify and calls the appropriate channel-specific service. |
| `PushNotificationService.java` | Handles the construction and sending of push notifications to Apple Push Notification Service (APNs) for iOS devices. |
| `FcmNotificationService.java` | Handles the construction and sending of push notifications to Firebase Cloud Messaging (FCM) for Android devices. |
| `NotificationTextGenerator.java` | Centralizes notification text generation to ensure consistency across iOS and Android platforms. |
| `SmsNotificationService.java` | Handles sending SMS messages via AWS SNS, primarily for phone verification. |
| `DeviceController.java` | Exposes REST endpoints for `/devices` to register and unregister device tokens. |
| `DeviceService.java` | Manages the persistence of device tokens in the `Devices` DynamoDB table. |
| `Device.java` | The `@DynamoDbBean` for a user's device, stored in a separate `Devices` table. |
| `ApnsConfig.java` | Configures the `ApnsClient` using credentials stored in AWS Parameter Store. |
| `SnsConfig.java` | Configures the `SnsClient` for sending SMS messages. |
| `HangoutSchedulerService.java` | Creates/updates/deletes EventBridge Scheduler schedules for hangout reminders. |
| `HangoutReminderListener.java` | SQS listener that processes reminder messages and triggers notifications. |
| `SchedulerConfig.java` | Configuration for EventBridge Scheduler client and queue settings. |
| `SqsConfig.java` | Configuration for SQS message listener infrastructure. |

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

### Sending a "Group Member Added" Notification

This notification is sent when a user is added to a group by another user.

1.  **Trigger:** `GroupServiceImpl.addMember()` calls `NotificationServiceImpl.notifyGroupMemberAdded()` after successfully adding a member.
2.  **Self-Join Check:** If the adder and added user are the same (self-join to a public group), the notification is skipped.
3.  **Adder Name Lookup:** `NotificationServiceImpl` looks up the adder's display name via `UserService.getUserSummary()` (cached). Falls back to "Unknown" on failure.
4.  **Device Lookup:** It calls `DeviceService.getActiveDevicesForUser()` to get all active devices for the added user.
5.  **Platform Routing:** For each device:
    *   **iOS:** Calls `PushNotificationService.sendGroupMemberAddedNotification()`
    *   **Android:** Calls `FcmNotificationService.sendGroupMemberAddedNotification()`
6.  **Message Format:** `"{adder name} added you to the group {group name}"` (or fallback: `"You were added to the group {group name}"`)
7.  **Fire-and-Forget:** Notification failures do not break the member addition operation.

### Sending a "Hangout Updated" Notification (Time/Location Changes)

This notification is sent when a hangout's time or location changes, notifying users who have indicated they're "GOING" or "INTERESTED".

1.  **Trigger:** `HangoutServiceImpl.updateHangout()` detects time and/or location changes by comparing old vs new values.
2.  **Change Detection:** Uses `isTimeInfoEqual()` helper for TimeInfo comparison, and `Objects.equals()` for Address (which has Lombok's `@Data`).
3.  **User Targeting:** Only users with GOING or INTERESTED status receive notifications. The user who made the change is excluded.
4.  **Attendance Lookup:** Fetches `HangoutDetailData.getAttendance()` and filters by status.
5.  **Device Lookup:** It calls `DeviceService.getActiveDevicesForUser()` for each interested user.
6.  **Platform Routing:** For each device:
    *   **iOS:** Calls `PushNotificationService.sendHangoutUpdatedNotification()`
    *   **Android:** Calls `FcmNotificationService.sendHangoutUpdatedNotification()`
7.  **Message Format:**
    *   Time only: `"Time changed for '{hangout title}'"`
    *   Location only: `"Location changed for '{hangout title}'"`
    *   Both: `"Time and location changed for '{hangout title}'"`
8.  **Fire-and-Forget:** Notification failures do not break the hangout update operation.

### SMS Verification

1.  **Trigger:** A service (e.g., `AuthService`, not detailed here) calls `SmsNotificationService.sendVerificationCode()` with a phone number and a code.
2.  **Allowlist Check:** The service first checks if the phone number is on a pre-configured `allowlist`. This is a security and cost-control measure for testing.
3.  **Delivery:**
    *   If the number is on the allowlist, the code is simply logged to the console (`[SMS Bypass]`).
    *   If not on the allowlist, the service uses the `SnsClient` to send a real SMS message to the user's phone number.

## 4. Hangout Reminder System

The hangout reminder system sends push notifications to users 2 hours before a hangout starts. It uses AWS EventBridge Scheduler to trigger notifications at the correct time, with SQS as the message delivery mechanism.

### Architecture Overview

```
┌─────────────────┐      ┌─────────────────────┐      ┌─────────────────┐
│  Hangout CRUD   │──────▶ EventBridge Scheduler│──────▶    SQS Queue    │
│  (create/update)│      │  (one-time schedule) │      │(hangout-reminders)│
└─────────────────┘      └─────────────────────┘      └────────┬────────┘
                                                               │
                                                               ▼
┌─────────────────┐      ┌─────────────────────┐      ┌─────────────────┐
│  APNs / FCM     │◀─────│ NotificationService │◀─────│ SQS Listener    │
│  (push delivery)│      │ (send to devices)   │      │(HangoutReminder)│
└─────────────────┘      └─────────────────────┘      └─────────────────┘
```

### Schedule Lifecycle

#### On Hangout Create

1. `HangoutServiceImpl.createHangout()` saves the hangout
2. Calls `HangoutSchedulerService.scheduleReminder(hangout)`
3. If hangout has a `startTimestamp`:
   - Calculate reminder time = `startTimestamp - 2 hours`
   - Skip if reminder time already passed (< 1 minute in future)
   - Create EventBridge schedule with `at(yyyy-MM-ddTHH:mm:ss)` expression
   - Schedule group: `hangout-reminders`
   - Schedule name: `hangout-{hangoutId}`
   - Target: SQS queue ARN
   - Message body: `{"hangoutId":"..."}`
   - `ActionAfterCompletion: DELETE` (auto-cleanup)
4. Store `reminderScheduleName` in hangout record

#### On Hangout Update (Time Change)

1. `HangoutServiceImpl.updateHangout()` detects time change
2. Calls `hangoutRepository.clearReminderSentAt(hangoutId)` to allow new reminder
3. Calls `HangoutSchedulerService.scheduleReminder(hangout)` to update schedule
4. EventBridge schedule is updated with new trigger time

#### On Hangout Cancel/Delete

1. `HangoutServiceImpl.cancelHangout()` is called
2. Calls `HangoutSchedulerService.cancelReminder(hangout)`
3. Deletes the EventBridge schedule if it exists

### Message Processing Flow

1. **EventBridge Scheduler** triggers at reminder time (±5 min flexible window)
2. **SQS** receives message: `{"hangoutId":"..."}`
3. **HangoutReminderListener** (`@SqsListener`) consumes message:
   - Parses `hangoutId` from JSON body
   - Fetches hangout from DynamoDB
   - Validates hangout exists and has start time
   - Checks time window (90-150 minutes before start)
   - Atomic idempotency check via `setReminderSentAtIfNull()`
   - Calls `NotificationService.sendHangoutReminder(hangout)`
4. **NotificationServiceImpl.sendHangoutReminder()**:
   - Fetches attendance data
   - Filters for GOING or INTERESTED users
   - For each user, gets devices and routes to platform service
5. **PushNotificationService/FcmNotificationService** sends push:
   - Title: "Starting Soon!"
   - Body: "{hangout title} starts in 2 hours"
   - Custom data: `type=hangout_reminder`, `hangoutId`, `groupId`

### Idempotency Mechanism

Reminders use a DynamoDB conditional update for idempotency:

```java
// HangoutRepositoryImpl.setReminderSentAtIfNull()
UpdateItemRequest.builder()
    .updateExpression("SET reminderSentAt = :timestamp, updatedAt = :now")
    .conditionExpression("attribute_not_exists(reminderSentAt)")
    .build();
```

- Returns `true` if update succeeded (this instance sends the reminder)
- Returns `false` if condition failed (another instance already sent it)
- Prevents duplicate notifications in multi-instance deployments
- Also handles SQS at-least-once delivery semantics

### Time Window Validation

The listener validates that the hangout start time is within the expected window:

```java
private static final long MIN_MINUTES_BEFORE_START = 90;   // 1.5 hours
private static final long MAX_MINUTES_BEFORE_START = 150;  // 2.5 hours
```

Messages outside this window are logged and discarded. This handles:
- Stale messages (hangout time was changed)
- Clock skew between scheduler and application
- Messages delayed in SQS

### Hangout Model Fields

```java
// In Hangout.java
private String reminderScheduleName;  // e.g., "hangout-{id}" for updates/deletion
private Long reminderSentAt;          // Epoch millis when reminder sent (idempotency)
```

### Configuration

**application.properties:**
```properties
scheduler.enabled=${SCHEDULER_ENABLED:false}
scheduler.group-name=hangout-reminders
scheduler.queue-arn=${SCHEDULER_QUEUE_ARN:}
scheduler.queue-name=hangout-reminders
scheduler.flexible-window-minutes=5
```

**application-prod.properties:**
```properties
scheduler.enabled=true
scheduler.queue-arn=arn:aws:sqs:us-west-2:871070087012:hangout-reminders
scheduler.role-arn=arn:aws:iam::871070087012:role/EventBridgeSchedulerRole
scheduler.dlq-arn=arn:aws:sqs:us-west-2:871070087012:hangout-reminder-dlq
```

### SQS Listener Configuration

**SqsConfig.java** configures the listener container:

| Setting | Value | Purpose |
|---------|-------|---------|
| `maxConcurrentMessages` | 10 | Parallel message processing |
| `maxMessagesPerPoll` | 10 | Batch size per poll |
| `pollTimeout` | 20 seconds | Long polling duration |
| `acknowledgementShutdownTimeout` | 30 seconds | Graceful shutdown wait |

The listener is conditionally enabled: `@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")`

### AWS Resources

| Resource | Name/ARN | Purpose |
|----------|----------|---------|
| SQS Queue | `hangout-reminders` | Receives scheduler messages |
| SQS DLQ | `hangout-reminder-dlq` | Failed message storage |
| Schedule Group | `hangout-reminders` | Groups all hangout schedules |
| IAM Role | `EventBridgeSchedulerRole` | Scheduler execution role |

**Queue Settings:**
- Visibility timeout: 60 seconds
- Message retention: 24 hours
- Long polling: 20 seconds

**IAM Permissions Required:**
- Scheduler role: `sqs:SendMessage` on queue and DLQ
- Application role: `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes`

### Metrics

The reminder system tracks these metrics:

| Metric | Tags | Description |
|--------|------|-------------|
| `hangout_schedule_created` | `status=success/error/conflict_resolved` | Schedule create/update |
| `hangout_schedule_deleted` | `status=success/error` | Schedule deletion |
| `hangout_reminder_total` | `status=sent/already_sent/not_found/outside_window/missing_id/lost_race/error` | Message processing |
| `hangout_reminder_notification_total` | `status=success/failure` | Individual notification sends |

### Debugging

**Check if schedule exists:**
```bash
aws scheduler get-schedule --name hangout-{hangoutId} --group-name hangout-reminders
```

**Manually send test message:**
```bash
aws sqs send-message \
  --queue-url https://sqs.us-west-2.amazonaws.com/{account}/hangout-reminders \
  --message-body '{"hangoutId":"valid-hangout-id"}'
```

**Check DLQ for failed messages:**
```bash
aws sqs receive-message \
  --queue-url https://sqs.us-west-2.amazonaws.com/{account}/hangout-reminder-dlq
```

**Verify reminder was sent (DynamoDB):**
- Check `reminderSentAt` field is populated on the hangout record
- Value is epoch milliseconds when reminder was processed

### Error Handling

| Scenario | Behavior |
|----------|----------|
| Hangout not found | Log warning, acknowledge message, increment `not_found` metric |
| Reminder already sent | Log info, acknowledge message, increment `already_sent` metric |
| Outside time window | Log warning, acknowledge message, increment `outside_window` metric |
| Failed conditional update | Log info (lost race), acknowledge message |
| Notification send failure | Log error per device, continue with other devices |
| JSON parse error | Log error, acknowledge message (no retry) |
| DynamoDB error | Log error, throw exception (message returns to queue) |

Messages are acknowledged (deleted) even on most errors to prevent infinite retry loops. Only transient infrastructure errors (DynamoDB unavailable) result in message redelivery.

## 5. Adding New Notification Types

To add a new notification type:

1. **Add method to NotificationService interface**
2. **Implement in NotificationServiceImpl** with user aggregation logic
3. **Add platform-specific methods** to `PushNotificationService` and `FcmNotificationService`
4. **Add text generation** to `NotificationTextGenerator` for consistent messaging
5. **Call from the triggering service** (e.g., HangoutService, GroupService)

For scheduled notifications (like reminders):
1. **Add scheduling logic** to `HangoutSchedulerService` or create new scheduler service
2. **Add listener** following `HangoutReminderListener` pattern
3. **Add idempotency fields** to the entity model
4. **Add repository methods** for atomic conditional updates
