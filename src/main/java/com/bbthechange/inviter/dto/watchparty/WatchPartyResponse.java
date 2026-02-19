package com.bbthechange.inviter.dto.watchparty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response after creating a watch party series.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchPartyResponse {

    /**
     * Event series ID for this watch party.
     */
    private String seriesId;

    /**
     * Series title (e.g., "RuPaul's Drag Race Season 18").
     */
    private String seriesTitle;

    /**
     * Main image path for the watch party (TVMaze show image URL).
     */
    private String mainImagePath;

    /**
     * List of hangouts created for episodes.
     */
    private List<WatchPartyHangoutSummary> hangouts;
}
