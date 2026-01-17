package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Hangout;
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

import java.util.List;
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

    public void sendGroupMemberAddedNotification(String deviceToken, String groupId,
                                                    String groupName, String adderName) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for group member added to '{}'", groupName);
            return;
        }

        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle(NotificationTextGenerator.GROUP_MEMBER_ADDED_TITLE);
            payloadBuilder.setAlertBody(textGenerator.getGroupMemberAddedBody(adderName, groupName));
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "group_member_added");
            payloadBuilder.addCustomProperty("groupId", groupId);

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("Group member added notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "group_member_added").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("Group member added notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "group_member_added",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending group member added notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "group_member_added",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending group member added notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "group_member_added",
                    "error_type", "unexpected", "category", "unexpected").increment();
        }
    }

    public void sendHangoutUpdatedNotification(String deviceToken, String hangoutId, String groupId,
                                                  String hangoutTitle, String changeType, String newLocationName) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for hangout update '{}'", hangoutTitle);
            return;
        }

        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle(NotificationTextGenerator.HANGOUT_UPDATED_TITLE);
            payloadBuilder.setAlertBody(textGenerator.getHangoutUpdatedBody(hangoutTitle, changeType, newLocationName));
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "hangout_updated");
            payloadBuilder.addCustomProperty("hangoutId", hangoutId);
            payloadBuilder.addCustomProperty("groupId", groupId);
            payloadBuilder.addCustomProperty("changeType", changeType);

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("Hangout update notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "hangout_updated").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("Hangout update notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "hangout_updated",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending hangout update notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "hangout_updated",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending hangout update notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "hangout_updated",
                    "error_type", "unexpected", "category", "unexpected").increment();
        }
    }

    public void sendHangoutReminderNotification(String deviceToken, Hangout hangout, String groupId) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for hangout reminder '{}'", hangout.getTitle());
            return;
        }

        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle(NotificationTextGenerator.HANGOUT_REMINDER_TITLE);
            payloadBuilder.setAlertBody(textGenerator.getHangoutReminderBody(hangout.getTitle()));
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "hangout_reminder");
            payloadBuilder.addCustomProperty("hangoutId", hangout.getHangoutId());
            payloadBuilder.addCustomProperty("groupId", groupId);

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("Hangout reminder notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "hangout_reminder").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("Hangout reminder notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "hangout_reminder",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending hangout reminder notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "hangout_reminder",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending hangout reminder notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "hangout_reminder",
                    "error_type", "unexpected", "category", "unexpected").increment();
        }
    }

    public void sendWatchPartyNotification(String deviceToken, String seriesId, String groupId, String message) {
        if (apnsClient == null) {
            logger.info("APNs not configured - skipping push notification for watch party update");
            return;
        }

        try {
            SimpleApnsPayloadBuilder payloadBuilder = new SimpleApnsPayloadBuilder();
            payloadBuilder.setAlertTitle(textGenerator.getWatchPartyTitle(message));
            payloadBuilder.setAlertBody(textGenerator.getWatchPartyBody(message));
            payloadBuilder.setBadgeNumber(1);
            payloadBuilder.setSound("default");
            payloadBuilder.addCustomProperty("type", "watch_party_update");
            payloadBuilder.addCustomProperty("seriesId", seriesId);
            if (groupId != null) {
                payloadBuilder.addCustomProperty("groupId", groupId);
            }

            String payload = payloadBuilder.build();
            String token = TokenUtil.sanitizeTokenString(deviceToken);

            SimpleApnsPushNotification pushNotification = new SimpleApnsPushNotification(token, bundleId, payload);

            PushNotificationResponse<SimpleApnsPushNotification> response = apnsClient.sendNotification(pushNotification).get();

            if (response.isAccepted()) {
                logger.info("Watch party notification sent successfully to device: {}", deviceToken.substring(0, 8) + "...");
                meterRegistry.counter("apns_notification_total", "status", "success", "type", "watch_party_update").increment();
            } else {
                Optional<String> rejectionReason = response.getRejectionReason();
                String reason = rejectionReason.orElse("unknown");
                logger.error("Watch party notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", reason);
                meterRegistry.counter("apns_notification_total",
                        "status", "rejected", "type", "watch_party_update",
                        "reason", reason, "category", categorizeApnsRejection(reason)).increment();
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending watch party notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "watch_party_update",
                    "error_type", "execution", "category", "transient").increment();
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending watch party notification", e);
            meterRegistry.counter("apns_notification_total",
                    "status", "error", "type", "watch_party_update",
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