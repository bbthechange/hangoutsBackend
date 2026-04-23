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
    public static final String HANGOUT_UPDATED_TITLE = "Hangout Updated";
    public static final String HANGOUT_REMINDER_TITLE = "Starting Soon!";

    // Watch Party notification titles
    public static final String WATCH_PARTY_NEW_EPISODE_TITLE = "New Episode Available";
    public static final String WATCH_PARTY_TITLE_UPDATED_TITLE = "Episode Renamed";
    public static final String WATCH_PARTY_EPISODE_REMOVED_TITLE = "Episode Removed";
    public static final String WATCH_PARTY_NEEDS_HOST_TITLE = "Host Needed";

    // Carpool notification titles
    public static final String CARPOOL_NEW_CAR_TITLE = "Ride Available";
    public static final String CARPOOL_RIDER_ADDED_TITLE = "Ride Confirmed";

    // Momentum notification titles
    public static final String MOMENTUM_CHANGE_TITLE = "Hangout Update";

    // Idea list notification titles
    public static final String IDEAS_ADDED_TITLE = "New Ideas";
    public static final String IDEA_LIST_CREATED_TITLE = "New Idea List";
    public static final String IDEA_INTEREST_TITLE = "Idea Update";

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

    /**
     * Generate body text for hangout update notification.
     * @param hangoutTitle Title of the hangout
     * @param changeType Type of change: "time", "location", or "time_and_location"
     * @param newLocationName The new location name (used for location changes, can be null)
     * @return Notification body text
     */
    public String getHangoutUpdatedBody(String hangoutTitle, String changeType, String newLocationName) {
        boolean hasLocation = newLocationName != null && !newLocationName.trim().isEmpty();

        return switch (changeType) {
            case "time" -> String.format("Time changed for '%s'", hangoutTitle);
            case "location" -> hasLocation
                ? String.format("Location changed for '%s', now at %s", hangoutTitle, newLocationName)
                : String.format("Location changed for '%s'", hangoutTitle);
            case "time_and_location" -> hasLocation
                ? String.format("Time and location changed for '%s', now at %s", hangoutTitle, newLocationName)
                : String.format("Time and location changed for '%s'", hangoutTitle);
            default -> String.format("'%s' was updated", hangoutTitle);
        };
    }

    /**
     * Generate body text for hangout reminder notification.
     * @param hangoutTitle Title of the hangout
     * @return Notification body text
     */
    public String getHangoutReminderBody(String hangoutTitle) {
        return hangoutTitle + " starts in 2 hours";
    }

    /**
     * Generate body text for carpool new car notification.
     * @param driverName Name of the driver offering a ride (can be null)
     * @param hangoutTitle Title of the hangout
     * @return Notification body text
     */
    public String getCarpoolNewCarBody(String driverName, String hangoutTitle) {
        if (driverName != null && !driverName.trim().isEmpty() && !"Unknown".equals(driverName)) {
            return String.format("%s offered a ride for '%s'", driverName, hangoutTitle);
        }
        return String.format("A ride was offered for '%s'", hangoutTitle);
    }

    /**
     * Generate body text for carpool rider added notification.
     * @param driverName Name of the driver who added the rider (can be null)
     * @param hangoutTitle Title of the hangout
     * @return Notification body text
     */
    public String getCarpoolRiderAddedBody(String driverName, String hangoutTitle) {
        if (driverName != null && !driverName.trim().isEmpty() && !"Unknown".equals(driverName)) {
            return String.format("%s added you to their car for '%s'", driverName, hangoutTitle);
        }
        return String.format("You were added to a car for '%s'", hangoutTitle);
    }

    /**
     * Generate the message body for a "gaining traction" momentum notification.
     */
    public static String gainingTractionMessage(String hangoutTitle, int interestedCount) {
        return String.format("'%s' is gaining traction — %d %s interested",
                hangoutTitle, interestedCount, interestedCount == 1 ? "person is" : "people are");
    }

    /**
     * Generate the message body for a ticket action notification.
     */
    public static String ticketPurchasedMessage(String actorName, String hangoutTitle) {
        if (actorName != null && !actorName.isBlank()) {
            return String.format("%s bought tickets for '%s' — it's on!", actorName, hangoutTitle);
        }
        return String.format("Tickets were purchased for '%s' — it's on!", hangoutTitle);
    }

    /**
     * Generate the message body for a manual "It's on!" confirmation.
     */
    public static String manualConfirmationMessage(String actorName, String hangoutTitle) {
        if (actorName != null && !actorName.isBlank()) {
            return String.format("%s confirmed '%s' — it's on!", actorName, hangoutTitle);
        }
        return String.format("'%s' is confirmed — it's on!", hangoutTitle);
    }

    /**
     * Generate the message body for an action nudge notification.
     */
    public static String actionNudgeMessage(String hangoutTitle, String dayLabel) {
        return String.format("'%s' is %s — consider buying tickets", hangoutTitle, dayLabel);
    }

    /**
     * Generate the message body for an empty-week nudge notification.
     */
    public static String emptyWeekMessage() {
        return "Nothing planned next week — check out your group's ideas";
    }

    // === Idea List notification body generators ===

    /**
     * Generate body for batched ideas-added notification.
     * "Alex added 'Sushi Nakazawa' to NYC Restaurants"
     * "Alex added 3 ideas to NYC Restaurants — Sushi Nakazawa, Joe's Pizza, and 1 more"
     * "Alex added 7 ideas to NYC Restaurants — tap to see what's new"
     */
    public String getIdeasAddedBody(String adderName, String listName, java.util.List<String> ideaNames) {
        int count = (ideaNames != null) ? ideaNames.size() : 0;
        String name = formatName(adderName);
        if (count == 0) {
            return String.format("%s added ideas to %s", name, listName);
        }
        if (count == 1) {
            return String.format("%s added '%s' to %s", name, ideaNames.get(0), listName);
        }
        if (count <= 3) {
            String summary = formatIdeaNameList(ideaNames);
            return String.format("%s added %d ideas to %s — %s", name, count, listName, summary);
        }
        return String.format("%s added %d ideas to %s — tap to see what's new", name, count, listName);
    }

    /**
     * Generate body for new idea list created notification.
     * "Alex created a new list: NYC Restaurants"
     */
    public String getIdeaListCreatedBody(String creatorName, String listName) {
        String name = formatName(creatorName);
        return String.format("%s created a new list: %s", name, listName);
    }

    /**
     * First Interest — sent to idea adder:
     * "Alex is also into 'Sushi Nakazawa'"
     */
    public String getFirstInterestBody(String interestedUserName, String ideaName) {
        return String.format("%s is also into '%s'", formatName(interestedUserName), ideaName);
    }

    /**
     * Broad Interest — sent to adder:
     * "Your idea 'Sushi Nakazawa' is popular — 3 people are interested"
     */
    public String getBroadInterestAdderBody(String ideaName, int interestCount) {
        return String.format("Your idea '%s' is popular — %d people are interested", ideaName, interestCount);
    }

    /**
     * Broad Interest — sent to non-interested members:
     * "3 people want to try Sushi Nakazawa — are you in?"
     */
    public String getBroadInterestBody(String ideaName, int interestCount) {
        return String.format("%d people want to try %s — are you in?", interestCount, ideaName);
    }

    /**
     * Group Consensus — sent to all members:
     * "Most of the group wants to do Sushi Nakazawa — time to make it happen?"
     */
    public String getGroupConsensusBody(String ideaName) {
        return String.format("Most of the group wants to do %s — time to make it happen?", ideaName);
    }

    /**
     * Format name with fallback to "Someone".
     */
    private String formatName(String name) {
        if (name == null || name.trim().isEmpty() || "Unknown".equals(name)) {
            return "Someone";
        }
        return name;
    }

    /**
     * Format a list of idea names for notification display.
     * "Sushi and Pizza" for 2 items
     * "Sushi, Pizza, and 1 more" for 3+ items
     */
    private String formatIdeaNameList(java.util.List<String> names) {
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        int remaining = names.size() - 2;
        return names.get(0) + ", " + names.get(1) + ", and " + remaining + " more";
    }

    /**
     * Determine the appropriate title for a watch party notification based on message content.
     * @param message The notification message
     * @return Appropriate notification title
     */
    public String getWatchPartyTitle(String message) {
        if (message == null) {
            return WATCH_PARTY_NEW_EPISODE_TITLE;
        }
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("cancelled") || lowerMessage.contains("removed")) {
            return WATCH_PARTY_EPISODE_REMOVED_TITLE;
        }
        if (lowerMessage.contains("renamed") || lowerMessage.contains("title changed")) {
            return WATCH_PARTY_TITLE_UPDATED_TITLE;
        }
        if (lowerMessage.contains("needs a host") || lowerMessage.contains("volunteer")) {
            return WATCH_PARTY_NEEDS_HOST_TITLE;
        }
        return WATCH_PARTY_NEW_EPISODE_TITLE;
    }

    /**
     * Get the body text for a watch party notification.
     * @param message The notification message
     * @return Notification body text
     */
    public String getWatchPartyBody(String message) {
        return message != null ? message : "Check the app for details";
    }
}
