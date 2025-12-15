package com.bbthechange.inviter.service;

import org.springframework.stereotype.Component;

/**
 * Shared notification text generator for iOS (APNs) and Android (FCM).
 * Centralizes text generation to ensure consistency across platforms.
 */
@Component
public class NotificationTextGenerator {

    public static final String NEW_HANGOUT_TITLE = "New Hangout";

    /**
     * Generate body text for new hangout notification.
     * @param creatorName Name of user who created the hangout (can be null)
     * @param hangoutTitle Title of the hangout
     * @param groupName Name of the group
     * @return Notification body text
     */
    public String getNewHangoutBody(String creatorName, String hangoutTitle, String groupName) {
        if (creatorName != null && !creatorName.trim().isEmpty() && !"Unknown".equals(creatorName)) {
            return String.format("%s created '%s' in %s", creatorName, hangoutTitle, groupName);
        }
        return String.format("New hangout '%s' in %s", hangoutTitle, groupName);
    }
}
