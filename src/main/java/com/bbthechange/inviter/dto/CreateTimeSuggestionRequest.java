package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.FuzzyTime;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /groups/{groupId}/hangouts/{hangoutId}/time-suggestions.
 */
public class CreateTimeSuggestionRequest {

    @NotNull(message = "fuzzyTime is required")
    private FuzzyTime fuzzyTime;

    /** Optional exact Unix timestamp in seconds (e.g. 1753200000). */
    private Long specificTime;

    public FuzzyTime getFuzzyTime() {
        return fuzzyTime;
    }

    public void setFuzzyTime(FuzzyTime fuzzyTime) {
        this.fuzzyTime = fuzzyTime;
    }

    public Long getSpecificTime() {
        return specificTime;
    }

    public void setSpecificTime(Long specificTime) {
        this.specificTime = specificTime;
    }
}
