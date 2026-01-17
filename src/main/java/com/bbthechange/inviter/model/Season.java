package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Season entity for the InviterTable.
 * Represents a TV season from TVMaze for Watch Party features.
 *
 * Key Pattern: PK = TVMAZE#SHOW#{showId}, SK = SEASON#{seasonNumber}
 * GSI: ExternalIdIndex with externalId = showId, externalSource = "TVMAZE" for querying all seasons of a show
 */
@DynamoDbBean
public class Season extends BaseItem {

    private static final String EXTERNAL_SOURCE = "TVMAZE";

    private Integer showId;             // TVMaze show ID
    private Integer seasonNumber;       // Season number (1-based)
    private String showName;            // Denormalized show name
    private String seasonImageUrl;      // Image URL for the season
    private Integer tvmazeSeasonId;     // TVMaze's internal season ID
    private Long endDate;               // Season end date (epoch seconds)
    private Long lastCheckedTimestamp;  // When episodes were last fetched (epoch millis)
    private List<Episode> episodes;     // List of episodes in this season
    private String externalId;          // Show ID as string for ExternalIdIndex GSI
    private String externalSource;      // "TVMAZE" for ExternalIdIndex GSI

    // Default constructor for DynamoDB
    public Season() {
        super();
        setItemType("SEASON");
        this.episodes = new ArrayList<>();
        this.externalSource = EXTERNAL_SOURCE;
    }

    /**
     * Create a new Season with required fields.
     */
    public Season(Integer showId, Integer seasonNumber, String showName) {
        super();
        setItemType("SEASON");
        this.showId = showId;
        this.seasonNumber = seasonNumber;
        this.showName = showName;
        this.episodes = new ArrayList<>();

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getSeasonPk(showId));
        setSk(InviterKeyFactory.getSeasonSk(seasonNumber));

        // Set GSI keys for ExternalIdIndex (allows querying all seasons for a show)
        this.externalId = showId.toString();
        this.externalSource = EXTERNAL_SOURCE;
    }

    public Integer getShowId() {
        return showId;
    }

    public void setShowId(Integer showId) {
        this.showId = showId;
    }

    public Integer getSeasonNumber() {
        return seasonNumber;
    }

    public void setSeasonNumber(Integer seasonNumber) {
        this.seasonNumber = seasonNumber;
    }

    public String getShowName() {
        return showName;
    }

    public void setShowName(String showName) {
        this.showName = showName;
        touch();
    }

    public String getSeasonImageUrl() {
        return seasonImageUrl;
    }

    public void setSeasonImageUrl(String seasonImageUrl) {
        this.seasonImageUrl = seasonImageUrl;
        touch();
    }

    public Integer getTvmazeSeasonId() {
        return tvmazeSeasonId;
    }

    public void setTvmazeSeasonId(Integer tvmazeSeasonId) {
        this.tvmazeSeasonId = tvmazeSeasonId;
        touch();
    }

    public Long getEndDate() {
        return endDate;
    }

    public void setEndDate(Long endDate) {
        this.endDate = endDate;
        touch();
    }

    public Long getLastCheckedTimestamp() {
        return lastCheckedTimestamp;
    }

    public void setLastCheckedTimestamp(Long lastCheckedTimestamp) {
        this.lastCheckedTimestamp = lastCheckedTimestamp;
        touch();
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(List<Episode> episodes) {
        this.episodes = episodes != null ? episodes : new ArrayList<>();
        touch();
    }

    /**
     * GSI partition key for ExternalIdIndex.
     * Used to query all seasons for a specific TVMaze show.
     */
    @DynamoDbSecondaryPartitionKey(indexNames = "ExternalIdIndex")
    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    /**
     * GSI sort key for ExternalIdIndex.
     * Always "TVMAZE" for Season entities.
     */
    @DynamoDbSecondarySortKey(indexNames = "ExternalIdIndex")
    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
    }

    /**
     * Add an episode to this season.
     */
    public void addEpisode(Episode episode) {
        if (this.episodes == null) {
            this.episodes = new ArrayList<>();
        }
        this.episodes.add(episode);
        touch();
    }

    /**
     * Find an episode by its TVMaze episode ID.
     */
    public Optional<Episode> findEpisodeById(Integer episodeId) {
        if (this.episodes == null || episodeId == null) {
            return Optional.empty();
        }
        return this.episodes.stream()
            .filter(e -> episodeId.equals(e.getEpisodeId()))
            .findFirst();
    }

    /**
     * Find an episode by its episode number.
     */
    public Optional<Episode> findEpisodeByNumber(Integer episodeNumber) {
        if (this.episodes == null || episodeNumber == null) {
            return Optional.empty();
        }
        return this.episodes.stream()
            .filter(e -> episodeNumber.equals(e.getEpisodeNumber()))
            .findFirst();
    }

    /**
     * Get the number of episodes in this season.
     */
    public int getEpisodeCount() {
        return this.episodes != null ? this.episodes.size() : 0;
    }

    /**
     * Check if this season has been checked for updates recently.
     *
     * @param maxAgeMillis Maximum age in milliseconds before considered stale
     * @return true if the season data is still fresh
     */
    public boolean isFresh(long maxAgeMillis) {
        if (lastCheckedTimestamp == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastCheckedTimestamp) < maxAgeMillis;
    }

    /**
     * Generate a reference string for this season.
     * Can be used as a foreign key in EventSeries.
     */
    public String getSeasonReference() {
        return InviterKeyFactory.getSeasonReference(showId, seasonNumber);
    }
}
