package com.bbthechange.inviter.dto.tvmaze;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for TVMaze season API response.
 * Maps the JSON response from GET /shows/{showId}/seasons
 * Reserved for future use.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TvMazeSeasonResponse {

    /**
     * TVMaze season ID (used to fetch episodes).
     */
    private Integer id;

    /**
     * Season number (1, 2, 3, etc.).
     */
    private Integer number;

    /**
     * Season name (often empty for regular seasons).
     */
    private String name;

    /**
     * Number of episodes in the season.
     */
    private Integer episodeOrder;

    /**
     * Season premiere date in YYYY-MM-DD format.
     */
    private String premiereDate;

    /**
     * Season finale date in YYYY-MM-DD format.
     */
    private String endDate;

    /**
     * Network information.
     */
    private TvMazeNetwork network;

    /**
     * Season image.
     */
    private TvMazeEpisodeResponse.TvMazeImage image;

    /**
     * Nested network object from TVMaze API.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TvMazeNetwork {
        private Integer id;
        private String name;
        private TvMazeCountry country;
    }

    /**
     * Nested country object from TVMaze API.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TvMazeCountry {
        private String name;
        private String code;
        private String timezone;
    }
}
