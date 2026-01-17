package com.bbthechange.inviter.dto.watchparty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for setting series-level interest.
 * Used by POST /watch-parties/{seriesId}/interest endpoint.
 *
 * Note: Uses "level" field (not "status") to distinguish from hangout-level interest.
 */
public class SetSeriesInterestRequest {

    @NotBlank(message = "Level is required")
    @Pattern(regexp = "GOING|INTERESTED|NOT_GOING", message = "Level must be GOING, INTERESTED, or NOT_GOING")
    private String level;

    public SetSeriesInterestRequest() {
    }

    public SetSeriesInterestRequest(String level) {
        this.level = level;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }
}
