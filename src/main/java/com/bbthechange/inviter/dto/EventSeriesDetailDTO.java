package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventSeries;

import java.time.Instant;
import java.util.List;

/**
 * Detailed Data Transfer Object for EventSeries information with full hangout details.
 * Used for the detailed read view that includes all hangout objects in the series.
 */
public class EventSeriesDetailDTO {
    
    private String seriesId;
    private String seriesTitle;
    private String seriesDescription;
    private String primaryEventId;
    private String groupId;
    private Long startTimestamp;
    private Long endTimestamp;
    private List<HangoutDetailDTO> hangouts;  // Full hangout details instead of just IDs
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    // External source fields
    private String externalId;
    private String externalSource;
    private Boolean isGeneratedTitle;

    public EventSeriesDetailDTO() {
    }

    /**
     * Constructor from EventSeries model and list of hangout details.
     */
    public EventSeriesDetailDTO(EventSeries eventSeries, List<HangoutDetailDTO> hangouts) {
        this.seriesId = eventSeries.getSeriesId();
        this.seriesTitle = eventSeries.getSeriesTitle();
        this.seriesDescription = eventSeries.getSeriesDescription();
        this.primaryEventId = eventSeries.getPrimaryEventId();
        this.groupId = eventSeries.getGroupId();
        this.startTimestamp = eventSeries.getStartTimestamp();
        this.endTimestamp = eventSeries.getEndTimestamp();
        this.hangouts = hangouts;
        this.version = eventSeries.getVersion();
        this.createdAt = eventSeries.getCreatedAt();
        this.updatedAt = eventSeries.getUpdatedAt();

        // External source fields
        this.externalId = eventSeries.getExternalId();
        this.externalSource = eventSeries.getExternalSource();
        this.isGeneratedTitle = eventSeries.getIsGeneratedTitle();
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
    
    public List<HangoutDetailDTO> getHangouts() {
        return hangouts;
    }
    
    public void setHangouts(List<HangoutDetailDTO> hangouts) {
        this.hangouts = hangouts;
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

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
    }

    public Boolean getIsGeneratedTitle() {
        return isGeneratedTitle;
    }

    public void setIsGeneratedTitle(Boolean isGeneratedTitle) {
        this.isGeneratedTitle = isGeneratedTitle;
    }
}