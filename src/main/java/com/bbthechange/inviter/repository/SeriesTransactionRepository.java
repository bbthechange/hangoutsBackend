package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import java.util.List;

/**
 * Repository for handling complex transactional operations when creating
 * and managing multi-part event series.
 * 
 * This repository encapsulates the atomic write operations required when
 * converting a standalone hangout into part of a series or adding new
 * parts to an existing series.
 */
public interface SeriesTransactionRepository {
    
    /**
     * Atomically creates a new event series from an existing hangout and a new hangout.
     * This transaction ensures all-or-nothing execution of:
     * 1. Creating the new EventSeries record
     * 2. Updating the existing Hangout to link it to the series
     * 3. Updating ALL existing Hangout's Pointers to link them to the series
     * 4. Creating the new Hangout part with series linkage
     * 5. Creating ALL new Hangout's Pointers with series linkage
     * 6. Creating ALL new SeriesPointer records (one per associated group)
     * 
     * If any step fails, the entire transaction is rolled back by DynamoDB.
     *
     * @param seriesToCreate The fully-prepared EventSeries object to create
     * @param hangoutToUpdate The existing Hangout that needs its seriesId field set
     * @param pointersToUpdate List of existing HangoutPointers that need their seriesId field set
     * @param newHangoutToCreate The fully-prepared new Hangout object to create
     * @param newPointersToCreate List of fully-prepared new HangoutPointer objects to create
     * @param seriesPointersToCreate List of fully-prepared SeriesPointer objects to create (one per group)
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void createSeriesWithNewPart(
        EventSeries seriesToCreate,
        Hangout hangoutToUpdate,
        List<HangoutPointer> pointersToUpdate,
        Hangout newHangoutToCreate,
        List<HangoutPointer> newPointersToCreate,
        List<SeriesPointer> seriesPointersToCreate
    );
    
    /**
     * Atomically adds a new hangout part to an existing event series.
     * This transaction ensures all-or-nothing execution of:
     * 1. Updating the EventSeries to add the new hangout ID to its list
     * 2. Creating the new Hangout part with series linkage
     * 3. Creating ALL new Hangout's Pointers with series linkage
     * 4. Updating ALL existing SeriesPointer records to reflect the new hangout
     * 
     * If any step fails, the entire transaction is rolled back by DynamoDB.
     *
     * @param seriesId The ID of the existing series to update
     * @param newHangoutToCreate The fully-prepared new Hangout object to create
     * @param newPointersToCreate List of fully-prepared new HangoutPointer objects to create
     * @param seriesPointersToUpdate List of existing SeriesPointer records to update with the new hangout
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void addPartToExistingSeries(
        String seriesId,
        Hangout newHangoutToCreate,
        List<HangoutPointer> newPointersToCreate,
        List<SeriesPointer> seriesPointersToUpdate
    );
    
    /**
     * Atomically removes a hangout from a series without deleting the hangout.
     * This transaction ensures all-or-nothing execution of:
     * 1. Updating the EventSeries to remove the hangout ID from its list
     * 2. Clearing the seriesId field from the Hangout
     * 3. Clearing the seriesId field from all HangoutPointers
     * 4. Updating all SeriesPointer records to reflect the removal
     *
     * @param seriesToUpdate The EventSeries with the hangout ID already removed
     * @param hangoutToUpdate The Hangout with seriesId cleared
     * @param pointersToUpdate List of HangoutPointers with seriesId cleared
     * @param seriesPointersToUpdate List of SeriesPointers with the hangout removed
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void unlinkHangoutFromSeries(
        EventSeries seriesToUpdate,
        Hangout hangoutToUpdate,
        List<HangoutPointer> pointersToUpdate,
        List<SeriesPointer> seriesPointersToUpdate
    );
    
    /**
     * Atomically deletes an entire series when the last hangout is removed.
     * This transaction ensures all-or-nothing execution of:
     * 1. Deleting the EventSeries record
     * 2. Clearing the seriesId field from the final Hangout
     * 3. Clearing the seriesId field from all HangoutPointers
     * 4. Deleting all SeriesPointer records
     *
     * @param seriesToDelete The EventSeries to delete
     * @param hangoutToUpdate The final Hangout with seriesId to be cleared
     * @param pointersToUpdate List of HangoutPointers with seriesId to be cleared
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void deleteEntireSeries(
        EventSeries seriesToDelete,
        Hangout hangoutToUpdate,
        List<HangoutPointer> pointersToUpdate
    );
    
    /**
     * Atomically removes a hangout from a series and deletes all hangout records.
     * This transaction ensures all-or-nothing execution of:
     * 1. Updating the EventSeries to remove the hangout ID from its list
     * 2. Deleting the Hangout record completely
     * 3. Deleting all HangoutPointer records
     * 4. Updating all SeriesPointer records to reflect the removal
     *
     * @param seriesToUpdate The EventSeries with the hangout ID already removed
     * @param hangoutToDelete The Hangout to delete completely
     * @param pointersToDelete List of HangoutPointers to delete
     * @param seriesPointersToUpdate List of SeriesPointers with the hangout removed
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void removeHangoutFromSeries(
        EventSeries seriesToUpdate,
        Hangout hangoutToDelete,
        List<HangoutPointer> pointersToDelete,
        List<SeriesPointer> seriesPointersToUpdate
    );
    
    /**
     * Atomically deletes an entire series and its final hangout.
     * This transaction ensures all-or-nothing execution of:
     * 1. Deleting the EventSeries record
     * 2. Deleting the final Hangout record completely
     * 3. Deleting all HangoutPointer records
     * 4. Deleting all SeriesPointer records
     *
     * @param seriesToDelete The EventSeries to delete
     * @param hangoutToDelete The final Hangout to delete completely
     * @param pointersToDelete List of HangoutPointers to delete
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void deleteSeriesAndFinalHangout(
        EventSeries seriesToDelete,
        Hangout hangoutToDelete,
        List<HangoutPointer> pointersToDelete
    );
    
    /**
     * Atomically updates a series and its pointers after a hangout modification.
     * This transaction ensures all-or-nothing execution of:
     * 1. Updating the EventSeries record (timestamps, version)
     * 2. Updating all SeriesPointer records with new series data
     *
     * @param seriesToUpdate The EventSeries with updated timestamps and version
     * @param seriesPointersToUpdate List of SeriesPointers to update
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void updateSeriesAfterHangoutChange(
        EventSeries seriesToUpdate,
        List<SeriesPointer> seriesPointersToUpdate
    );
}