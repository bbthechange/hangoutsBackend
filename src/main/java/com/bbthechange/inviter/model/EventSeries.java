package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * EventSeries entity for the InviterTable.
 * Represents the canonical record for a multi-part event series.
 * Contains information about the overall series that applies to all parts.
 * 
 * Key Pattern: PK = SERIES#{seriesId}, SK = METADATA
 */
@DynamoDbBean
public class EventSeries extends BaseItem {
    
    private String seriesId;
    private String seriesTitle;
    private String seriesDescription;
    private String primaryEventId;      // The main hangout in the series
    private String groupId;             // The group this series belongs to
    private String mainImagePath;       // Image for the series
    private Long startTimestamp;        // Timestamp of the first event in the series (for GSI)
    private Long endTimestamp;          // Timestamp of the last event in the series
    private List<String> hangoutIds;   // List of hangout IDs that are part of this series
    private Long version;               // Optimistic locking

    // External source fields (for integration with Ticketmaster, Yelp, etc.)
    private String externalId;            // ID from external source
    private String externalSource;        // Source system name (e.g., "TICKETMASTER", "YELP")
    private Boolean isGeneratedTitle;     // Whether title was auto-generated (defaults to false)

    // Watch Party fields (for TV Watch Party feature)
    private String eventSeriesType;       // "WATCH_PARTY" discriminator, null for regular series
    private String seasonId;              // Reference: "TVMAZE#SHOW#{showId}|SEASON#{seasonNumber}"
    private String defaultHostId;         // Optional default host user ID
    private String defaultTime;           // Default time in "HH:mm" format
    private Integer dayOverride;          // Day of week override (0=Sunday, 6=Saturday)
    private String timezone;              // IANA timezone (e.g., "America/Los_Angeles")
    private Set<String> deletedEpisodeIds; // Episode IDs that user has deleted from series

    // Default constructor for DynamoDB
    public EventSeries() {
        super();
        setItemType("EVENT_SERIES");
        this.hangoutIds = new ArrayList<>();
        this.version = 1L;
    }

    /**
     * Create a new event series with generated UUID.
     */
    public EventSeries(String seriesTitle, String seriesDescription, String groupId) {
        super();
        setItemType("EVENT_SERIES");
        this.seriesId = UUID.randomUUID().toString();
        this.seriesTitle = seriesTitle;
        this.seriesDescription = seriesDescription;
        this.groupId = groupId;
        this.hangoutIds = new ArrayList<>();
        this.version = 1L;

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getSeriesPk(this.seriesId));
        setSk(InviterKeyFactory.getMetadataSk());

        // Set GSI keys for EntityTimeIndex
        setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
    }
    
    public String getSeriesId() {
        return seriesId;
    }
    
    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }
    
    public String getSeriesTitle() {
        return seriesTitle;
    }
    
    public void setSeriesTitle(String seriesTitle) {
        this.seriesTitle = seriesTitle;
        touch(); // Update timestamp
    }
    
    public String getSeriesDescription() {
        return seriesDescription;
    }
    
    public void setSeriesDescription(String seriesDescription) {
        this.seriesDescription = seriesDescription;
        touch(); // Update timestamp
    }
    
    public String getPrimaryEventId() {
        return primaryEventId;
    }
    
    public void setPrimaryEventId(String primaryEventId) {
        this.primaryEventId = primaryEventId;
        touch(); // Update timestamp
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
        touch(); // Update timestamp
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        touch(); // Update timestamp
    }

    /**
     * GSI sort key for EntityTimeIndex - timestamp of the first event in the series.
     */
    @DynamoDbSecondarySortKey(indexNames = "EntityTimeIndex")
    public Long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        touch(); // Update timestamp
    }
    
    public Long getEndTimestamp() {
        return endTimestamp;
    }
    
    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        touch(); // Update timestamp
    }
    
    public List<String> getHangoutIds() {
        return hangoutIds;
    }
    
    public void setHangoutIds(List<String> hangoutIds) {
        this.hangoutIds = hangoutIds != null ? hangoutIds : new ArrayList<>();
        touch(); // Update timestamp
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    /**
     * GSI partition key for EntityTimeIndex - inherited from BaseItem.
     * This allows series to appear in time-based queries alongside hangouts.
     */
    @Override
    @DynamoDbSecondaryPartitionKey(indexNames = {"UserGroupIndex", "EntityTimeIndex", "EndTimestampIndex"})
    public String getGsi1pk() {
        return super.getGsi1pk();
    }
    
    /**
     * Increment version for optimistic locking.
     */
    public void incrementVersion() {
        this.version = (this.version != null) ? this.version + 1 : 1L;
        touch();
    }
    
    /**
     * Add a hangout to the series.
     */
    public void addHangout(String hangoutId) {
        if (this.hangoutIds == null) {
            this.hangoutIds = new ArrayList<>();
        }
        if (!this.hangoutIds.contains(hangoutId)) {
            this.hangoutIds.add(hangoutId);
            touch();
        }
    }
    
    /**
     * Remove a hangout from the series.
     */
    public void removeHangout(String hangoutId) {
        if (this.hangoutIds != null) {
            this.hangoutIds.remove(hangoutId);
            touch();
        }
    }
    
    /**
     * Check if a hangout is part of this series.
     */
    public boolean containsHangout(String hangoutId) {
        return this.hangoutIds != null && this.hangoutIds.contains(hangoutId);
    }
    
    /**
     * Get the number of hangouts in this series.
     */
    public int getHangoutCount() {
        return this.hangoutIds != null ? this.hangoutIds.size() : 0;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "ExternalIdIndex")
    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch(); // Update timestamp
    }

    @DynamoDbSecondarySortKey(indexNames = "ExternalIdIndex")
    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
        touch(); // Update timestamp
    }

    public Boolean getIsGeneratedTitle() {
        return isGeneratedTitle;
    }

    public void setIsGeneratedTitle(Boolean isGeneratedTitle) {
        this.isGeneratedTitle = isGeneratedTitle;
        touch(); // Update timestamp
    }

    // ============================================================================
    // WATCH PARTY FIELDS
    // ============================================================================

    public String getEventSeriesType() {
        return eventSeriesType;
    }

    public void setEventSeriesType(String eventSeriesType) {
        this.eventSeriesType = eventSeriesType;
        touch();
    }

    public String getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(String seasonId) {
        this.seasonId = seasonId;
        touch();
    }

    public String getDefaultHostId() {
        return defaultHostId;
    }

    public void setDefaultHostId(String defaultHostId) {
        this.defaultHostId = defaultHostId;
        touch();
    }

    public String getDefaultTime() {
        return defaultTime;
    }

    public void setDefaultTime(String defaultTime) {
        this.defaultTime = defaultTime;
        touch();
    }

    public Integer getDayOverride() {
        return dayOverride;
    }

    public void setDayOverride(Integer dayOverride) {
        this.dayOverride = dayOverride;
        touch();
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
        touch();
    }

    public Set<String> getDeletedEpisodeIds() {
        return deletedEpisodeIds;
    }

    public void setDeletedEpisodeIds(Set<String> deletedEpisodeIds) {
        if (deletedEpisodeIds == null || deletedEpisodeIds.isEmpty()) {
            this.deletedEpisodeIds = null;
        } else {
            this.deletedEpisodeIds = deletedEpisodeIds;
        }
        touch();
    }

    // ============================================================================
    // WATCH PARTY HELPER METHODS
    // ============================================================================

    /**
     * Check if this series is a Watch Party series.
     */
    public boolean isWatchParty() {
        return "WATCH_PARTY".equals(eventSeriesType);
    }

    /**
     * Add an episode ID to the deleted set.
     *
     * @param episodeId The episode ID to mark as deleted
     */
    public void addDeletedEpisodeId(String episodeId) {
        if (this.deletedEpisodeIds == null) {
            this.deletedEpisodeIds = new HashSet<>();
        }
        this.deletedEpisodeIds.add(episodeId);
        touch();
    }

    /**
     * Check if an episode has been deleted from this series.
     *
     * @param episodeId The episode ID to check
     * @return true if the episode has been deleted
     */
    public boolean isEpisodeDeleted(String episodeId) {
        return this.deletedEpisodeIds != null && this.deletedEpisodeIds.contains(episodeId);
    }

    /**
     * Remove an episode ID from the deleted set (restore it).
     *
     * @param episodeId The episode ID to restore
     */
    public void removeDeletedEpisodeId(String episodeId) {
        if (this.deletedEpisodeIds != null && this.deletedEpisodeIds.remove(episodeId)) {
            if (this.deletedEpisodeIds.isEmpty()) {
                this.deletedEpisodeIds = null;
            }
            touch();
        }
    }
}