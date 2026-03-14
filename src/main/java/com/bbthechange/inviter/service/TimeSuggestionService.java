package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CreateTimeSuggestionRequest;
import com.bbthechange.inviter.dto.TimeSuggestionDTO;

import java.util.List;

/**
 * Service for managing time suggestions on hangouts that have no time set yet.
 *
 * Implements the "silence = consent" auto-adoption logic described in spec section 8:
 *   - A suggestion with support and no competitors → auto-adopted after the adoption window.
 *   - A suggestion with no votes at all → also auto-adopted after a longer window.
 *   - Competing suggestions → remain as a poll; neither is auto-adopted.
 *
 * When a suggestion is adopted, the hangout's time is updated and momentum is recomputed.
 */
public interface TimeSuggestionService {

    /**
     * Create a new time suggestion for a hangout.
     *
     * Validates:
     *   - The hangout exists.
     *   - The hangout has no time set yet.
     *   - The requesting user is a member of the group.
     *
     * @param groupId    Group context (used for authorization)
     * @param hangoutId  Target hangout
     * @param request    Fuzzy time and optional specific time
     * @param userId     ID of the requesting user
     * @return The created suggestion as a DTO
     */
    TimeSuggestionDTO createSuggestion(String groupId, String hangoutId,
                                       CreateTimeSuggestionRequest request, String userId);

    /**
     * Add a +1 (support) from the requesting user to an existing suggestion.
     *
     * Validates:
     *   - Suggestion exists and is ACTIVE.
     *   - User is a group member.
     *   - User has not already supported this suggestion.
     *
     * @param groupId      Group context
     * @param hangoutId    Hangout the suggestion belongs to
     * @param suggestionId The suggestion to support
     * @param userId       ID of the requesting user
     * @return Updated suggestion as a DTO
     */
    TimeSuggestionDTO supportSuggestion(String groupId, String hangoutId,
                                        String suggestionId, String userId);

    /**
     * List all ACTIVE time suggestions for a hangout.
     *
     * Validates that the requesting user is a group member.
     *
     * @param groupId   Group context
     * @param hangoutId Target hangout
     * @param userId    ID of the requesting user
     * @return List of active suggestion DTOs
     */
    List<TimeSuggestionDTO> listSuggestions(String groupId, String hangoutId, String userId);

    /**
     * Evaluate and potentially adopt a time suggestion for a single hangout.
     * Called by the EventBridge-triggered SQS listener after the adoption window elapses.
     *
     * Rules:
     *   1. Single suggestion with ≥1 supporter, no competitors → ADOPTED after shortWindow.
     *   2. Single suggestion with 0 votes, no competitors     → ADOPTED after longWindow.
     *   3. Multiple competing suggestions                      → leave as poll; skip.
     *
     * @param hangoutId        The hangout to evaluate
     * @param shortWindowHours Adoption window (hours) for suggestions with support
     * @param longWindowHours  Adoption window (hours) for zero-vote suggestions
     */
    void adoptForHangout(String hangoutId, int shortWindowHours, int longWindowHours);
}
