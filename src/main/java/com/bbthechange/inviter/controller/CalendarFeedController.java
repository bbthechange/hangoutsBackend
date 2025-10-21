package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.CalendarSubscriptionService;
import com.bbthechange.inviter.service.ICalendarService;
import com.bbthechange.inviter.util.PaginatedResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST controller for calendar subscription and ICS feed endpoints.
 * Handles creation, listing, and deletion of calendar subscriptions,
 * plus serving ICS calendar feeds for calendar apps.
 */
@RestController
@RequestMapping("/v1/calendar")
public class CalendarFeedController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(CalendarFeedController.class);

    private final CalendarSubscriptionService subscriptionService;
    private final ICalendarService iCalendarService;
    private final GroupRepository groupRepository;
    private final HangoutRepository hangoutRepository;

    @Autowired
    public CalendarFeedController(CalendarSubscriptionService subscriptionService,
                                 ICalendarService iCalendarService,
                                 GroupRepository groupRepository,
                                 HangoutRepository hangoutRepository) {
        this.subscriptionService = subscriptionService;
        this.iCalendarService = iCalendarService;
        this.groupRepository = groupRepository;
        this.hangoutRepository = hangoutRepository;
    }

    /**
     * Create a new calendar subscription for a group.
     * Generates unique subscription URLs for calendar apps.
     *
     * POST /v1/calendar/subscriptions/{groupId}
     */
    @PostMapping("/subscriptions/{groupId}")
    public ResponseEntity<CalendarSubscriptionResponse> createSubscription(
            @PathVariable String groupId,
            HttpServletRequest request) {

        String userId = extractUserId(request);
        logger.info("Creating calendar subscription for user {} in group {}", userId, groupId);

        CalendarSubscriptionResponse response = subscriptionService.createSubscription(groupId, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all calendar subscriptions for the authenticated user.
     *
     * GET /v1/calendar/subscriptions
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<CalendarSubscriptionListResponse> listSubscriptions(HttpServletRequest request) {
        String userId = extractUserId(request);
        logger.debug("Listing calendar subscriptions for user {}", userId);

        CalendarSubscriptionListResponse response = subscriptionService.getUserSubscriptions(userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a calendar subscription.
     * Removes the subscription token from the group membership.
     *
     * DELETE /v1/calendar/subscriptions/{groupId}
     */
    @DeleteMapping("/subscriptions/{groupId}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable String groupId,
            HttpServletRequest request) {

        String userId = extractUserId(request);
        logger.info("Deleting calendar subscription for user {} in group {}", userId, groupId);

        subscriptionService.deleteSubscription(groupId, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Serve ICS calendar feed for a group (public endpoint using token auth).
     * This endpoint is accessed by calendar applications like iOS Calendar.
     *
     * GET /v1/calendar/subscribe/{groupId}/{token}
     *
     * Implements HTTP caching with ETags and Cache-Control headers.
     * CloudFront-ready: uses public caching, stable URLs, and proper headers.
     */
    @GetMapping(value = "/subscribe/{groupId}/{token}",
                produces = "text/calendar; charset=utf-8")
    public ResponseEntity<String> getCalendarFeed(
            @PathVariable String groupId,
            @PathVariable String token,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        logger.debug("Calendar feed requested for group {} with token {}", groupId, token.substring(0, 8) + "...");

        // 1. Validate token and check membership (single query via CalendarTokenIndex)
        GroupMembership membership = validateTokenAndMembership(token, groupId);

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
                    .maxAge(2, TimeUnit.HOURS)
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
                .maxAge(2, TimeUnit.HOURS)  // Cache for 2 hours
                .cachePublic()              // Allow CDN caching
                .mustRevalidate())          // Check ETag after expiry
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
        // Note: This requires the CalendarTokenIndex GSI to be created first
        // For now, we'll do a simple membership lookup with token validation
        // TODO: Once GSI is created, replace with GSI query

        // Temporary implementation: get all group members and find by token
        List<GroupMembership> members = groupRepository.findMembersByGroupId(groupId);

        GroupMembership membership = members.stream()
            .filter(m -> token.equals(m.getCalendarToken()))
            .findFirst()
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
        PaginatedResult<com.bbthechange.inviter.model.BaseItem> result =
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
     * Exception handler for ForbiddenException to return 403 instead of default 403.
     * Needed specifically for calendar feed endpoint since it's public (no JWT).
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        logger.warn("Forbidden access attempt: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("FORBIDDEN", e.getMessage()));
    }
}
