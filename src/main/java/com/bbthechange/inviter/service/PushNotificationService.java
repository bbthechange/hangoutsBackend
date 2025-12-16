package com.bbthechange.inviter.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

@Service
public class PushNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    @Autowired(required = false)
    private ApnsClient apnsClient;

    @Autowired
    private NotificationTextGenerator textGenerator;

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${apns.bundle-id:}")
    private String bundleId;

    public void sendInviteNotification(String deviceToken, String eventTitle, String hostName) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for invite to '{}'", eventTitle);
            return;
        }
        
        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle("Event Invitation");
            String alertBody = hostName != null && !hostName.trim().isEmpty() && !"Unknown Host".equals(hostName)
                ? String.format("You've been invited to %s by %s", eventTitle, hostName)
                : String.format("You've been invited to %s", eventTitle);
            payloadBuilder.setAlertBody(alertBody);
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "invite");

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("Push notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "invite").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("Push notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "invite",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending push notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "invite",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending push notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "invite",
                    "error_type", "unexpected", "category", "unexpected").increment();
        }
    }

    public void sendEventUpdateNotification(String deviceToken, String eventTitle, String updateMessage) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for event update '{}'", eventTitle);
            return;
        }

        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle("Event Update");
            payloadBuilder.setAlertBody(String.format("%s: %s", eventTitle, updateMessage));
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "event_update");

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("Event update notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "event_update").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("Event update notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "event_update",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending event update notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "event_update",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending event update notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "event_update",
                    "error_type", "unexpected", "category", "unexpected").increment();
        }
    }

    public void sendNewHangoutNotification(String deviceToken, String hangoutId, String groupId,
                                          String hangoutTitle, String groupName, String creatorName) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for new hangout '{}'", hangoutTitle);
            return;
        }

        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle(NotificationTextGenerator.NEW_HANGOUT_TITLE);
            payloadBuilder.setAlertBody(textGenerator.getNewHangoutBody(creatorName, hangoutTitle, groupName));
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "new_hangout");
            payloadBuilder.addCustomProperty("hangoutId", hangoutId);
            payloadBuilder.addCustomProperty("groupId", groupId);

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("New hangout notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "new_hangout").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("New hangout notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "new_hangout",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending new hangout notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "new_hangout",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending new hangout notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "new_hangout",
                    "error_type", "unexpected", "category", "unexpected").increment();
        }
    }

    /**
     * Categorize APNs rejection reason as expected or unexpected.
     * Expected: User/device issues (app uninstalled, token expired)
     * Unexpected: Configuration issues on our side
     */
    private String categorizeApnsRejection(String reason) {
        if (reason == null) return "unexpected";
        return switch (reason) {
            case "BadDeviceToken", "Unregistered", "DeviceTokenNotForTopic",
                 "ExpiredToken", "InvalidToken" -> "expected";
            default -> "unexpected";
        };
    }
}