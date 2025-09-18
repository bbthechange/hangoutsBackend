package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.EventSeriesDetailDTO;
import com.bbthechange.inviter.dto.UpdateSeriesRequest;

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
    
    /**
     * Unlinks a hangout from its series without deleting the hangout.
     * This operation is atomic - either all changes succeed or none are applied.
     * If this is the last hangout in the series, the entire series is deleted.
     *
     * @param seriesId The ID of the series to remove from
     * @param hangoutId The ID of the hangout to unlink
     * @param userId The ID of the user performing the action
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series or hangout doesn't exist
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user lacks permission
     * @throws com.bbthechange.inviter.exception.RepositoryException if transaction fails
     */
    void unlinkHangoutFromSeries(String seriesId, String hangoutId, String userId);
    
    /**
     * Updates a series when one of its hangouts is modified.
     * This propagates changes to SeriesPointer records to maintain data consistency.
     * This operation is atomic - either all changes succeed or none are applied.
     *
     * @param hangoutId The ID of the hangout that was modified
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if hangout doesn't exist
     * @throws com.bbthechange.inviter.exception.RepositoryException if transaction fails
     */
    void updateSeriesAfterHangoutModification(String hangoutId);
    
    /**
     * Removes a hangout from its series and deletes all related records.
     * This operation is atomic - either all changes succeed or none are applied.
     * If this is the last hangout in the series, the entire series is deleted.
     *
     * @param hangoutId The ID of the hangout being deleted
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if hangout doesn't exist
     * @throws com.bbthechange.inviter.exception.RepositoryException if transaction fails
     */
    void removeHangoutFromSeries(String hangoutId);
    
    /**
     * Gets detailed view of a single series including all its hangout details.
     * This method fetches the EventSeries record and all full Hangout objects.
     *
     * @param seriesId The ID of the series to retrieve
     * @param userId The ID of the user requesting the series (for authorization)
     * @return EventSeriesDetailDTO containing series info and full hangout details
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series doesn't exist
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user lacks permission
     * @throws com.bbthechange.inviter.exception.RepositoryException if query fails
     */
    EventSeriesDetailDTO getSeriesDetail(String seriesId, String userId);
    
    /**
     * Updates the properties of an existing event series.
     * Only the non-null fields in the request will be updated.
     * Validates that primaryEventId (if provided) is a member of the series.
     * Updates all corresponding SeriesPointer records to maintain data consistency.
     *
     * @param seriesId The ID of the series to update
     * @param updateRequest The update request containing new values and current version
     * @param userId The ID of the user performing the action
     * @return The updated EventSeries object
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series doesn't exist
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user lacks permission
     * @throws com.bbthechange.inviter.exception.ValidationException if primaryEventId is not a member
     * @throws com.bbthechange.inviter.exception.OptimisticLockingException if version doesn't match
     * @throws com.bbthechange.inviter.exception.RepositoryException if update fails
     */
    EventSeries updateSeries(String seriesId, UpdateSeriesRequest updateRequest, String userId);
    
    /**
     * Deletes an entire event series and all of its constituent hangouts.
     * This is a cascading delete operation that removes all associated records atomically:
     * - The EventSeries record
     * - All Hangout records that are part of the series
     * - All HangoutPointer records for those hangouts
     * - All SeriesPointer records for the series
     * This operation is atomic - either all changes succeed or none are applied.
     *
     * @param seriesId The ID of the series to delete
     * @param userId The ID of the user performing the action
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series doesn't exist
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user lacks permission
     * @throws com.bbthechange.inviter.exception.RepositoryException if transaction fails
     */
    void deleteEntireSeries(String seriesId, String userId);
}