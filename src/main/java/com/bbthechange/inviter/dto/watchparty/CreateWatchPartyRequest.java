package com.bbthechange.inviter.dto.watchparty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Phase 3: Episodes can be fetched from TVMaze using tvmazeSeasonId.
 *
 * Either tvmazeSeasonId OR episodes must be provided.
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
    @Min(value = 0, message = "dayOverride must be between 0 (Sunday) and 6 (Saturday)")
    @Max(value = 6, message = "dayOverride must be between 0 (Sunday) and 6 (Saturday)")
    private Integer dayOverride;

    /**
     * Optional default host user ID for all hangouts.
     */
    private String defaultHostId;

    /**
     * Optional TVMaze show image URL.
     * Must start with "https://static.tvmaze.com/" if provided.
     */
    private String showImageUrl;

    /**
     * TVMaze season ID for Phase 3.
     * When provided, episodes are fetched from TVMaze API.
     * If not provided, episodes must be supplied directly.
     */
    private Integer tvmazeSeasonId;

    /**
     * Episode list for Phase 2 (optional when tvmazeSeasonId is provided).
     * In Phase 3, episodes can be fetched from TVMaze using tvmazeSeasonId.
     */
    private List<CreateWatchPartyEpisodeRequest> episodes;

    /**
     * Check if episodes should be fetched from TVMaze.
     * Returns true if tvmazeSeasonId is provided and episodes list is null/empty.
     */
    public boolean shouldFetchFromTvMaze() {
        return tvmazeSeasonId != null && (episodes == null || episodes.isEmpty());
    }
}
