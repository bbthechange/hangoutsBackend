package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.util.ArrayList;
import java.util.List;

/**
 * SeriesPointer entity for the InviterTable.
 * Represents a denormalized record that contains a copy of EventSeries data 
 * for each group the series is associated with.
 * 
 * This enables efficient single-query retrieval of group feeds by having
 * one pointer record per group, containing all necessary series information.
 * 
 * Key Pattern: PK = GROUP#{groupId}, SK = SERIES#{seriesId}
 * GSI: EntityTimeIndex for time-based sorting in group feeds
 */
@DynamoDbBean
public class SeriesPointer extends BaseItem {
    
    // Denormalized EventSeries data
    private String seriesId;
    private String seriesTitle;
    private String seriesDescription;
    private String primaryEventId;      // The main hangout in the series
    private String groupId;             // The specific group this pointer belongs to
    private String mainImagePath;       // Denormalized image path from EventSeries
    private Long startTimestamp;        // Timestamp of the first event in the series (for GSI)
    private Long endTimestamp;          // Timestamp of the last event in the series
    private List<String> hangoutIds;   // List of hangout IDs that are part of this series
    private List<HangoutPointer> parts; // Denormalized list of HangoutPointer objects for each part
    private Long version;               // Copy of series version for consistency

    // External source fields (denormalized from EventSeries)
    private String externalId;          // ID from external source (Ticketmaster, Yelp, etc.)
    private String externalSource;      // Source system name
    private Boolean isGeneratedTitle;   // Whether title was auto-generated

    // Watch Party fields (denormalized from EventSeries)
    private String eventSeriesType;     // "WATCH_PARTY" discriminator, null for regular series
    private String seasonId;            // Reference: "TVMAZE#SHOW#{showId}|SEASON#{seasonNumber}"
    private String defaultHostId;       // Optional default host user ID
    private String defaultTime;         // Default time in "HH:mm" format
    private Integer dayOverride;        // Day of week override (0=Sunday, 6=Saturday)
    private String timezone;            // IANA timezone (e.g., "America/Los_Angeles")
    private List<InterestLevel> interestLevels; // Series-level interest/attendance

    // Default constructor for DynamoDB
    public SeriesPointer() {
        super();
        setItemType("SERIES_POINTER");
        this.hangoutIds = new ArrayList<>();
        this.parts = new ArrayList<>();
        this.interestLevels = new ArrayList<>();
        this.version = 1L;
    }
    
    /**
     * Create a SeriesPointer from an EventSeries for a specific group.
     */
    public SeriesPointer(String groupId, String seriesId, String seriesTitle) {
        super();
        setItemType("SERIES_POINTER");
        this.groupId = groupId;
        this.seriesId = seriesId;
        this.seriesTitle = seriesTitle;
        this.hangoutIds = new ArrayList<>();
        this.parts = new ArrayList<>();
        this.interestLevels = new ArrayList<>();
        this.version = 1L;

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(groupId));
        setSk(InviterKeyFactory.getSeriesSk(seriesId));

        // Set GSI keys for EntityTimeIndex (same pattern as HangoutPointer)
        setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
    }
    
    /**
     * Copy constructor to create a SeriesPointer from an EventSeries.
     * All EventSeries data is copied to this denormalized record.
     */
    public static SeriesPointer fromEventSeries(EventSeries series, String groupId) {
        SeriesPointer pointer = new SeriesPointer(groupId, series.getSeriesId(), series.getSeriesTitle());

        // Copy all EventSeries fields
        pointer.setSeriesDescription(series.getSeriesDescription());
        pointer.setPrimaryEventId(series.getPrimaryEventId());
        pointer.setMainImagePath(series.getMainImagePath());
        pointer.setStartTimestamp(series.getStartTimestamp());
        pointer.setEndTimestamp(series.getEndTimestamp());
        pointer.setHangoutIds(series.getHangoutIds() != null ? new ArrayList<>(series.getHangoutIds()) : new ArrayList<>());
        pointer.setVersion(series.getVersion());

        // Copy external source fields
        pointer.setExternalId(series.getExternalId());
        pointer.setExternalSource(series.getExternalSource());
        pointer.setIsGeneratedTitle(series.getIsGeneratedTitle());

        // Copy watch party fields
        pointer.setEventSeriesType(series.getEventSeriesType());
        pointer.setSeasonId(series.getSeasonId());
        pointer.setDefaultHostId(series.getDefaultHostId());
        pointer.setDefaultTime(series.getDefaultTime());
        pointer.setDayOverride(series.getDayOverride());
        pointer.setTimezone(series.getTimezone());

        return pointer;
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
     * This allows series to be sorted by time alongside hangouts in group feeds.
     */
    @DynamoDbSecondarySortKey(indexNames = "EntityTimeIndex")
    public Long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        touch(); // Update timestamp
    }
    
    @DynamoDbSecondarySortKey(indexNames = "EndTimestampIndex")
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
     * This allows series pointers to appear in time-based queries alongside hangout pointers.
     */
    @Override
    @DynamoDbSecondaryPartitionKey(indexNames = {"UserGroupIndex", "EntityTimeIndex", "EndTimestampIndex"})
    public String getGsi1pk() {
        return super.getGsi1pk();
    }
    
    /**
     * Get the number of hangouts in this series.
     */
    public int getHangoutCount() {
        return this.hangoutIds != null ? this.hangoutIds.size() : 0;
    }
    
    /**
     * Check if a hangout is part of this series.
     */
    public boolean containsHangout(String hangoutId) {
        return this.hangoutIds != null && this.hangoutIds.contains(hangoutId);
    }
    
    /**
     * Sync this pointer with updated EventSeries data.
     * This is used when the series is modified and all pointers need updating.
     */
    public void syncWithEventSeries(EventSeries series) {
        setSeriesTitle(series.getSeriesTitle());
        setSeriesDescription(series.getSeriesDescription());
        setPrimaryEventId(series.getPrimaryEventId());
        setMainImagePath(series.getMainImagePath());
        setStartTimestamp(series.getStartTimestamp());
        setEndTimestamp(series.getEndTimestamp());
        setHangoutIds(series.getHangoutIds() != null ? new ArrayList<>(series.getHangoutIds()) : new ArrayList<>());
        setVersion(series.getVersion());

        // Sync external source fields
        setExternalId(series.getExternalId());
        setExternalSource(series.getExternalSource());
        setIsGeneratedTitle(series.getIsGeneratedTitle());

        // Sync watch party fields
        setEventSeriesType(series.getEventSeriesType());
        setSeasonId(series.getSeasonId());
        setDefaultHostId(series.getDefaultHostId());
        setDefaultTime(series.getDefaultTime());
        setDayOverride(series.getDayOverride());
        setTimezone(series.getTimezone());
    }
    
    /**
     * Get the denormalized list of HangoutPointer objects for all parts in this series.
     * These are sorted chronologically if they have timestamps.
     */
    public List<HangoutPointer> getParts() {
        if (parts != null && !parts.isEmpty() && parts.get(0).getStartTimestamp() != null) {
            // Ensure parts are returned in chronological order
            parts.sort((a, b) -> {
                if (a.getStartTimestamp() == null || b.getStartTimestamp() == null) {
                    return 0;
                }
                return a.getStartTimestamp().compareTo(b.getStartTimestamp());
            });
        }
        return parts;
    }
    
    /**
     * Set the denormalized list of HangoutPointer objects for all parts in this series.
     */
    public void setParts(List<HangoutPointer> parts) {
        this.parts = parts != null ? parts : new ArrayList<>();
        touch(); // Update timestamp
    }
    
    /**
     * Add a HangoutPointer to the parts list.
     */
    public void addPart(HangoutPointer part) {
        if (this.parts == null) {
            this.parts = new ArrayList<>();
        }
        this.parts.add(part);
        touch(); // Update timestamp
    }
    
    /**
     * Get the number of parts in this series (from the denormalized list).
     */
    public int getPartsCount() {
        return this.parts != null ? this.parts.size() : 0;
    }

    // ============================================================================
    // EXTERNAL SOURCE FIELDS (Denormalized from canonical EventSeries)
    // ============================================================================

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch();
    }

    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
        touch();
    }

    public Boolean getIsGeneratedTitle() {
        return isGeneratedTitle;
    }

    public void setIsGeneratedTitle(Boolean isGeneratedTitle) {
        this.isGeneratedTitle = isGeneratedTitle;
        touch();
    }

    // ============================================================================
    // WATCH PARTY FIELDS (Denormalized from canonical EventSeries)
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

    public List<InterestLevel> getInterestLevels() {
        return interestLevels;
    }

    public void setInterestLevels(List<InterestLevel> interestLevels) {
        this.interestLevels = interestLevels != null ? interestLevels : new ArrayList<>();
        touch();
    }

    /**
     * Check if this series pointer is for a Watch Party series.
     */
    public boolean isWatchParty() {
        return "WATCH_PARTY".equals(eventSeriesType);
    }

    /**
     * Add an interest level to the series.
     * Prevents duplicates based on userId.
     */
    public void addInterestLevel(InterestLevel interestLevel) {
        if (this.interestLevels == null) {
            this.interestLevels = new ArrayList<>();
        }
        // Check for existing interest level from same user
        boolean exists = this.interestLevels.stream()
            .anyMatch(il -> il.getUserId() != null && il.getUserId().equals(interestLevel.getUserId()));
        if (!exists) {
            this.interestLevels.add(interestLevel);
            touch();
        }
    }

    /**
     * Set or update an interest level for a user.
     * If the user already has an interest level, it is replaced.
     * If not, the new interest level is added.
     *
     * @param interestLevel The interest level to set or update
     */
    public void setOrUpdateInterestLevel(InterestLevel interestLevel) {
        if (this.interestLevels == null) {
            this.interestLevels = new ArrayList<>();
        }
        // Remove existing entry from same user if present
        this.interestLevels.removeIf(il ->
            il.getUserId() != null && il.getUserId().equals(interestLevel.getUserId()));
        // Add the new entry
        this.interestLevels.add(interestLevel);
        touch();
    }

    /**
     * Get the count of interest levels.
     */
    public int getInterestLevelsCount() {
        return this.interestLevels != null ? this.interestLevels.size() : 0;
    }
}