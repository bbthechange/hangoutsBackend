package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Hangout;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Firebase Cloud Messaging service for Android push notifications.
 *
 * Error handling based on Firebase documentation:
 * - UNREGISTERED: Token expired or app uninstalled - device should be removed
 * - INVALID_ARGUMENT: Could be any bad argument - logged only (not necessarily bad token)
 * - QUOTA_EXCEEDED/UNAVAILABLE/INTERNAL: Transient errors - logged only
 *
 * @see <a href="https://firebase.google.com/docs/cloud-messaging/error-codes">FCM Error Codes</a>
 */
@Service
public class FcmNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(FcmNotificationService.class);

    private final FirebaseApp firebaseApp;
    private final NotificationTextGenerator textGenerator;
    private final DeviceService deviceService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public FcmNotificationService(
            @Autowired(required = false) FirebaseApp firebaseApp,
            NotificationTextGenerator textGenerator,
            DeviceService deviceService,
            MeterRegistry meterRegistry) {
        this.firebaseApp = firebaseApp;
        this.textGenerator = textGenerator;
        this.deviceService = deviceService;
        this.meterRegistry = meterRegistry;
    }

    public void sendNewHangoutNotification(String deviceToken, String hangoutId, String groupId,
                                           String hangoutTitle, String groupName, String creatorName) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for new hangout '{}'", hangoutTitle);
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.NEW_HANGOUT_TITLE)
                            .setBody(textGenerator.getNewHangoutBody(creatorName, hangoutTitle, groupName))
                            .build())
                    .putData("type", "new_hangout")
                    .putData("hangoutId", hangoutId)
                    .putData("groupId", groupId)
                    .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            logger.info("New hangout notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendGroupMemberAddedNotification(String deviceToken, String groupId,
                                                  String groupName, String adderName) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for group member added to '{}'", groupName);
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.GROUP_MEMBER_ADDED_TITLE)
                            .setBody(textGenerator.getGroupMemberAddedBody(adderName, groupName))
                            .build())
                    .putData("type", "group_member_added")
                    .putData("groupId", groupId)
                    .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            logger.info("Group member added notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "group_member_added").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendHangoutUpdatedNotification(String deviceToken, String hangoutId, String groupId,
                                                  String hangoutTitle, String changeType, String newLocationName) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for hangout update '{}'", hangoutTitle);
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.HANGOUT_UPDATED_TITLE)
                            .setBody(textGenerator.getHangoutUpdatedBody(hangoutTitle, changeType, newLocationName))
                            .build())
                    .putData("type", "hangout_updated")
                    .putData("hangoutId", hangoutId)
                    .putData("groupId", groupId)
                    .putData("changeType", changeType)
                    .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            logger.info("Hangout update notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "hangout_updated").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendHangoutReminderNotification(String deviceToken, Hangout hangout, String groupId) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for hangout reminder '{}'", hangout.getTitle());
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.HANGOUT_REMINDER_TITLE)
                            .setBody(textGenerator.getHangoutReminderBody(hangout.getTitle()))
                            .build())
                    .putData("type", "hangout_reminder")
                    .putData("hangoutId", hangout.getHangoutId())
                    .putData("groupId", groupId)
                    .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            logger.info("Hangout reminder notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "hangout_reminder").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendWatchPartyNotification(String deviceToken, String seriesId, String groupId, String message) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for watch party update");
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(textGenerator.getWatchPartyTitle(message))
                            .setBody(textGenerator.getWatchPartyBody(message))
                            .build())
                    .putData("type", "watch_party_update")
                    .putData("seriesId", seriesId);

            if (groupId != null) {
                messageBuilder.putData("groupId", groupId);
            }

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(messageBuilder.build());
            logger.info("Watch party notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "watch_party_update").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendCarpoolNewCarNotification(String deviceToken, String hangoutId, String groupId,
                                               String hangoutTitle, String driverName) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for carpool new car '{}'", hangoutTitle);
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.CARPOOL_NEW_CAR_TITLE)
                            .setBody(textGenerator.getCarpoolNewCarBody(driverName, hangoutTitle))
                            .build())
                    .putData("type", "carpool_new_car")
                    .putData("hangoutId", hangoutId);

            if (groupId != null) {
                messageBuilder.putData("groupId", groupId);
            }

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(messageBuilder.build());
            logger.info("Carpool new car notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "carpool_new_car").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendCarpoolRiderAddedNotification(String deviceToken, String hangoutId, String groupId,
                                                   String hangoutTitle, String driverName) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping push notification for carpool rider added '{}'", hangoutTitle);
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.CARPOOL_RIDER_ADDED_TITLE)
                            .setBody(textGenerator.getCarpoolRiderAddedBody(driverName, hangoutTitle))
                            .build())
                    .putData("type", "carpool_rider_added")
                    .putData("hangoutId", hangoutId);

            if (groupId != null) {
                messageBuilder.putData("groupId", groupId);
            }

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(messageBuilder.build());
            logger.info("Carpool rider added notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "carpool_rider_added").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    public void sendMomentumChangeNotification(String deviceToken, String hangoutId, String groupId,
                                                String hangoutTitle, String message) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping momentum change notification for '{}'", hangoutTitle);
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(NotificationTextGenerator.MOMENTUM_CHANGE_TITLE)
                            .setBody(message)
                            .build())
                    .putData("type", "momentum_change")
                    .putData("hangoutId", hangoutId);

            if (groupId != null) {
                messageBuilder.putData("groupId", groupId);
            }

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(messageBuilder.build());
            logger.info("Momentum change notification sent to device: {}, messageId: {}", tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "momentum_change").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    /**
     * Send idea list activity notification (list created or ideas added).
     * Deep links to the idea list detail screen.
     */
    public void sendIdeaListNotification(String deviceToken, String groupId, String listId,
                                          String title, String body, String notificationType) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping idea list notification");
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", notificationType)
                    .putData("groupId", groupId)
                    .putData("listId", listId)
                    .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            logger.info("Idea list notification sent successfully to device: {}, messageId: {}",
                    tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", notificationType).increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    /**
     * Send idea interest milestone notification.
     * Deep links to the idea detail screen.
     */
    public void sendIdeaInterestNotification(String deviceToken, String groupId, String listId,
                                              String ideaId, String title, String body) {
        if (firebaseApp == null) {
            logger.info("FCM not configured - skipping idea interest notification");
            return;
        }

        String tokenPrefix = deviceToken.substring(0, Math.min(8, deviceToken.length())) + "...";

        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", "idea_interest")
                    .putData("groupId", groupId)
                    .putData("listId", listId)
                    .putData("ideaId", ideaId)
                    .build();

            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            logger.info("Idea interest notification sent to device: {}, messageId: {}", tokenPrefix, messageId);
            meterRegistry.counter("fcm_notification_total", "status", "success", "type", "idea_interest").increment();

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    /**
     * Handle FCM errors and clean up invalid tokens.
     */
    private void handleFcmError(FirebaseMessagingException e, String deviceToken, String tokenPrefix) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        String category;

        if (errorCode == MessagingErrorCode.UNREGISTERED) {
            // Token is invalid (expired, app uninstalled) - remove device from database
            category = "expected";
            logger.warn("FCM token unregistered, removing device: {}", tokenPrefix);
            try {
                deviceService.deleteDevice(deviceToken);
            } catch (Exception deleteEx) {
                logger.error("Failed to delete unregistered device {}: {}", tokenPrefix, deleteEx.getMessage());
            }
        } else if (errorCode == MessagingErrorCode.QUOTA_EXCEEDED ||
                   errorCode == MessagingErrorCode.UNAVAILABLE ||
                   errorCode == MessagingErrorCode.INTERNAL) {
            category = "transient";
            if (errorCode == MessagingErrorCode.QUOTA_EXCEEDED) {
                logger.warn("FCM quota exceeded for device: {}. Message not sent.", tokenPrefix);
            } else {
                logger.warn("FCM service temporarily unavailable ({}). Device: {}", errorCode, tokenPrefix);
            }
        } else {
            category = "unexpected";
            if (errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                logger.error("FCM invalid argument error for device {}: {}", tokenPrefix, e.getMessage());
            } else {
                logger.error("FCM error sending to device {}: {} - {}",
                        tokenPrefix, errorCode, e.getMessage());
            }
        }

        meterRegistry.counter("fcm_notification_total",
                "status", "error",
                "error_code", errorCode != null ? errorCode.name() : "unknown",
                "category", category
        ).increment();
    }
}
