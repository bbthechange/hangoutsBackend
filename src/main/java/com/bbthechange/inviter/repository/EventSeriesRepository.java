package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.EventSeries;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for EventSeries management operations in the InviterTable.
 * Provides operations for managing multi-part event series.
 */
public interface EventSeriesRepository {

    /**
     * Save an EventSeries to the database.
     * Performs upsert operation - creates new or updates existing series.
     * 
     * @param eventSeries The series to save
     * @return The saved series
     */
    EventSeries save(EventSeries eventSeries);
    
    /**
     * Find an EventSeries by its ID.
     * 
     * @param seriesId The unique series identifier
     * @return Optional containing the series if found, empty otherwise
     */
    Optional<EventSeries> findById(String seriesId);
    
    /**
     * Delete an EventSeries by its ID.
     * This is an idempotent operation - succeeds even if series doesn't exist.
     * 
     * @param seriesId The unique series identifier
     */
    void deleteById(String seriesId);
    
    /**
     * Update specific fields of an EventSeries using DynamoDB UpdateExpression.
     * This allows atomic updates of individual fields without retrieving the entire record.
     * 
     * @param seriesId The unique series identifier
     * @param updates Map of attribute names to new values
     */
    void updateSeriesMetadata(String seriesId, Map<String, AttributeValue> updates);
    
    /**
     * Find all EventSeries for a specific group.
     * Uses the EntityTimeIndex GSI to get all series in a group, sorted by start time.
     * 
     * @param groupId The group identifier
     * @return List of EventSeries for the group, ordered by start timestamp
     */
    List<EventSeries> findByGroupId(String groupId);
    
    /**
     * Find upcoming EventSeries for a specific group.
     * Uses the EntityTimeIndex GSI with timestamp filtering for future series only.
     * 
     * @param groupId The group identifier
     * @param currentTimestamp Current time in epoch seconds
     * @return List of future EventSeries for the group, ordered by start timestamp
     */
    List<EventSeries> findUpcomingByGroupId(String groupId, long currentTimestamp);
}