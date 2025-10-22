package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.service.CalendarSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for calendar subscription and ICS feed endpoints.
 * Handles creation, listing, and deletion of calendar subscriptions,
 * plus serving ICS calendar feeds for calendar apps.
 */
@RestController
@RequestMapping("/calendar")
public class CalendarFeedController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(CalendarFeedController.class);

    private final CalendarSubscriptionService subscriptionService;

    @Autowired
    public CalendarFeedController(CalendarSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Create a new calendar subscription for a group.
     * Generates unique subscription URLs for calendar apps.
     *
     * POST /calendar/subscriptions/{groupId}
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
     * GET /calendar/subscriptions
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
     * DELETE /calendar/subscriptions/{groupId}
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
     * GET /calendar/feed/{groupId}/{token}
     *
     * Implements HTTP caching with ETags and Cache-Control headers.
     * CloudFront-ready: uses public caching, stable URLs, and proper headers.
     */
    @GetMapping(value = "/feed/{groupId}/{token}",
                produces = "text/calendar; charset=utf-8")
    public ResponseEntity<String> getCalendarFeed(
            @PathVariable String groupId,
            @PathVariable String token,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        logger.debug("Calendar feed requested for group {} with token {}", groupId, token.substring(0, 8) + "...");

        return subscriptionService.getCalendarFeed(groupId, token, ifNoneMatch);
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
