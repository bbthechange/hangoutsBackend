package com.bbthechange.inviter.dto.watchparty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Detailed response for GET watch party endpoint.
 * Includes series settings and all hangouts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchPartyDetailResponse {

    /**
     * Event series ID.
     */
    private String seriesId;

    /**
     * Series title.
     */
    private String seriesTitle;

    /**
     * Group this watch party belongs to.
     */
    private String groupId;

    /**
     * Type of event series, always "WATCH_PARTY".
     */
    private String eventSeriesType;

    /**
     * TVMaze show ID.
     */
    private Integer showId;

    /**
     * Season number.
     */
    private Integer seasonNumber;

    /**
     * Default time for hangouts (HH:mm).
     */
    private String defaultTime;

    /**
     * IANA timezone.
     */
    private String timezone;

    /**
     * Day override (0-6, null if not set).
     */
    private Integer dayOverride;

    /**
     * Default host user ID (null if not set).
     */
    private String defaultHostId;

    /**
     * All hangouts in this watch party.
     */
    private List<WatchPartyHangoutSummary> hangouts;
}
