package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /groups/{groupId}/hangouts/{hangoutId}/time-suggestions.
 * Uses the same TimeInfo shape as hangout create/update endpoints.
 * Shape validation (fuzzy XOR exact, ISO-8601 parse, granularity allowed list)
 * is performed in the service via FuzzyTimeService.convert().
 */
public class CreateTimeSuggestionRequest {

    @NotNull(message = "timeInput is required")
    private TimeInfo timeInput;

    public TimeInfo getTimeInput() {
        return timeInput;
    }

    public void setTimeInput(TimeInfo timeInput) {
        this.timeInput = timeInput;
    }
}
