package com.bbthechange.inviter.service;

import java.util.List;

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

    // Future methods (not implementing yet):
    // void notifyUpcomingHangout(String hangoutId, List<String> interestedUserIds);
    // void notifyHangoutUpdated(String hangoutId, String updatedByUserId,
    //                          List<String> participantUserIds, String updateDescription);
}