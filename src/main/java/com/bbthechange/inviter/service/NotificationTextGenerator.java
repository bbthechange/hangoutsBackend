package com.bbthechange.inviter.service;

import org.springframework.stereotype.Component;

/**
 * Shared notification text generator for iOS (APNs) and Android (FCM).
 * Centralizes text generation to ensure consistency across platforms.
 */
@Component
public class NotificationTextGenerator {

    public static final String NEW_HANGOUT_TITLE = "New Hangout";
    public static final String GROUP_MEMBER_ADDED_TITLE = "Added to Group";

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

    /**
     * Generate body text for group member added notification.
     * @param adderName Name of user who added the member
     * @param groupName Name of the group
     * @return Notification body text
     */
    public String getGroupMemberAddedBody(String adderName, String groupName) {
        if (adderName != null && !adderName.trim().isEmpty() && !"Unknown".equals(adderName)) {
            return String.format("%s added you to the group %s", adderName, groupName);
        }
        return String.format("You were added to the group %s", groupName);
    }
}
