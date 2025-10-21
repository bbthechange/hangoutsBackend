package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;

/**
 * Service for managing calendar subscriptions.
 * Handles creation, listing, and deletion of calendar feed subscriptions for groups.
 */
public interface CalendarSubscriptionService {

    /**
     * Create a new calendar subscription for a group.
     * Generates a unique token and returns subscription URLs.
     * Idempotent - returns existing subscription if already subscribed.
     *
     * @param groupId ID of the group to subscribe to
     * @param userId ID of the user creating the subscription
     * @return Calendar subscription response with URLs
     * @throws com.bbthechange.inviter.exception.ForbiddenException if user is not a member of the group
     */
    CalendarSubscriptionResponse createSubscription(String groupId, String userId);

    /**
     * List all calendar subscriptions for a user.
     *
     * @param userId ID of the user
     * @return List of active calendar subscriptions
     */
    CalendarSubscriptionListResponse getUserSubscriptions(String userId);

    /**
     * Delete a calendar subscription.
     * Removes the subscription token from the group membership.
     *
     * @param groupId ID of the group
     * @param userId ID of the user
     * @throws com.bbthechange.inviter.exception.NotFoundException if subscription does not exist
     */
    void deleteSubscription(String groupId, String userId);
}
