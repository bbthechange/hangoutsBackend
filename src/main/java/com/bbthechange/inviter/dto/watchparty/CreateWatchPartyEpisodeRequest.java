package com.bbthechange.inviter.dto.watchparty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Episode data for Phase 2 watch party creation.
 * In Phase 3, episodes will be fetched from TVMaze directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWatchPartyEpisodeRequest {

    /**
     * TVMaze episode ID.
     */
    private Integer episodeId;

    /**
     * Episode number within the season (1-based).
     * Optional for Phase 2, populated from TVMaze in Phase 3.
     */
    private Integer episodeNumber;

    /**
     * Episode title (may be "TBA").
     */
    private String title;

    /**
     * Unix timestamp of air time (seconds).
     */
    private Long airTimestamp;

    /**
     * Episode runtime in minutes.
     */
    private Integer runtime;
}
