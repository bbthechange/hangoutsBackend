package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.EventDetailData;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
    EventDetailData getEventDetailData(String eventId);
    
    // Simple CRUD for canonical event records (still uses existing Events table)
    Event save(Event event);
    Optional<Event> findById(String eventId);
    void updateEventMetadata(String eventId, Map<String, AttributeValue> updates);
    void delete(String eventId);
    
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
}