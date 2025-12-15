package com.bbthechange.inviter.service;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.util.SimpleApnsPayloadBuilder;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class PushNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    @Autowired(required = false)
    private ApnsClient apnsClient;

    @Autowired
    private NotificationTextGenerator textGenerator;

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
            } else {
                logger.error("Push notification failed for device: {}. Reason: {}", 
                    deviceToken.substring(0, 8) + "...", response.getRejectionReason());
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending push notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending push notification", e);
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
            } else {
                logger.error("Event update notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", response.getRejectionReason());
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending event update notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending event update notification", e);
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
            } else {
                logger.error("New hangout notification failed for device: {}. Reason: {}",
                    deviceToken.substring(0, 8) + "...", response.getRejectionReason());
            }

        } catch (ExecutionException | InterruptedException e) {
            logger.error("Error sending new hangout notification to device: {}", deviceToken.substring(0, 8) + "...", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unexpected error sending new hangout notification", e);
        }
    }
}