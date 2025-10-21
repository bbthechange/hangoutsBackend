package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.exception.NotFoundException;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.CalendarSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of CalendarSubscriptionService.
 * Manages calendar subscription tokens on GroupMembership records.
 */
@Service
public class CalendarSubscriptionServiceImpl implements CalendarSubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarSubscriptionServiceImpl.class);

    private final GroupRepository groupRepository;
    private final String baseUrl;

    @Autowired
    public CalendarSubscriptionServiceImpl(GroupRepository groupRepository,
                                         @Value("${calendar.base-url:https://api.inviter.app}") String baseUrl) {
        this.groupRepository = groupRepository;
        this.baseUrl = baseUrl;
    }

    @Override
    public CalendarSubscriptionResponse createSubscription(String groupId, String userId) {
        logger.info("Creating calendar subscription for user {} in group {}", userId, groupId);

        // Get membership to verify user is in group
        GroupMembership membership = groupRepository.findMembership(groupId, userId)
            .orElseThrow(() -> new ForbiddenException("You must be a member of this group to subscribe to its calendar"));

        // Check if already has a token (idempotent operation)
        if (membership.getCalendarToken() != null) {
            logger.debug("User {} already has calendar subscription for group {}", userId, groupId);
            return toResponse(membership);
        }

        // Generate and set token
        String token = UUID.randomUUID().toString();
        membership.setCalendarToken(token);

        // Save updated membership
        groupRepository.addMember(membership);

        logger.info("Created calendar subscription for user {} in group {} with token {}",
            userId, groupId, token.substring(0, 8) + "...");

        return toResponse(membership);
    }

    @Override
    public CalendarSubscriptionListResponse getUserSubscriptions(String userId) {
        logger.debug("Fetching calendar subscriptions for user {}", userId);

        // Get all user's memberships
        List<GroupMembership> allMemberships = groupRepository.findGroupsByUserId(userId);

        // Filter for memberships with active calendar subscriptions
        List<CalendarSubscriptionResponse> subscriptions = allMemberships.stream()
            .filter(m -> m.getCalendarToken() != null)
            .map(this::toResponse)
            .collect(Collectors.toList());

        logger.debug("Found {} calendar subscriptions for user {}", subscriptions.size(), userId);

        return new CalendarSubscriptionListResponse(subscriptions);
    }

    @Override
    public void deleteSubscription(String groupId, String userId) {
        logger.info("Deleting calendar subscription for user {} in group {}", userId, groupId);

        // Get membership
        GroupMembership membership = groupRepository.findMembership(groupId, userId)
            .orElseThrow(() -> new NotFoundException("Subscription not found"));

        // Check if has token
        if (membership.getCalendarToken() == null) {
            throw new NotFoundException("Subscription not found");
        }

        // Clear token
        membership.setCalendarToken(null);

        // Save updated membership
        groupRepository.addMember(membership);

        logger.info("Deleted calendar subscription for user {} in group {}", userId, groupId);
    }

    /**
     * Convert GroupMembership with token to CalendarSubscriptionResponse.
     *
     * @param membership The group membership with calendar token
     * @return Subscription response with URLs
     */
    private CalendarSubscriptionResponse toResponse(GroupMembership membership) {
        String subscriptionUrl = String.format("%s/v1/calendar/subscribe/%s/%s",
            baseUrl, membership.getGroupId(), membership.getCalendarToken());

        String webcalUrl = subscriptionUrl.replace("https://", "webcal://")
                                         .replace("http://", "webcal://");

        return new CalendarSubscriptionResponse(
            membership.getGroupId(), // Use groupId as subscriptionId (one subscription per user per group)
            membership.getGroupId(),
            membership.getGroupName(),
            subscriptionUrl,
            webcalUrl,
            membership.getCreatedAt()
        );
    }
}
