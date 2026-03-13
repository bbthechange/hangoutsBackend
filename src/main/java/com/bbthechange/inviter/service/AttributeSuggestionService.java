package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.PollWithOptionsDTO;
import com.bbthechange.inviter.dto.SuggestedAttributeDTO;
import com.bbthechange.inviter.model.Hangout;

import java.util.List;
import java.util.Map;

/**
 * Service for managing attribute suggestions via the polls system.
 *
 * <p>Replaces the old AttributeProposal "silence=consent" system with polls
 * tagged by {@code attributeType}. Suggestion polls are regular polls that
 * can be auto-promoted to hangout attributes after 24h if unopposed.
 */
public interface AttributeSuggestionService {

    /**
     * Pure computation: derive suggested attributes from already-fetched poll data.
     * No DB queries — designed to be called from getHangoutDetail() or hydrateFeed().
     *
     * @param hangout The hangout (unused for now but available for future logic)
     * @param polls   Already-fetched polls with options and vote counts
     * @return Map keyed by attributeType ("LOCATION", "DESCRIPTION") with suggestion state
     */
    Map<String, SuggestedAttributeDTO> computeSuggestedAttributes(Hangout hangout, List<PollWithOptionsDTO> polls);

    /**
     * Deactivate active suggestion polls for the given attribute type when
     * a direct edit supersedes them.
     *
     * @param hangoutId     The hangout ID
     * @param attributeType "LOCATION" or "DESCRIPTION"
     */
    void supersedeSuggestionPolls(String hangoutId, String attributeType);

    /**
     * Find and auto-promote eligible suggestion polls that have passed the 24h window
     * with a single unopposed leader. Called by the hourly scheduled task.
     */
    void promoteEligibleSuggestions();
}
