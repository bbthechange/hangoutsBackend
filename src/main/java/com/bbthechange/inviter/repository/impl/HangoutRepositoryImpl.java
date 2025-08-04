package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.EventDetailData;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of HangoutRepository using the powerful item collection pattern.
 * Single query gets all event-related data for maximum efficiency.
 */
@Repository
public class HangoutRepositoryImpl implements HangoutRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(HangoutRepositoryImpl.class);
    
    private final DynamoDbTable<BaseItem> inviterTable;
    private final QueryPerformanceTracker performanceTracker;
    private final EventRepository eventRepository; // For canonical Event records
    
    @Autowired
    public HangoutRepositoryImpl(
            DynamoDbEnhancedClient dynamoDbEnhancedClient,
            QueryPerformanceTracker performanceTracker,
            EventRepository eventRepository) {
        this.inviterTable = dynamoDbEnhancedClient.table("InviterTable", TableSchema.fromBean(BaseItem.class));
        this.performanceTracker = performanceTracker;
        this.eventRepository = eventRepository;
    }
    
    @Override
    public EventDetailData getEventDetailData(String eventId) {
        return performanceTracker.trackQuery("getEventDetailData", "InviterTable", () -> {
            try {
                // Single query gets ALL event data - the power pattern!
                List<BaseItem> allItems = inviterTable.query(QueryEnhancedRequest.builder()
                    .queryConditional(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(InviterKeyFactory.getEventPk(eventId))
                        .build()))
                    .scanIndexForward(true)
                    .build())
                    .items()
                    .stream()
                    .collect(Collectors.toList());
                
                // Sort by sort key patterns in memory (efficient - documented contract)
                List<Poll> polls = new ArrayList<>();
                List<Car> cars = new ArrayList<>();
                List<Vote> votes = new ArrayList<>();
                List<InterestLevel> attendance = new ArrayList<>();
                
                for (BaseItem item : allItems) {
                    String sk = item.getSk();
                    
                    // Use key patterns to safely identify types (documented contract)
                    if (InviterKeyFactory.isPollItem(sk)) {
                        polls.add((Poll) item); // Safe - key pattern guarantees Poll
                    } else if (InviterKeyFactory.isCarItem(sk)) {
                        cars.add((Car) item); // Safe - key pattern guarantees Car
                    } else if (InviterKeyFactory.isVoteItem(sk)) {
                        votes.add((Vote) item); // Safe - key pattern guarantees Vote
                    } else if (InviterKeyFactory.isAttendanceItem(sk)) {
                        attendance.add((InterestLevel) item); // Safe - key pattern guarantees InterestLevel
                    }
                }
                
                // Get canonical event record from main Events table
                Event event = eventRepository.findById(UUID.fromString(eventId))
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
                
                return new EventDetailData(event, polls, cars, votes, attendance);
                
            } catch (Exception e) {
                logger.error("Failed to get event detail data for event {}", eventId, e);
                throw new RepositoryException("Failed to retrieve event details", e);
            }
        });
    }
    
    @Override
    public Event save(Event event) {
        // Delegate to existing EventRepository for canonical records
        return eventRepository.save(event);
    }
    
    @Override
    public Optional<Event> findById(String eventId) {
        // Delegate to existing EventRepository for canonical records
        return eventRepository.findById(UUID.fromString(eventId));
    }
    
    @Override
    public void updateEventMetadata(String eventId, Map<String, AttributeValue> updates) {
        // Delegate to existing EventRepository for canonical updates
        // This would need to be implemented in EventRepository if not already present
        throw new UnsupportedOperationException("Direct metadata updates not yet implemented");
    }
    
    @Override
    public void delete(String eventId) {
        // Delegate to existing EventRepository for canonical records
        eventRepository.deleteById(UUID.fromString(eventId));
    }
    
    @Override
    public Poll savePoll(Poll poll) {
        return performanceTracker.trackQuery("savePoll", "InviterTable", () -> {
            try {
                inviterTable.putItem(poll);
                return poll;
            } catch (Exception e) {
                logger.error("Failed to save poll {}", poll.getPollId(), e);
                throw new RepositoryException("Failed to save poll", e);
            }
        });
    }
    
    @Override
    public void deletePoll(String eventId, String pollId) {
        performanceTracker.trackQuery("deletePoll", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(eventId))
                    .sortValue(InviterKeyFactory.getPollSk(pollId))
                    .build();
                
                inviterTable.deleteItem(key);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete poll {} for event {}", pollId, eventId, e);
                throw new RepositoryException("Failed to delete poll", e);
            }
        });
    }
    
    @Override
    public Car saveCar(Car car) {
        return performanceTracker.trackQuery("saveCar", "InviterTable", () -> {
            try {
                inviterTable.putItem(car);
                return car;
            } catch (Exception e) {
                logger.error("Failed to save car for driver {}", car.getDriverId(), e);
                throw new RepositoryException("Failed to save car", e);
            }
        });
    }
    
    @Override
    public void deleteCar(String eventId, String driverId) {
        performanceTracker.trackQuery("deleteCar", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(eventId))
                    .sortValue(InviterKeyFactory.getCarSk(driverId))
                    .build();
                
                inviterTable.deleteItem(key);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete car for driver {} in event {}", driverId, eventId, e);
                throw new RepositoryException("Failed to delete car", e);
            }
        });
    }
    
    @Override
    public CarRider saveCarRider(CarRider carRider) {
        return performanceTracker.trackQuery("saveCarRider", "InviterTable", () -> {
            try {
                inviterTable.putItem(carRider);
                return carRider;
            } catch (Exception e) {
                logger.error("Failed to save car rider {} for driver {}", carRider.getRiderId(), carRider.getDriverId(), e);
                throw new RepositoryException("Failed to save car rider", e);
            }
        });
    }
    
    @Override
    public void deleteCarRider(String eventId, String driverId, String riderId) {
        performanceTracker.trackQuery("deleteCarRider", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(eventId))
                    .sortValue(InviterKeyFactory.getCarRiderSk(driverId, riderId))
                    .build();
                
                inviterTable.deleteItem(key);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete car rider {} from driver {} in event {}", riderId, driverId, eventId, e);
                throw new RepositoryException("Failed to delete car rider", e);
            }
        });
    }
    
    @Override
    public Vote saveVote(Vote vote) {
        return performanceTracker.trackQuery("saveVote", "InviterTable", () -> {
            try {
                inviterTable.putItem(vote);
                return vote;
            } catch (Exception e) {
                logger.error("Failed to save vote for user {} on poll {}", vote.getUserId(), vote.getPollId(), e);
                throw new RepositoryException("Failed to save vote", e);
            }
        });
    }
    
    @Override
    public void deleteVote(String eventId, String pollId, String userId, String optionId) {
        performanceTracker.trackQuery("deleteVote", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(eventId))
                    .sortValue(InviterKeyFactory.getVoteSk(pollId, userId, optionId))
                    .build();
                
                inviterTable.deleteItem(key);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete vote for user {} on poll {} option {}", userId, pollId, optionId, e);
                throw new RepositoryException("Failed to delete vote", e);
            }
        });
    }
    
    @Override
    public InterestLevel saveInterestLevel(InterestLevel interestLevel) {
        return performanceTracker.trackQuery("saveInterestLevel", "InviterTable", () -> {
            try {
                inviterTable.putItem(interestLevel);
                return interestLevel;
            } catch (Exception e) {
                logger.error("Failed to save interest level for user {} in event {}", interestLevel.getUserId(), interestLevel.getEventId(), e);
                throw new RepositoryException("Failed to save interest level", e);
            }
        });
    }
    
    @Override
    public void deleteInterestLevel(String eventId, String userId) {
        performanceTracker.trackQuery("deleteInterestLevel", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(eventId))
                    .sortValue(InviterKeyFactory.getAttendanceSk(userId))
                    .build();
                
                inviterTable.deleteItem(key);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete interest level for user {} in event {}", userId, eventId, e);
                throw new RepositoryException("Failed to delete interest level", e);
            }
        });
    }
}