package com.bbthechange.inviter.dto.tvmaze;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for TVMaze episode API response.
 * Maps the JSON response from GET /seasons/{seasonId}/episodes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TvMazeEpisodeResponse {

    /**
     * TVMaze episode ID.
     */
    private Integer id;

    /**
     * Episode name/title.
     */
    private String name;

    /**
     * Season number.
     */
    private Integer season;

    /**
     * Episode number within the season.
     */
    private Integer number;

    /**
     * Episode type: "regular", "significant_special", or other special types.
     */
    private String type;

    /**
     * Air date in YYYY-MM-DD format.
     */
    private String airdate;

    /**
     * Air time in HH:mm format.
     */
    private String airtime;

    /**
     * ISO 8601 timestamp of air date/time (e.g., "2011-04-17T21:00:00-04:00").
     */
    private String airstamp;

    /**
     * Runtime in minutes.
     */
    private Integer runtime;

    /**
     * Episode summary (HTML).
     */
    private String summary;

    /**
     * Episode image URLs.
     */
    private TvMazeImage image;

    /**
     * Nested image object from TVMaze API.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TvMazeImage {
        private String medium;
        private String original;
    }

    /**
     * Check if this episode should be included in watch party.
     * Only regular episodes and significant specials are included.
     */
    public boolean isIncludable() {
        return "regular".equals(type) || "significant_special".equals(type);
    }
}
