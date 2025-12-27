package com.bbthechange.inviter.service;

import java.util.List;
import java.util.Set;

/**
 * Service for managing and sending notifications across multiple channels.
 * Handles user deduplication, preference checking, and delegation to specific
 * notification channels (push, SMS, email, etc.).
 */
public interface NotificationService {

    /**
     * Notify all relevant users about a new hangout.
     * Automatically deduplicates users who are in multiple groups.
     * The creator is not notified about their own hangout.
     *
     * @param hangout The created hangout
     * @param creatorUserId The user who created the hangout (won't be notified)
     * @param creatorName The display name of the creator
     */
    void notifyNewHangout(com.bbthechange.inviter.model.Hangout hangout, String creatorUserId, String creatorName);

    /**
     * Notify a user that they have been added to a group.
     * Skips notification if the user added themselves (self-join).
     *
     * @param groupId The ID of the group
     * @param groupName The name of the group
     * @param addedUserId The user who was added (will be notified)
     * @param adderUserId The user who added them (used for message)
     */
    void notifyGroupMemberAdded(String groupId, String groupName, String addedUserId, String adderUserId);

    /**
     * Notify users about hangout time/location changes.
     * Only notifies users with GOING or INTERESTED status.
     * Excludes the user who made the change.
     *
     * @param hangoutId The ID of the updated hangout
     * @param hangoutTitle The title of the hangout
     * @param groupIds The associated group IDs
     * @param changeType Type of change: "time", "location", or "time_and_location"
     * @param updatedByUserId The user who made the update (won't be notified)
     * @param interestedUserIds Set of user IDs with GOING or INTERESTED status
     * @param newLocationName The new location name for location changes (can be null)
     */
    void notifyHangoutUpdated(String hangoutId, String hangoutTitle, List<String> groupIds,
                              String changeType, String updatedByUserId, Set<String> interestedUserIds,
                              String newLocationName);

    /**
     * Send reminder notification for an upcoming hangout.
     * Notifies users with GOING or INTERESTED status.
     * Called by EventBridge Scheduler 2 hours before hangout start.
     *
     * @param hangout The hangout to send reminders for
     */
    void sendHangoutReminder(com.bbthechange.inviter.model.Hangout hangout);
}