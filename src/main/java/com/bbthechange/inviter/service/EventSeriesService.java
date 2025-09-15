package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.dto.CreateHangoutRequest;

/**
 * Service interface for managing multi-part event series.
 * Handles the business logic for converting standalone events into series
 * and adding new parts to existing series.
 */
public interface EventSeriesService {
    
    /**
     * Converts a standalone hangout into a multi-part series by adding a new member event.
     * This operation is atomic - either all changes succeed or none are applied.
     *
     * @param existingHangoutId The ID of the hangout being converted into a series
     * @param newMemberRequest The details for the new hangout member to create
     * @param userId The ID of the user performing the action
     * @return The newly created EventSeries object
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if hangout doesn't exist
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user lacks permission
     * @throws com.bbthechange.inviter.exception.RepositoryException if transaction fails
     */
    EventSeries convertToSeriesWithNewMember(
        String existingHangoutId, 
        CreateHangoutRequest newMemberRequest, 
        String userId
    );
    
    /**
     * Creates a new hangout as part of an already existing series.
     * This operation is atomic - either all changes succeed or none are applied.
     *
     * @param seriesId The ID of the series to add to
     * @param newMemberRequest The details for the new hangout member to create
     * @param userId The ID of the user performing the action
     * @return The updated EventSeries object
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series doesn't exist
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user lacks permission
     * @throws com.bbthechange.inviter.exception.RepositoryException if transaction fails
     */
    EventSeries createHangoutInExistingSeries(
        String seriesId, 
        CreateHangoutRequest newMemberRequest, 
        String userId
    );
}