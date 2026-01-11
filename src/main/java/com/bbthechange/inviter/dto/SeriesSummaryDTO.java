package com.bbthechange.inviter.dto;

import java.util.List;

/**
 * DTO representing a multi-part event series in the group feed.
 * Contains summary information about the series and all its individual parts.
 */
public class SeriesSummaryDTO implements FeedItem {
    
    private String seriesId;
    private String seriesTitle;
    private String seriesDescription;
    private String primaryEventId;
    private Long startTimestamp; // Earliest event in the series
    private Long endTimestamp;   // Latest event in the series
    private String mainImagePath; // Denormalized image path from EventSeries/SeriesPointer
    private List<HangoutSummaryDTO> parts; // Individual events in the series
    private int totalParts;
    private String type = "series"; // Type discriminator for client-side handling

    // External source fields (denormalized from SeriesPointer)
    private String externalId;
    private String externalSource;
    private Boolean isGeneratedTitle;

    public SeriesSummaryDTO() {
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

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }

    public List<HangoutSummaryDTO> getParts() {
        return parts;
    }
    
    public void setParts(List<HangoutSummaryDTO> parts) {
        this.parts = parts;
        this.totalParts = (parts != null) ? parts.size() : 0;
    }
    
    public int getTotalParts() {
        return totalParts;
    }
    
    public void setTotalParts(int totalParts) {
        this.totalParts = totalParts;
    }
    
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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