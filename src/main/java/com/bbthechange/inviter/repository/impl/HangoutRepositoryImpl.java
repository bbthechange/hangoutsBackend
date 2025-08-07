package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.EventDetailData;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.time.Instant;
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
    
    private final DynamoDbClient dynamoDbClient;
    private static final String TABLE_NAME = "InviterTable";
    private final TableSchema<Hangout> hangoutSchema;
    private final TableSchema<Poll> pollSchema;
    private final TableSchema<Car> carSchema;
    private final TableSchema<Vote> voteSchema;
    private final TableSchema<InterestLevel> interestLevelSchema;
    private final TableSchema<CarRider> carRiderSchema;
    private final TableSchema<HangoutPointer> hangoutPointerSchema;
    private final QueryPerformanceTracker performanceTracker;
    private final EventRepository eventRepository; // For canonical Event records
    
    @Autowired
    public HangoutRepositoryImpl(
            DynamoDbClient dynamoDbClient,
            QueryPerformanceTracker performanceTracker,
            EventRepository eventRepository) {
        this.dynamoDbClient = dynamoDbClient;
        this.hangoutSchema = TableSchema.fromBean(Hangout.class);
        this.pollSchema = TableSchema.fromBean(Poll.class);
        this.carSchema = TableSchema.fromBean(Car.class);
        this.voteSchema = TableSchema.fromBean(Vote.class);
        this.interestLevelSchema = TableSchema.fromBean(InterestLevel.class);
        this.carRiderSchema = TableSchema.fromBean(CarRider.class);
        this.hangoutPointerSchema = TableSchema.fromBean(HangoutPointer.class);
        this.performanceTracker = performanceTracker;
        this.eventRepository = eventRepository;
    }
    
    /**
     * Deserialize a DynamoDB item based on its itemType discriminator or SK pattern.
     */
    private BaseItem deserializeItem(Map<String, AttributeValue> itemMap) {
        AttributeValue typeAttr = itemMap.get("itemType");
        if (typeAttr == null || typeAttr.s() == null) {
            // Fallback to SK pattern matching for backward compatibility
            AttributeValue skAttr = itemMap.get("sk");
            if (skAttr != null) {
                String sk = skAttr.s();
                if (InviterKeyFactory.isMetadata(sk)) {
                    return hangoutSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isPollItem(sk)) {
                    return pollSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isCarItem(sk)) {
                    return carSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isVoteItem(sk)) {
                    return voteSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isAttendanceItem(sk)) {
                    return interestLevelSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isCarRiderItem(sk)) {
                    return carRiderSchema.mapToItem(itemMap);
                }
            }
            throw new IllegalStateException("Missing itemType discriminator and unable to determine type from SK");
        }
        
        String itemType = typeAttr.s();
        switch (itemType) {
            case "HANGOUT":
                return hangoutSchema.mapToItem(itemMap);
            case "POLL":
                return pollSchema.mapToItem(itemMap);
            case "CAR":
                return carSchema.mapToItem(itemMap);
            case "VOTE":
                return voteSchema.mapToItem(itemMap);
            case "INTEREST_LEVEL":
                return interestLevelSchema.mapToItem(itemMap);
            case "CAR_RIDER":
                return carRiderSchema.mapToItem(itemMap);
            default:
                throw new IllegalArgumentException("Unknown item type: " + itemType);
        }
    }


    public HangoutDetailData getHangoutDetailData(String eventId) {
        return performanceTracker.trackQuery("getHangoutDetailData", "InviterTable", () -> {
            try {
                // Single query gets ALL event data - the power pattern!
                QueryRequest request = QueryRequest.builder()
                        .tableName(TABLE_NAME)
                        .keyConditionExpression("pk = :pk")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build()
                        ))
                        .scanIndexForward(true)
                        .build();

                QueryResponse response = dynamoDbClient.query(request);

                List<BaseItem> allItems = response.items().stream()
                        .map(this::deserializeItem)
                        .collect(Collectors.toList());

                // Sort by sort key patterns in memory (efficient - documented contract)
                List<Poll> polls = new ArrayList<>();
                List<Car> cars = new ArrayList<>();
                List<Vote> votes = new ArrayList<>();
                List<InterestLevel> attendance = new ArrayList<>();
                List<CarRider> carRiders = new ArrayList<>();
                Optional<Hangout> hangoutOption = Optional.empty();

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
                    } else if (InviterKeyFactory.isCarRiderItem(sk)) {
                        carRiders.add((CarRider) item); // Safe - key pattern guarantees CarRider
                    } else if (InviterKeyFactory.isMetadata(sk)) {
                        hangoutOption = Optional.of((Hangout) item);
                    }
                }

                Hangout hangout = hangoutOption
                        .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

                return new HangoutDetailData(hangout, polls, cars, votes, attendance, carRiders);

            } catch (Exception e) {
                logger.error("Failed to get event detail data for event {}", eventId, e);
                throw new RepositoryException("Failed to retrieve event details", e);
            }
        });
    }
    
    @Override
    public EventDetailData getEventDetailData(String eventId) {
        return performanceTracker.trackQuery("getEventDetailData", "InviterTable", () -> {
            try {
                // Single query gets ALL event data - the power pattern!
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build()
                    ))
                    .scanIndexForward(true)
                    .build();
                
                QueryResponse response = dynamoDbClient.query(request);
                
                List<BaseItem> allItems = response.items().stream()
                    .map(this::deserializeItem)
                    .collect(Collectors.toList());
                
                // Sort by sort key patterns in memory (efficient - documented contract)
                List<Poll> polls = new ArrayList<>();
                List<Car> cars = new ArrayList<>();
                List<Vote> votes = new ArrayList<>();
                List<InterestLevel> attendance = new ArrayList<>();
                List<CarRider> carRiders = new ArrayList<>();

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
                    } else if (InviterKeyFactory.isCarRiderItem(sk)) {
                        carRiders.add((CarRider) item); // Safe - key pattern guarantees CarRider
                    }
                }

                // Get canonical event record from main Events table
                Event event = eventRepository.findById(UUID.fromString(eventId))
                    .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));
                
                return new EventDetailData(event, polls, cars, votes, attendance, carRiders);
                
            } catch (Exception e) {
                logger.error("Failed to get event detail data for event {}", eventId, e);
                throw new RepositoryException("Failed to retrieve event details", e);
            }
        });
    }
    
    @Override
    public Hangout createHangout(Hangout hangout) {
        return performanceTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                hangout.touch();
                Map<String, AttributeValue> itemMap = hangoutSchema.itemToMap(hangout, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
                return hangout;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to create hangout {}", hangout.getHangoutId(), e);
                throw new RepositoryException("Failed to create hangout", e);
            }
        });
    }
    
    @Override
    public void saveHangoutAndPointersAtomically(Hangout hangout, List<HangoutPointer> pointers) {
        performanceTracker.trackQuery("saveHangoutAndPointersAtomically", TABLE_NAME, () -> {
            try {
                hangout.touch();
                
                // Prepare all transact write items
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // Add hangout put item
                Map<String, AttributeValue> hangoutItem = hangoutSchema.itemToMap(hangout, true);
                transactItems.add(TransactWriteItem.builder()
                    .put(Put.builder()
                        .tableName(TABLE_NAME)
                        .item(hangoutItem)
                        .build())
                    .build());
                
                // Add pointer put items
                for (HangoutPointer pointer : pointers) {
                    pointer.touch();
                    Map<String, AttributeValue> pointerItem = hangoutPointerSchema.itemToMap(pointer, true);
                    transactItems.add(TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(pointerItem)
                            .build())
                        .build());
                }
                
                // Execute atomic transaction
                TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(request);
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to atomically save hangout {} and {} pointers", 
                    hangout.getHangoutId(), pointers.size(), e);
                throw new RepositoryException("Failed to atomically save hangout and pointers", e);
            }
        });
    }
    
    @Override
    public Optional<Hangout> findHangoutById(String hangoutId) {
        return performanceTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();
                
                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }
                
                Hangout hangout = hangoutSchema.mapToItem(response.item());
                return Optional.of(hangout);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to find hangout {}", hangoutId, e);
                throw new RepositoryException("Failed to retrieve hangout", e);
            }
        });
    }
    
    @Override
    public void updateHangoutMetadata(String hangoutId, Map<String, AttributeValue> updates) {
        performanceTracker.trackQuery("updateHangoutMetadata", "InviterTable", () -> {
            try {
                // This would need to be implemented using UpdateExpression
                throw new UnsupportedOperationException("Direct metadata updates not yet implemented for hangouts");
            } catch (Exception e) {
                logger.error("Failed to update hangout metadata for {}", hangoutId, e);
                throw new RepositoryException("Failed to update hangout metadata", e);
            }
        });
    }
    
    @Override
    public void deleteHangout(String hangoutId) {
        performanceTracker.trackQuery("deleteHangout", "InviterTable", () -> {
            try {
                // First get all items in the hangout's item collection
                QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build()
                    ))
                    .build();
                
                QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
                
                // Delete all items in the collection using individual delete operations
                for (Map<String, AttributeValue> item : queryResponse.items()) {
                    DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", item.get("pk"),
                            "sk", item.get("sk")
                        ))
                        .build();
                    dynamoDbClient.deleteItem(deleteRequest);
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete hangout {}", hangoutId, e);
                throw new RepositoryException("Failed to delete hangout", e);
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
                poll.touch();
                Map<String, AttributeValue> itemMap = pollSchema.itemToMap(poll, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
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
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getPollSk(pollId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
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
                car.touch();
                Map<String, AttributeValue> itemMap = carSchema.itemToMap(car, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
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
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getCarSk(driverId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
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
                carRider.touch();
                Map<String, AttributeValue> itemMap = carRiderSchema.itemToMap(carRider, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
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
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getCarRiderSk(driverId, riderId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
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
                vote.touch();
                Map<String, AttributeValue> itemMap = voteSchema.itemToMap(vote, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
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
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getVoteSk(pollId, userId, optionId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
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
                interestLevel.touch();
                Map<String, AttributeValue> itemMap = interestLevelSchema.itemToMap(interestLevel, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
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
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getAttendanceSk(userId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete interest level for user {} in event {}", userId, eventId, e);
                throw new RepositoryException("Failed to delete interest level", e);
            }
        });
    }
}