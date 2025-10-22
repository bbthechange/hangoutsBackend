package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import org.springframework.http.ResponseEntity;

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

    /**
     * Get ICS calendar feed for a group.
     * Public endpoint that validates token and returns calendar feed with caching.
     *
     * @param groupId ID of the group
     * @param token Calendar subscription token
     * @param ifNoneMatch ETag from client for cache validation
     * @return ResponseEntity with ICS content or 304 Not Modified
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if token is invalid
     * @throws com.bbthechange.inviter.exception.ForbiddenException if group not found
     */
    ResponseEntity<String> getCalendarFeed(String groupId, String token, String ifNoneMatch);
}
