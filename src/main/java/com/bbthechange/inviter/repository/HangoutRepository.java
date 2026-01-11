package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.util.PaginatedResult;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for hangout/event management operations in the InviterTable.
 * Provides the powerful item collection pattern for efficient data retrieval.
 */
public interface HangoutRepository {


    /**
     * Get all event-related data in a single query using item collection pattern.
     * This is the power pattern - one query gets event + polls + cars + votes + attendance!
     */
    HangoutDetailData getHangoutDetailData(String eventId);
    
    // New Hangout CRUD operations for InviterTable
    Hangout createHangout(Hangout hangout);
    Hangout createHangoutWithAttributes(Hangout hangout, List<HangoutPointer> pointers, List<HangoutAttribute> attributes, List<Poll> polls, List<PollOption> pollOptions);
    Optional<Hangout> findHangoutById(String hangoutId);
    void updateHangoutMetadata(String hangoutId, Map<String, AttributeValue> updates);
    void deleteHangout(String hangoutId);
    
    // Hangout CRUD operations
    Hangout save(Hangout hangout);
    
    // Legacy Event CRUD for backward compatibility (uses existing Events table)
    Event save(Event event);
    Optional<Event> findById(String eventId);
    void updateEventMetadata(String eventId, Map<String, AttributeValue> updates);
    void delete(String eventId);
    
    // Atomic operations for hangout and pointer records
    void saveHangoutAndPointersAtomically(Hangout hangout, List<HangoutPointer> pointers);
    
    // InviterTable operations for new hangout features
    Poll savePoll(Poll poll);
    void deletePoll(String eventId, String pollId);
    
    // Poll Option operations
    PollOption savePollOption(PollOption option);
    List<PollOption> getPollOptions(String eventId, String pollId);
    void deletePollOptionTransaction(String eventId, String pollId, String optionId);
    
    // Query methods for poll data retrieval
    List<BaseItem> getAllPollData(String eventId);
    List<BaseItem> getSpecificPollData(String eventId, String pollId);
    
    Car saveCar(Car car);
    void deleteCar(String eventId, String driverId);
    
    CarRider saveCarRider(CarRider carRider);
    void deleteCarRider(String eventId, String driverId, String riderId);
    
    Vote saveVote(Vote vote);
    void deleteVote(String eventId, String pollId, String userId, String optionId);
    
    InterestLevel saveInterestLevel(InterestLevel interestLevel);
    void deleteInterestLevel(String eventId, String userId);
    
    // Hangout Attribute operations (UUID-based for efficient access)
    /**
     * Find a specific attribute by hangout ID and attribute ID.
     * Uses direct key access for optimal performance.
     */
    Optional<HangoutAttribute> findAttributeById(String hangoutId, String attributeId);
    
    /**
     * Find all attributes for a hangout in a single query.
     * Uses single-partition query with pk=EVENT#{hangoutId} and SK begins_with ATTRIBUTE#
     */
    List<HangoutAttribute> findAttributesByHangoutId(String hangoutId);
    
    /**
     * Save a hangout attribute. Performs upsert operation.
     */
    HangoutAttribute saveAttribute(HangoutAttribute attribute);
    
    /**
     * Delete a hangout attribute by hangout ID and attribute ID.
     * Idempotent operation - succeeds even if attribute doesn't exist.
     */
    void deleteAttribute(String hangoutId, String attributeId);
    
    // Needs Ride operations
    NeedsRide saveNeedsRide(NeedsRide needsRide);
    List<NeedsRide> getNeedsRideListForEvent(String eventId);
    void deleteNeedsRide(String eventId, String userId);
    Optional<NeedsRide> getNeedsRideRequestForUser(String eventId, String userId);
    
    // GSI query methods for EntityTimeIndex
    /**
     * Find upcoming hangouts for a participant (user or group) in chronological order.
     * Only returns hangouts that start after the current time.
     */
    List<BaseItem> findUpcomingHangoutsForParticipant(String participantKey, String timePrefix);
    
    /**
     * Find a paginated set of upcoming hangouts for a participant (user or group).
     * Uses true database-level pagination with DynamoDB ExclusiveStartKey.
     * 
     * @param participantKey The participant key (e.g., "GROUP#groupId" or "USER#userId")
     * @param timePrefix The time prefix for filtering (e.g., "T#")
     * @param limit Maximum number of items to return in this page
     * @param startToken Pagination token from previous request, or null for first page
     * @return PaginatedResult containing the hangouts for this page and next page token
     */
    PaginatedResult<HangoutPointer> findUpcomingHangoutsPage(String participantKey, String timePrefix, 
                                                            int limit, String startToken);
    
    // Enhanced Group Feed Pagination Methods
    
    /**
     * Get future events for a group using EntityTimeIndex.
     * Queries events with startTimestamp > nowTimestamp.
     * Now returns BaseItem to support both HangoutPointer and SeriesPointer.
     * 
     * @param groupId The group ID
     * @param nowTimestamp Current timestamp
     * @param limit Maximum number of events to return (null for no limit)
     * @param startToken Pagination token for continuing from previous query
     * @return PaginatedResult containing future events and series
     */
    PaginatedResult<BaseItem> getFutureEventsPage(String groupId, long nowTimestamp, 
                                                 Integer limit, String startToken);
    
    /**
     * Get in-progress events for a group using EndTimestampIndex.
     * Queries events with endTimestamp > nowTimestamp and applies filter startTimestamp <= nowTimestamp.
     * Now returns BaseItem to support both HangoutPointer and SeriesPointer.
     * 
     * @param groupId The group ID
     * @param nowTimestamp Current timestamp
     * @param limit Maximum number of events to return (null for no limit)
     * @param startToken Pagination token for continuing from previous query
     * @return PaginatedResult containing in-progress events and series
     */
    PaginatedResult<BaseItem> getInProgressEventsPage(String groupId, long nowTimestamp,
                                                     Integer limit, String startToken);
    
    /**
     * Get past events for a group using EntityTimeIndex in reverse order.
     * Queries events with startTimestamp < nowTimestamp.
     * Now returns BaseItem to support both HangoutPointer and SeriesPointer.
     * 
     * @param groupId The group ID
     * @param nowTimestamp Current timestamp
     * @param limit Maximum number of events to return (null for no limit)
     * @param endToken Pagination token for continuing backwards from previous query
     * @return PaginatedResult containing past events and series
     */
    PaginatedResult<BaseItem> getPastEventsPage(String groupId, long nowTimestamp,
                                               Integer limit, String endToken);
    
    // HangoutPointer operations
    
    /**
     * Find all HangoutPointer records for a specific hangout across all its associated groups.
     * Uses BatchGetItem for efficient retrieval of multiple pointer records.
     * 
     * Since a hangout can be associated with multiple groups, there will be one pointer
     * record per group (e.g., PK=GROUP#A, SK=HANGOUT#123 for each group).
     * 
     * @param hangout The hangout to find pointers for
     * @return List of HangoutPointer records (one per associated group)
     */
    List<HangoutPointer> findPointersForHangout(Hangout hangout);
    
    // Series-related operations
    
    /**
     * Find all hangouts that belong to a specific EventSeries.
     * Uses the SeriesIndex GSI for efficient querying.
     *
     * ⚠️ PERFORMANCE CRITICAL: This method MUST use the SeriesIndex GSI.
     * Never fetch all hangouts and filter in memory - this would be a severe performance issue.
     *
     * @param seriesId The series identifier
     * @return List of hangouts in the series, ordered by start timestamp
     */
    List<Hangout> findHangoutsBySeriesId(String seriesId);

    // Reminder scheduling operations

    /**
     * Atomically set reminderSentAt if it is currently null/missing.
     * Used for idempotent reminder sending - only the first caller wins.
     *
     * @param hangoutId The hangout ID
     * @param timestamp The epoch millis timestamp to set
     * @return true if update succeeded (was null), false if already set (lost race)
     */
    boolean setReminderSentAtIfNull(String hangoutId, long timestamp);

    /**
     * Update the reminderScheduleName for a hangout.
     * Used to track the EventBridge schedule for updates/deletion.
     *
     * @param hangoutId The hangout ID
     * @param scheduleName The schedule name (e.g., "hangout-{id}")
     */
    void updateReminderScheduleName(String hangoutId, String scheduleName);

    /**
     * Clear the reminderSentAt flag when a hangout's start time changes.
     * Allows a new reminder to be scheduled after time updates.
     *
     * @param hangoutId The hangout ID
     */
    void clearReminderSentAt(String hangoutId);

    /**
     * Find a hangout by its external ID and external source.
     * Uses the ExternalIdIndex GSI for efficient lookup.
     *
     * @param externalId The external identifier from the source system
     * @param externalSource The source system name (e.g., "TICKETMASTER", "YELP")
     * @return Optional containing the hangout if found, empty otherwise
     */
    Optional<Hangout> findByExternalIdAndSource(String externalId, String externalSource);
}