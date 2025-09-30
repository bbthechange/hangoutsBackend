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
    
    // Default constructor for DynamoDB
    public SeriesPointer() {
        super();
        setItemType("SERIES_POINTER");
        this.hangoutIds = new ArrayList<>();
        this.parts = new ArrayList<>();
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
}