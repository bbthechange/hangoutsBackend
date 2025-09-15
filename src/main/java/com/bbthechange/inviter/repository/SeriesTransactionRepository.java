package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;

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
     * 3. Updating the existing Hangout's Pointer to link it to the series
     * 4. Creating the new Hangout part with series linkage
     * 5. Creating the new Hangout's Pointer with series linkage
     * 
     * If any step fails, the entire transaction is rolled back by DynamoDB.
     *
     * @param seriesToCreate The fully-prepared EventSeries object to create
     * @param hangoutToUpdate The existing Hangout that needs its seriesId field set
     * @param pointerToUpdate The existing HangoutPointer that needs its seriesId field set
     * @param newHangoutToCreate The fully-prepared new Hangout object to create
     * @param newPointerToCreate The fully-prepared new HangoutPointer object to create
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void createSeriesWithNewPart(
        EventSeries seriesToCreate,
        Hangout hangoutToUpdate,
        HangoutPointer pointerToUpdate,
        Hangout newHangoutToCreate,
        HangoutPointer newPointerToCreate
    );
    
    /**
     * Atomically adds a new hangout part to an existing event series.
     * This transaction ensures all-or-nothing execution of:
     * 1. Updating the EventSeries to add the new hangout ID to its list
     * 2. Creating the new Hangout part with series linkage
     * 3. Creating the new Hangout's Pointer with series linkage
     * 
     * If any step fails, the entire transaction is rolled back by DynamoDB.
     *
     * @param seriesId The ID of the existing series to update
     * @param newHangoutToCreate The fully-prepared new Hangout object to create
     * @param newPointerToCreate The fully-prepared new HangoutPointer object to create
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void addPartToExistingSeries(
        String seriesId,
        Hangout newHangoutToCreate,
        HangoutPointer newPointerToCreate
    );
}