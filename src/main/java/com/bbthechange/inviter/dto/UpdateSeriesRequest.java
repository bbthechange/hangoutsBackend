package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request DTO for updating an existing event series.
 * Contains the fields that can be modified on a series and the current version for optimistic locking.
 */
@Data
public class UpdateSeriesRequest {
    
    /**
     * The current version of the series for optimistic locking.
     * Must match the current version in the database for the update to succeed.
     */
    @NotNull(message = "Version is required for optimistic locking")
    private Long version;
    
    /**
     * Updated title for the series.
     * If null, the title will not be changed.
     */
    private String seriesTitle;
    
    /**
     * Updated description for the series.
     * If null, the description will not be changed.
     */
    private String seriesDescription;
    
    /**
     * Updated primary event ID for the series.
     * Must be a hangout that is currently part of this series.
     * If null, the primary event ID will not be changed.
     */
    private String primaryEventId;
    
    public UpdateSeriesRequest() {
    }
    
    public UpdateSeriesRequest(Long version, String seriesTitle, String seriesDescription, String primaryEventId) {
        this.version = version;
        this.seriesTitle = seriesTitle;
        this.seriesDescription = seriesDescription;
        this.primaryEventId = primaryEventId;
    }
    
    /**
     * Check if this request has any fields to update.
     * @return true if at least one field is non-null
     */
    public boolean hasUpdates() {
        return seriesTitle != null || seriesDescription != null || primaryEventId != null;
    }
}