package com.bbthechange.inviter.service;

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
