package com.bbthechange.inviter.dto.watchparty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /watch-parties/{seriesId}/notification-preferences}.
 *
 * <p>{@code nudgeType} is validated against {@code NudgeTypes} constants in the
 * service layer (unknown values become 400). {@code muted=true} sets the mute,
 * {@code muted=false} clears it.</p>
 */
public class SetSeriesNotificationPreferenceRequest {

    @NotBlank(message = "nudgeType is required")
    private String nudgeType;

    @NotNull(message = "muted is required")
    private Boolean muted;

    public SetSeriesNotificationPreferenceRequest() {
    }

    public SetSeriesNotificationPreferenceRequest(String nudgeType, Boolean muted) {
        this.nudgeType = nudgeType;
        this.muted = muted;
    }

    public String getNudgeType() {
        return nudgeType;
    }

    public void setNudgeType(String nudgeType) {
        this.nudgeType = nudgeType;
    }

    public Boolean getMuted() {
        return muted;
    }

    public void setMuted(Boolean muted) {
        this.muted = muted;
    }
}
