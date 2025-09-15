package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
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
     * 
     * If any step fails, the entire transaction is rolled back by DynamoDB.
     *
     * @param seriesToCreate The fully-prepared EventSeries object to create
     * @param hangoutToUpdate The existing Hangout that needs its seriesId field set
     * @param pointersToUpdate List of existing HangoutPointers that need their seriesId field set
     * @param newHangoutToCreate The fully-prepared new Hangout object to create
     * @param newPointersToCreate List of fully-prepared new HangoutPointer objects to create
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void createSeriesWithNewPart(
        EventSeries seriesToCreate,
        Hangout hangoutToUpdate,
        List<HangoutPointer> pointersToUpdate,
        Hangout newHangoutToCreate,
        List<HangoutPointer> newPointersToCreate
    );
    
    /**
     * Atomically adds a new hangout part to an existing event series.
     * This transaction ensures all-or-nothing execution of:
     * 1. Updating the EventSeries to add the new hangout ID to its list
     * 2. Creating the new Hangout part with series linkage
     * 3. Creating ALL new Hangout's Pointers with series linkage
     * 
     * If any step fails, the entire transaction is rolled back by DynamoDB.
     *
     * @param seriesId The ID of the existing series to update
     * @param newHangoutToCreate The fully-prepared new Hangout object to create
     * @param newPointersToCreate List of fully-prepared new HangoutPointer objects to create
     * @throws com.bbthechange.inviter.exception.RepositoryException if the transaction fails
     */
    void addPartToExistingSeries(
        String seriesId,
        Hangout newHangoutToCreate,
        List<HangoutPointer> newPointersToCreate
    );
}