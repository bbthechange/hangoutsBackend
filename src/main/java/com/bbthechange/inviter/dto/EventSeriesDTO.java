package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventSeries;

import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for EventSeries information.
 * Represents a multi-part event series for API responses.
 */
public class EventSeriesDTO {
    
    private String seriesId;
    private String seriesTitle;
    private String seriesDescription;
    private String primaryEventId;
    private String groupId;
    private Long startTimestamp;
    private Long endTimestamp;
    private List<String> hangoutIds;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    
    public EventSeriesDTO() {
    }
    
    /**
     * Constructor from EventSeries model.
     */
    public EventSeriesDTO(EventSeries eventSeries) {
        this.seriesId = eventSeries.getSeriesId();
        this.seriesTitle = eventSeries.getSeriesTitle();
        this.seriesDescription = eventSeries.getSeriesDescription();
        this.primaryEventId = eventSeries.getPrimaryEventId();
        this.groupId = eventSeries.getGroupId();
        this.startTimestamp = eventSeries.getStartTimestamp();
        this.endTimestamp = eventSeries.getEndTimestamp();
        this.hangoutIds = eventSeries.getHangoutIds();
        this.version = eventSeries.getVersion();
        this.createdAt = eventSeries.getCreatedAt();
        this.updatedAt = eventSeries.getUpdatedAt();
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
    }
    
    public String getSeriesDescription() {
        return seriesDescription;
    }
    
    public void setSeriesDescription(String seriesDescription) {
        this.seriesDescription = seriesDescription;
    }
    
    public String getPrimaryEventId() {
        return primaryEventId;
    }
    
    public void setPrimaryEventId(String primaryEventId) {
        this.primaryEventId = primaryEventId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public Long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }
    
    public Long getEndTimestamp() {
        return endTimestamp;
    }
    
    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }
    
    public List<String> getHangoutIds() {
        return hangoutIds;
    }
    
    public void setHangoutIds(List<String> hangoutIds) {
        this.hangoutIds = hangoutIds;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}