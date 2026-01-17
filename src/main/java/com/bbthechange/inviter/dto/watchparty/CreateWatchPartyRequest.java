package com.bbthechange.inviter.dto.watchparty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request to create a TV Watch Party series.
 *
 * Phase 2: Episodes are provided directly in the request.
 * Phase 3: Episodes will be fetched from TVMaze using tvmazeSeasonId.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWatchPartyRequest {

    /**
     * TVMaze show ID.
     */
    @NotNull(message = "showId is required")
    private Integer showId;

    /**
     * Season number (1, 2, 3, etc.).
     */
    @NotNull(message = "seasonNumber is required")
    private Integer seasonNumber;

    /**
     * Show name for display.
     */
    @NotBlank(message = "showName is required")
    private String showName;

    /**
     * Default time for all hangouts in HH:mm format (e.g., "20:00").
     */
    @NotBlank(message = "defaultTime is required")
    @Pattern(regexp = "^([01]?[0-9]|2[0-3]):[0-5][0-9]$", message = "defaultTime must be in HH:mm format")
    private String defaultTime;

    /**
     * IANA timezone (e.g., "America/Los_Angeles", "America/New_York").
     * Used for DST-aware scheduling.
     */
    @NotBlank(message = "timezone is required")
    private String timezone;

    /**
     * Optional day of week override (0=Sunday, 6=Saturday).
     * If set, hangouts are scheduled on this day on or after the air date.
     */
    private Integer dayOverride;

    /**
     * Optional default host user ID for all hangouts.
     */
    private String defaultHostId;

    /**
     * Episode list for Phase 2 (temporary - will be removed in Phase 3).
     * In Phase 3, episodes are fetched from TVMaze.
     */
    @NotEmpty(message = "episodes list is required for Phase 2")
    private List<CreateWatchPartyEpisodeRequest> episodes;
}
