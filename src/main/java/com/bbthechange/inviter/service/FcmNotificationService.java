package com.bbthechange.inviter.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
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

    @Autowired
    public FcmNotificationService(
            @Autowired(required = false) FirebaseApp firebaseApp,
            NotificationTextGenerator textGenerator,
            DeviceService deviceService) {
        this.firebaseApp = firebaseApp;
        this.textGenerator = textGenerator;
        this.deviceService = deviceService;
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

        } catch (FirebaseMessagingException e) {
            handleFcmError(e, deviceToken, tokenPrefix);
        }
    }

    /**
     * Handle FCM errors and clean up invalid tokens.
     */
    private void handleFcmError(FirebaseMessagingException e, String deviceToken, String tokenPrefix) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();

        if (errorCode == MessagingErrorCode.UNREGISTERED) {
            // Token is invalid (expired, app uninstalled) - remove device from database
            logger.warn("FCM token unregistered, removing device: {}", tokenPrefix);
            try {
                deviceService.deleteDevice(deviceToken);
            } catch (Exception deleteEx) {
                logger.error("Failed to delete unregistered device {}: {}", tokenPrefix, deleteEx.getMessage());
            }
        } else if (errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
            logger.error("FCM invalid argument error for device {}: {}", tokenPrefix, e.getMessage());
        } else if (errorCode == MessagingErrorCode.QUOTA_EXCEEDED) {
            logger.warn("FCM quota exceeded for device: {}. Message not sent.", tokenPrefix);
        } else if (errorCode == MessagingErrorCode.UNAVAILABLE || errorCode == MessagingErrorCode.INTERNAL) {
            logger.warn("FCM service temporarily unavailable ({}). Device: {}", errorCode, tokenPrefix);
        } else {
            logger.error("FCM error sending to device {}: {} - {}",
                    tokenPrefix, errorCode, e.getMessage());
        }
    }
}
