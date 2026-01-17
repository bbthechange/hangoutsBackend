package com.bbthechange.inviter.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Episode embedded class for Season.episodes list.
 * Represents a single episode within a TV season for Watch Party features.
 *
 * This is a DynamoDB embedded bean stored as a list within Season entities.
 */
@DynamoDbBean
public class Episode {

    private Integer episodeId;      // TVMaze episode ID
    private Integer episodeNumber;  // Episode number within the season (1-based)
    private String title;           // Episode title
    private Long airTimestamp;      // Air date/time in epoch seconds
    private String imageUrl;        // Episode image URL
    private Integer runtime;        // Runtime in minutes
    private String type;            // "regular" or "significant_special"

    // Default constructor for DynamoDB
    public Episode() {
    }

    /**
     * Create a new Episode with required fields.
     */
    public Episode(Integer episodeId, Integer episodeNumber, String title) {
        this.episodeId = episodeId;
        this.episodeNumber = episodeNumber;
        this.title = title;
        this.type = "regular"; // Default type
    }

    public Integer getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(Integer episodeId) {
        this.episodeId = episodeId;
    }

    public Integer getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getAirTimestamp() {
        return airTimestamp;
    }

    public void setAirTimestamp(Long airTimestamp) {
        this.airTimestamp = airTimestamp;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Check if this is a regular episode (not a special).
     */
    public boolean isRegular() {
        return "regular".equals(type);
    }

    /**
     * Check if this is a significant special episode.
     */
    public boolean isSignificantSpecial() {
        return "significant_special".equals(type);
    }
}
