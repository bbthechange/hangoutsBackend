package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.EventDetailData;
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
    @Deprecated
    EventDetailData getEventDetailData(String eventId);

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
     * Uses single-partition query with PK=EVENT#{hangoutId} and SK begins_with ATTRIBUTE#
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
    
    // GSI query methods for EntityTimeIndex
    /**
     * Find upcoming hangouts for a participant (user or group) in chronological order.
     * Only returns hangouts that start after the current time.
     */
    List<BaseItem> findUpcomingHangoutsForParticipant(String participantKey, String timePrefix);
}