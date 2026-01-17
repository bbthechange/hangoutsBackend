package com.bbthechange.inviter.dto.watchparty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Summary of a hangout created as part of a watch party.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchPartyHangoutSummary {

    /**
     * Hangout ID.
     */
    private String hangoutId;

    /**
     * Hangout title (may be "Double Episode: X, Y" for combined episodes).
     */
    private String title;

    /**
     * Start timestamp (Unix epoch seconds).
     */
    private Long startTimestamp;

    /**
     * End timestamp (Unix epoch seconds).
     * Calculated from runtime rounded up to nearest 30 minutes.
     */
    private Long endTimestamp;

    /**
     * Primary TVMaze episode ID.
     * For combined episodes, this is the first episode's ID.
     */
    private String externalId;

    /**
     * All TVMaze episode IDs for combined episodes.
     * For single episodes, this list has one element.
     */
    private List<String> combinedExternalIds;
}
