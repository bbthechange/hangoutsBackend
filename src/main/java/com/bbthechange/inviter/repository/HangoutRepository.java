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
     * 
     * @param groupId The group ID
     * @param nowTimestamp Current timestamp
     * @param limit Maximum number of events to return (null for no limit)
     * @param startToken Pagination token for continuing from previous query
     * @return PaginatedResult containing future events
     */
    PaginatedResult<HangoutPointer> getFutureEventsPage(String groupId, long nowTimestamp, 
                                                       Integer limit, String startToken);
    
    /**
     * Get in-progress events for a group using EndTimestampIndex.
     * Queries events with endTimestamp > nowTimestamp and applies filter startTimestamp <= nowTimestamp.
     * 
     * @param groupId The group ID
     * @param nowTimestamp Current timestamp
     * @param limit Maximum number of events to return (null for no limit)
     * @param startToken Pagination token for continuing from previous query
     * @return PaginatedResult containing in-progress events
     */
    PaginatedResult<HangoutPointer> getInProgressEventsPage(String groupId, long nowTimestamp,
                                                           Integer limit, String startToken);
    
    /**
     * Get past events for a group using EntityTimeIndex in reverse order.
     * Queries events with startTimestamp < nowTimestamp.
     * 
     * @param groupId The group ID
     * @param nowTimestamp Current timestamp
     * @param limit Maximum number of events to return (null for no limit)
     * @param endToken Pagination token for continuing backwards from previous query
     * @return PaginatedResult containing past events
     */
    PaginatedResult<HangoutPointer> getPastEventsPage(String groupId, long nowTimestamp,
                                                     Integer limit, String endToken);
}