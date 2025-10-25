package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.exception.NotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.CalendarSubscriptionService;
import com.bbthechange.inviter.service.ICalendarService;
import com.bbthechange.inviter.util.PaginatedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of CalendarSubscriptionService.
 * Manages calendar subscription tokens on GroupMembership records.
 */
@Service
public class CalendarSubscriptionServiceImpl implements CalendarSubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarSubscriptionServiceImpl.class);
    public static final int MAX_CACHE_MINUTES = 30;

    private final GroupRepository groupRepository;
    private final HangoutRepository hangoutRepository;
    private final ICalendarService iCalendarService;
    private final String baseUrl;

    @Autowired
    public CalendarSubscriptionServiceImpl(GroupRepository groupRepository,
                                         HangoutRepository hangoutRepository,
                                         ICalendarService iCalendarService,
                                         @Value("${calendar.base-url:https://api.inviter.app}") String baseUrl) {
        this.groupRepository = groupRepository;
        this.hangoutRepository = hangoutRepository;
        this.iCalendarService = iCalendarService;
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

    @Override
    public ResponseEntity<String> getCalendarFeed(String groupId, String token, String ifNoneMatch) {
        logger.debug("Calendar feed requested for group {} with token {}", groupId, token.substring(0, 8) + "...");

        // 1. Validate token and check membership (single query via CalendarTokenIndex)
        validateTokenAndMembership(token, groupId);

        // 2. Get group metadata for ETag calculation
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ForbiddenException("Group not found"));

        // 3. Calculate ETag from group's lastHangoutModified timestamp
        Instant lastModified = group.getLastHangoutModified();
        String etag = String.format("\"%s-%d\"",
            groupId,
            lastModified != null ? lastModified.toEpochMilli() : 0);

        // 4. Return 304 Not Modified if client has current version
        if (etag.equals(ifNoneMatch)) {
            logger.debug("Calendar feed for group {} not modified (ETag match)", groupId);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(CacheControl
                    .maxAge(MAX_CACHE_MINUTES, TimeUnit.MINUTES)
                    .cachePublic()
                    .mustRevalidate())
                .build();
        }

        // 5. Query future hangouts using EntityTimeIndex GSI
        List<HangoutPointer> hangouts = queryFutureHangouts(groupId);

        logger.debug("Generating ICS feed for group {} with {} future hangouts", groupId, hangouts.size());

        // 6. Generate ICS content
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // 7. Return with caching headers (CloudFront-ready)
        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(CacheControl
                .maxAge(MAX_CACHE_MINUTES, TimeUnit.MINUTES)
                .cachePublic()                 // Allow CDN caching
                .mustRevalidate())             // Check ETag after expiry
            .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
            .body(icsContent);
    }

    /**
     * Validate calendar subscription token and verify user is still a group member.
     * Uses CalendarTokenIndex GSI for efficient token lookup.
     *
     * @param token Calendar subscription token
     * @param groupId Group ID from URL path
     * @return GroupMembership if valid
     * @throws UnauthorizedException if token is invalid
     * @throws ForbiddenException if user is no longer a member
     */
    private GroupMembership validateTokenAndMembership(String token, String groupId) {
        // Query CalendarTokenIndex GSI to find membership by token
        GroupMembership membership = groupRepository.findMembershipByToken(token)
            .orElseThrow(() -> new UnauthorizedException("Invalid subscription token"));

        // Verify groupId matches (prevent token reuse across groups)
        if (!membership.getGroupId().equals(groupId)) {
            throw new UnauthorizedException("Token does not match group");
        }

        logger.debug("Validated calendar token for user {} in group {}", membership.getUserId(), groupId);

        return membership;
    }

    /**
     * Query future hangouts for a group using EntityTimeIndex GSI.
     *
     * @param groupId Group ID
     * @return List of future hangout pointers sorted by start time
     */
    private List<HangoutPointer> queryFutureHangouts(String groupId) {
        long nowTimestamp = Instant.now().getEpochSecond();

        // Query EntityTimeIndex for future hangouts
        // Use pagination with large limit to get all future hangouts
        PaginatedResult<BaseItem> result =
            hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, 100, null);

        // Filter for HangoutPointer items and collect
        List<HangoutPointer> hangouts = result.getResults().stream()
            .filter(item -> item instanceof HangoutPointer)
            .map(item -> (HangoutPointer) item)
            .collect(Collectors.toList());

        // If there are more pages, fetch them (should be rare for calendar feeds)
        String nextToken = result.getNextToken();
        while (nextToken != null && hangouts.size() < 500) {  // Cap at 500 events for safety
            result = hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, 100, nextToken);

            hangouts.addAll(result.getResults().stream()
                .filter(item -> item instanceof HangoutPointer)
                .map(item -> (HangoutPointer) item)
                .collect(Collectors.toList()));

            nextToken = result.getNextToken();
        }

        return hangouts;
    }

    /**
     * Convert GroupMembership with token to CalendarSubscriptionResponse.
     *
     * @param membership The group membership with calendar token
     * @return Subscription response with URLs
     */
    private CalendarSubscriptionResponse toResponse(GroupMembership membership) {
        String subscriptionUrl = String.format("%s/calendar/feed/%s/%s",
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
