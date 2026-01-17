package com.bbthechange.inviter.dto.watchparty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to update TV Watch Party series settings.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWatchPartyRequest {

    /**
     * Default time for hangouts in HH:mm format (e.g., "20:00").
     */
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "defaultTime must be in HH:mm format")
    private String defaultTime;

    /**
     * IANA timezone (e.g., "America/Los_Angeles", "America/New_York").
     */
    private String timezone;

    /**
     * Day of week override (0=Sunday, 6=Saturday).
     * If set, hangouts are scheduled on this day on or after the air date.
     * Set to null to clear.
     */
    @Min(value = 0, message = "dayOverride must be between 0 (Sunday) and 6 (Saturday)")
    @Max(value = 6, message = "dayOverride must be between 0 (Sunday) and 6 (Saturday)")
    private Integer dayOverride;

    /**
     * Default host user ID for all hangouts.
     * Set to empty string to clear.
     */
    private String defaultHostId;

    /**
     * Whether to cascade changes to existing upcoming hangouts.
     * Defaults to true - changes will apply to future hangouts.
     * Set to false to only update series settings without modifying hangouts.
     */
    @Builder.Default
    private Boolean changeExistingUpcomingHangouts = true;
}
