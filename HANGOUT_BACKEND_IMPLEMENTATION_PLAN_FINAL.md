# Backend Technical Implementation Plan for HangOut App (Final)

## Overview
This comprehensive plan addresses all critical issues identified in the original implementation, including proper separation of concerns, error handling, type safety, data consistency, testing, deployment, and performance optimization.

## Key Improvements in Final Version
- Proper repository interfaces and separation of concerns
- Comprehensive error handling and validation
- Type-safe operations using sort key patterns (no unsafe casting)
- DynamoDB transactions for data consistency
- Elimination of circular dependencies
- Performance optimizations with built-in query tracking
- Enhanced security and authorization
- Testable architecture with proper abstractions
- Complete testing strategy with TestContainers
- Deployment and monitoring strategies
- Performance monitoring built-in from day one

## Phase 1: Database Architecture Changes

### 1.1 InviterTable Implementation
Create a new single-table design for all new HangOut features:

```java
// Table: InviterTable
// Uses composite keys for optimal query patterns

| PK (Partition Key) | SK (Sort Key) | GSI1PK | GSI1SK | Key Data Attributes |
|-------------------|----------------|---------|---------|---------------------|
| GROUP#{GroupID} | METADATA | - | - | groupName, isPublic, createdAt |
| GROUP#{GroupID} | USER#{UserID} | USER#{UserID} | GROUP#{GroupID} | role, joinedAt, groupName |
| EVENT#{HangoutID} | METADATA | - | - | title, status, hangoutTime, locationName, participantCount, associatedGroups[], carpoolEnabled |
| EVENT#{HangoutID} | INVITE#{UserID} | - | - | status: "PENDING/ACCEPTED" |
| EVENT#{HangoutID} | ATTENDANCE#{UserID} | - | - | status: "GOING/INTERESTED/NOT_GOING" |
| GROUP#{GroupID} | HANGOUT#{HangoutID} | - | - | title, status, hangoutTime, locationName, participantCount |
| EVENT#{HangoutID} | POLL#{PollID} | - | - | title, createdAt |
| EVENT#{HangoutID} | POLL#{PollID}#OPTION#{OptionID} | - | - | text, createdAt |
| EVENT#{HangoutID} | POLL#{PollID}#VOTE#{UserID}#OPTION#{OptionID} | - | - | voteType, userName, userId |
| EVENT#{HangoutID} | CAR#{DriverID} | - | - | driverName, totalCapacity, availableSeats |
| EVENT#{HangoutID} | CAR#{DriverID}#RIDER#{RiderID} | - | - | riderName, plusOneCount |
| EVENT#{HangoutID} | NEEDS_RIDE#{UserID} | - | - | userName, notes, plusOneCount |
```

### 1.2 Existing Table Modifications
Modify the Events table metadata to support new fields:
- Add `associatedGroups` array field
- Add `carpoolEnabled` boolean flag
- Keep existing fields for backward compatibility

## Phase 2: Spring Boot Implementation Components

### 2.1 Core Infrastructure

**InviterKeyFactory.java (Type-Safe with Validation)**
```java
package com.bbthechange.inviter.util;

import com.bbthechange.inviter.exception.InvalidKeyException;
import java.util.regex.Pattern;

public final class InviterKeyFactory {
    private static final String DELIMITER = "#";
    private static final Pattern UUID_PATTERN = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", 
        Pattern.CASE_INSENSITIVE
    );
    
    // Constants for magic strings
    public static final String GROUP_PREFIX = "GROUP";
    public static final String USER_PREFIX = "USER";
    public static final String EVENT_PREFIX = "EVENT";
    public static final String METADATA_SUFFIX = "METADATA";
    public static final String POLL_PREFIX = "POLL";
    public static final String CAR_PREFIX = "CAR";
    public static final String ATTENDANCE_PREFIX = "ATTENDANCE";
    public static final String HANGOUT_PREFIX = "HANGOUT";
    
    // Validation methods
    private static void validateId(String id, String type) {
        if (id == null || id.trim().isEmpty()) {
            throw new InvalidKeyException(type + " ID cannot be null or empty");
        }
        if (!UUID_PATTERN.matcher(id).matches()) {
            throw new InvalidKeyException("Invalid " + type + " ID format: " + id);
        }
    }
    
    // Group Keys with validation
    public static String getGroupPk(String groupId) {
        validateId(groupId, "Group");
        return GROUP_PREFIX + DELIMITER + groupId;
    }
    
    public static String getUserSk(String userId) {
        validateId(userId, "User");
        return USER_PREFIX + DELIMITER + userId;
    }
    
    // Event/Hangout Keys
    public static String getEventPk(String eventId) {
        validateId(eventId, "Event");
        return EVENT_PREFIX + DELIMITER + eventId;
    }
    
    public static String getHangoutSk(String hangoutId) {
        validateId(hangoutId, "Hangout");
        return HANGOUT_PREFIX + DELIMITER + hangoutId;
    }
    
    // Poll Keys (designed for efficient querying)
    public static String getPollSk(String pollId) {
        validateId(pollId, "Poll");
        return POLL_PREFIX + DELIMITER + pollId;
    }
    
    public static String getPollOptionSk(String pollId, String optionId) {
        validateId(pollId, "Poll");
        validateId(optionId, "Option");
        return String.join(DELIMITER, POLL_PREFIX, pollId, "OPTION", optionId);
    }
    
    public static String getVoteSk(String pollId, String userId, String optionId) {
        validateId(pollId, "Poll");
        validateId(userId, "User");
        validateId(optionId, "Option");
        return String.join(DELIMITER, POLL_PREFIX, pollId, "VOTE", userId, "OPTION", optionId);
    }
    
    // Query Prefixes for efficient filtering
    public static String getPollPrefix(String pollId) {
        validateId(pollId, "Poll");
        return POLL_PREFIX + DELIMITER + pollId;
    }
    
    // GSI Keys
    public static String getUserGsi1Pk(String userId) {
        validateId(userId, "User");
        return USER_PREFIX + DELIMITER + userId;
    }
    
    public static String getGroupGsi1Sk(String groupId) {
        validateId(groupId, "Group");
        return GROUP_PREFIX + DELIMITER + groupId;
    }
    
    // Helper methods for type-safe filtering
    public static boolean isPollItem(String sortKey) {
        return sortKey.startsWith(POLL_PREFIX + DELIMITER) && 
               !sortKey.contains("#OPTION#") && 
               !sortKey.contains("#VOTE#");
    }
    
    public static boolean isCarItem(String sortKey) {
        return sortKey.startsWith(CAR_PREFIX + DELIMITER) && 
               !sortKey.contains("#RIDER#");
    }
    
    public static boolean isVoteItem(String sortKey) {
        return sortKey.contains("#VOTE#");
    }
}
```

### 2.2 Repository Layer with Proper Abstractions

**GroupRepository.java (Interface)**
```java
package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.*;
import java.util.List;
import java.util.Optional;

public interface GroupRepository {
    // Atomic operations
    void createGroupWithFirstMember(Group group, GroupMembership membership);
    
    // Simple CRUD
    Group save(Group group);
    Optional<Group> findById(String groupId);
    void delete(String groupId);
    
    // Membership operations
    GroupMembership addMember(GroupMembership membership);
    void removeMember(String groupId, String userId);
    Optional<GroupMembership> findMembership(String groupId, String userId);
    List<GroupMembership> findMembersByGroupId(String groupId);
    List<GroupMembership> findGroupsByUserId(String userId); // GSI query
    
    // Pointer record operations (simple CRUD only)
    void saveHangoutPointer(HangoutPointer pointer);
    void updateHangoutPointer(String groupId, String hangoutId, Map<String, AttributeValue> updates);
    void deleteHangoutPointer(String groupId, String hangoutId);
    List<HangoutPointer> findHangoutsByGroupId(String groupId);
}
```

**GroupRepositoryImpl.java (With Performance Tracking)**
```java
package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class GroupRepositoryImpl implements GroupRepository {
    private static final Logger logger = LoggerFactory.getLogger(GroupRepositoryImpl.class);
    
    private final DynamoDbTable<BaseItem> inviterTable;
    private final DynamoDbClient dynamoDbClient;
    private final QueryPerformanceTracker performanceTracker;
    
    @Autowired
    public GroupRepositoryImpl(
            DynamoDbEnhancedClient dynamoDbEnhancedClient, 
            DynamoDbClient dynamoDbClient,
            QueryPerformanceTracker performanceTracker) {
        this.inviterTable = dynamoDbEnhancedClient.table("InviterTable", TableSchema.fromBean(BaseItem.class));
        this.dynamoDbClient = dynamoDbClient;
        this.performanceTracker = performanceTracker;
    }
    
    @Override
    public void createGroupWithFirstMember(Group group, GroupMembership membership) {
        try {
            // Atomic creation using TransactWriteItems (follows context doc pattern)
            TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                    // 1. Create group metadata
                    TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName("InviterTable")
                            .item(convertToAttributeValueMap(group))
                            .conditionExpression("attribute_not_exists(pk)")
                            .build())
                        .build(),
                    // 2. Add creator as first member  
                    TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName("InviterTable")
                            .item(convertToAttributeValueMap(membership))
                            .build())
                        .build()
                )
                .build();
                
            dynamoDbClient.transactWriteItems(request);
            logger.info("Created group {} with first member {}", group.getGroupId(), membership.getUserId());
            
        } catch (TransactionCanceledException e) {
            logger.error("Transaction failed for group creation: {}", e.getMessage());
            throw new TransactionFailedException("Failed to create group atomically", e);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error in group creation", e);
            throw new RepositoryException("Database operation failed", e);
        }
    }
    
    @Override
    public List<GroupMembership> findGroupsByUserId(String userId) {
        return performanceTracker.trackQuery("findGroupsByUserId", "InviterTable", () -> {
            try {
                // Single GSI query - returns denormalized groupName, no additional queries needed!
                QueryConditional queryConditional = QueryConditional
                    .keyEqualTo(Key.builder()
                        .partitionValue(InviterKeyFactory.getUserGsi1Pk(userId))
                        .build());
                
                return inviterTable.index("UserGroupIndex")
                    .query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(true)
                        .build())
                    .items()
                    .stream()
                    .filter(item -> item.getSk().startsWith("USER#")) // Safe type filtering
                    .map(item -> (GroupMembership) item) // Safe cast - key pattern guarantees type
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find groups for user {}", userId, e);
                throw new RepositoryException("Failed to retrieve user groups", e);
            }
        });
    }
    
    @Override
    public List<HangoutPointer> findHangoutsByGroupId(String groupId) {
        return performanceTracker.trackQuery("findHangoutsByGroupId", "InviterTable", () -> {
            try {
                QueryConditional queryConditional = QueryConditional
                    .sortBeginsWith(Key.builder()
                        .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                        .sortValue(InviterKeyFactory.HANGOUT_PREFIX)
                        .build());
                
                return inviterTable.query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(true)
                        .build())
                    .items()
                    .stream()
                    .filter(item -> item.getSk().startsWith("HANGOUT#")) // Safe type filtering
                    .map(item -> (HangoutPointer) item) // Safe cast - key pattern guarantees type
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find hangouts for group {}", groupId, e);
                throw new RepositoryException("Failed to retrieve group hangouts", e);
            }
        });
    }
    
    // Helper method for DynamoDB attribute conversion
    private Map<String, AttributeValue> convertToAttributeValueMap(BaseItem item) {
        return TableSchema.fromBean(item.getClass()).itemToMap(item, true);
    }
}
```

**EventRepository.java (Item Collection Pattern)**
```java
package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.EventDetailData;
import java.util.Optional;
import java.util.Map;

public interface EventRepository {
    // Single item collection query (the power pattern!)
    EventDetailData getEventDetailData(String eventId);
    
    // Simple CRUD for canonical records
    Event save(Event event);
    Optional<Event> findById(String eventId);
    void updateEventMetadata(String eventId, Map<String, AttributeValue> updates);
    void delete(String eventId);
}
```

**EventRepositoryImpl.java (Item Collection Implementation)**
```java
@Repository
public class EventRepositoryImpl implements EventRepository {
    private final QueryPerformanceTracker performanceTracker;
    
    @Override
    public EventDetailData getEventDetailData(String eventId) {
        return performanceTracker.trackQuery("getEventDetailData", "InviterTable", () -> {
            try {
                // Single query gets ALL event data - the power pattern from context doc!
                List<BaseItem> allItems = inviterTable.query(QueryEnhancedRequest.builder()
                    .keyCondition(QueryConditional.keyEqualTo(Key.builder()
                        .partitionValue(InviterKeyFactory.getEventPk(eventId))
                        .build()))
                    .scanIndexForward(true)
                    .build())
                    .items()
                    .stream()
                    .collect(Collectors.toList());
                
                // Sort by sort key patterns in memory (efficient - documented contract)
                Event event = null;
                List<Poll> polls = new ArrayList<>();
                List<Car> cars = new ArrayList<>();
                List<Vote> votes = new ArrayList<>();
                List<AttendanceRecord> attendance = new ArrayList<>();
                
                for (BaseItem item : allItems) {
                    String sk = item.getSk();
                    
                    // Use key patterns to safely identify types (documented contract)
                    if (InviterKeyFactory.METADATA_SUFFIX.equals(sk)) {
                        event = (Event) item; // Safe - METADATA always = Event
                    } else if (InviterKeyFactory.isPollItem(sk)) {
                        polls.add((Poll) item); // Safe - key pattern guarantees Poll
                    } else if (InviterKeyFactory.isCarItem(sk)) {
                        cars.add((Car) item); // Safe - key pattern guarantees Car
                    } else if (InviterKeyFactory.isVoteItem(sk)) {
                        votes.add((Vote) item); // Safe - key pattern guarantees Vote
                    } else if (sk.startsWith(InviterKeyFactory.ATTENDANCE_PREFIX)) {
                        attendance.add((AttendanceRecord) item); // Safe - key pattern guarantees AttendanceRecord
                    }
                }
                
                return new EventDetailData(event, polls, cars, votes, attendance);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to get event detail data for event {}", eventId, e);
                throw new RepositoryException("Failed to retrieve event details", e);
            }
        });
    }
}
```

## Phase 3: Service Layer with Proper Business Logic

### 3.1 Service Interfaces

**GroupService.java**
```java
package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import java.util.List;

public interface GroupService {
    GroupDTO createGroup(CreateGroupRequest request, String creatorId);
    GroupDTO getGroup(String groupId, String requestingUserId);
    void deleteGroup(String groupId, String requestingUserId);
    
    void addMember(String groupId, String userId, String addedBy);
    void removeMember(String groupId, String userId, String removedBy);
    List<GroupMemberDTO> getGroupMembers(String groupId, String requestingUserId);
    
    List<GroupDTO> getUserGroups(String userId); // Uses efficient GSI pattern
    boolean isUserInGroup(String userId, String groupId);
    
    GroupFeedDTO getGroupFeed(String groupId, String requestingUserId);
}
```

### 3.2 Service Implementations (Follows Multi-Step Pattern from Context Doc)

**GroupServiceImpl.java**
```java
@Service
@Transactional
public class GroupServiceImpl implements GroupService {
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);
    
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    
    @Override
    public GroupDTO createGroup(CreateGroupRequest request, String creatorId) {
        // Input validation
        validateCreateGroupRequest(request);
        
        // Verify creator exists
        User creator = userRepository.findById(UUID.fromString(creatorId))
            .orElseThrow(() -> new UserNotFoundException("Creator not found: " + creatorId));
        
        // Create both records for atomic operation
        Group group = new Group(request.getGroupName(), request.isPublic());
        GroupMembership membership = new GroupMembership(
            group.getGroupId(), 
            creatorId, 
            group.getGroupName() // Denormalize for GSI efficiency
        );
        membership.setRole("ADMIN");
        
        // Atomic creation using TransactWriteItems pattern from context doc
        groupRepository.createGroupWithFirstMember(group, membership);
        
        logger.info("Created group {} with creator {}", group.getGroupId(), creatorId);
        return new GroupDTO(group, membership);
    }
    
    @Override
    public List<GroupDTO> getUserGroups(String userId) {
        // Single GSI query gets everything we need - no N+1 queries!
        List<GroupMembership> memberships = groupRepository.findGroupsByUserId(userId);
        
        // No additional queries needed! groupName is denormalized on membership records
        return memberships.stream()
            .map(membership -> new GroupDTO(
                membership.getGroupId(),
                membership.getGroupName(), // Already available - no lookup needed!
                membership.getRole(),
                membership.getJoinedAt()
            ))
            .collect(Collectors.toList());
    }
    
    @Override
    public GroupFeedDTO getGroupFeed(String groupId, String requestingUserId) {
        // Authorization check
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new UnauthorizedException("User not in group");
        }
        
        // Single query gets all hangout pointers for group
        List<HangoutPointer> hangouts = groupRepository.findHangoutsByGroupId(groupId);
        
        // Separate by status in memory (fast)
        List<HangoutDTO> withDay = hangouts.stream()
            .filter(h -> h.getHangoutTime() != null)
            .sorted(Comparator.comparing(HangoutPointer::getHangoutTime))
            .map(HangoutDTO::new)
            .collect(Collectors.toList());
            
        List<HangoutDTO> needsDay = hangouts.stream()
            .filter(h -> h.getHangoutTime() == null)
            .map(HangoutDTO::new)
            .collect(Collectors.toList());
            
        return new GroupFeedDTO(groupId, withDay, needsDay);
    }
    
    private void validateCreateGroupRequest(CreateGroupRequest request) {
        if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
            throw new ValidationException("Group name is required");
        }
        if (request.getGroupName().length() > 100) {
            throw new ValidationException("Group name must be 100 characters or less");
        }
    }
}
```

**EventService.java (Pointer Update Pattern)**
```java
@Service
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final GroupRepository groupRepository;
    
    @Override
    public EventDetailDTO getEventDetail(String eventId, String requestingUserId) {
        // Single item collection query gets EVERYTHING (the power pattern!)
        EventDetailData data = eventRepository.getEventDetailData(eventId);
        
        // Authorization check
        if (!canUserViewEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot view event");
        }
        
        // All data fetched in one query - just transform to DTO
        return new EventDetailDTO(
            data.getEvent(),
            data.getPolls(),
            data.getCars(),
            data.getVotes(),
            data.getAttendance()
        );
    }
    
    @Override
    public void updateEventTitle(String eventId, String newTitle, String requestingUserId) {
        // Authorization check first
        EventDetailData data = eventRepository.getEventDetailData(eventId);
        if (!canUserEditEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        // Multi-step process from context doc:
        
        // Step 1: Update canonical record first
        Map<String, AttributeValue> updates = Map.of(
            ":title", AttributeValue.builder().s(newTitle).build()
        );
        eventRepository.updateEventMetadata(eventId, updates);
        
        // Step 2: Get associated groups list from canonical record
        List<String> associatedGroups = data.getEvent().getAssociatedGroups();
        
        // Step 3: Batch update each pointer record for performance
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updatePointerRecordsBatch(eventId, associatedGroups, Map.of("title", newTitle));
        }
    }
    
    private void updatePointerRecordsBatch(String eventId, List<String> groupIds, Map<String, String> updates) {
        // Process in batches of 25 (DynamoDB batch limit) for performance
        List<List<String>> batches = Lists.partition(groupIds, 25);
        
        for (List<String> batch : batches) {
            Map<String, AttributeValue> updateValues = updates.entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> ":" + entry.getKey(),
                    entry -> AttributeValue.builder().s(entry.getValue()).build()
                ));
            
            for (String groupId : batch) {
                groupRepository.updateHangoutPointer(groupId, eventId, updateValues);
            }
        }
    }
}
```

## Phase 4: Controller Layer with Comprehensive Validation

### 4.1 Base Controller with Error Handling

**BaseController.java**
```java
@RestController
public abstract class BaseController {
    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);
    
    // Extract user ID with proper error handling
    protected String extractUserId(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null || userId.trim().isEmpty()) {
            throw new UnauthorizedException("No authenticated user");
        }
        return userId;
    }
    
    // Common exception handlers
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        logger.warn("Unauthorized access attempt: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ErrorResponse("UNAUTHORIZED", e.getMessage()));
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        logger.warn("Validation error: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage()));
    }
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        logger.debug("Resource not found: {}", e.getMessage());
        return ResponseEntity.notFound().build();
    }
    
    @ExceptionHandler(DynamoDbException.class)
    public ResponseEntity<ErrorResponse> handleDynamoDbError(DynamoDbException e) {
        logger.error("DynamoDB error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("DATABASE_ERROR", "Internal server error"));
    }
}
```

### 4.2 Controllers with Proper Validation

**GroupController.java**
```java
@RestController
@RequestMapping("/groups")
@Validated
public class GroupController extends BaseController {
    private static final Logger logger = LoggerFactory.getLogger(GroupController.class);
    
    private final GroupService groupService;
    
    @PostMapping
    public ResponseEntity<GroupDTO> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Creating group {} for user {}", request.getGroupName(), userId);
        
        GroupDTO group = groupService.createGroup(request, userId);
        logger.info("Successfully created group {} with ID {}", group.getGroupName(), group.getGroupId());
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }
    
    @GetMapping
    public ResponseEntity<List<GroupDTO>> getUserGroups(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        
        // This uses the efficient GSI pattern - no N+1 queries!
        List<GroupDTO> groups = groupService.getUserGroups(userId);
        logger.debug("Retrieved {} groups for user {}", groups.size(), userId);
        return ResponseEntity.ok(groups);
    }
    
    @GetMapping("/{groupId}/feed")
    public ResponseEntity<GroupFeedDTO> getGroupFeed(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}") String groupId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        // Single query gets all hangout pointers - very efficient!
        GroupFeedDTO feed = groupService.getGroupFeed(groupId, userId);
        return ResponseEntity.ok(feed);
    }
}
```

**EventController.java (Item Collection Pattern)**
```java
@RestController
@RequestMapping("/events")
public class EventController extends BaseController {
    private final EventService eventService;
    
    @GetMapping("/{eventId}/detail")
    public ResponseEntity<EventDetailDTO> getEventDetail(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}") String eventId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        // Single item collection query gets ALL data - the power pattern!
        EventDetailDTO detail = eventService.getEventDetail(eventId, userId);
        
        logger.debug("Retrieved event detail for {} - {} polls, {} cars, {} attendance records", 
            eventId, 
            detail.getPolls().size(),
            detail.getCars().size(), 
            detail.getAttendance().size());
            
        return ResponseEntity.ok(detail);
    }
    
    @PatchMapping("/{eventId}")
    public ResponseEntity<Void> updateEvent(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}") String eventId,
            @Valid @RequestBody UpdateEventRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        // This follows the multi-step pointer update pattern from context doc
        if (request.getTitle() != null) {
            eventService.updateEventTitle(eventId, request.getTitle(), userId);
            logger.info("Updated title for event {} by user {}", eventId, userId);
        }
        
        return ResponseEntity.ok().build();
    }
}
```

## Phase 5: Input Validation Strategy

### 5.1 Multi-Layered Validation

**Controller Layer - Format & Security Validation**
```java
// Path parameter validation
@PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId

// Query parameter validation  
@RequestParam @Min(0) int offset
@RequestParam @Min(1) @Max(100) int limit

// Request body validation
@Valid @RequestBody CreateGroupRequest request
```

**DTO Level - JSR-303 Bean Validation**
```java
public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    private String groupName;
    
    @NotNull(message = "Public setting is required")
    private Boolean isPublic;
    
    // Input sanitization in getters
    public String getGroupName() { 
        return groupName != null ? groupName.trim() : null; 
    }
}
```

**Service Layer - Business Rule Validation**
```java
private void validateCreateGroupRequest(CreateGroupRequest request) {
    if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
        throw new ValidationException("Group name is required");
    }
    
    if (request.getGroupName().length() > 100) {
        throw new ValidationException("Group name must be 100 characters or less");
    }
}
```

## Phase 6: Testing Strategy

### 6.1 Repository Testing with TestContainers

```java
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
class GroupRepositoryImplTest {
    
    @Container
    static final GenericContainer<?> dynamoContainer = new GenericContainer<>("amazon/dynamodb-local:latest")
        .withExposedPorts(8000);
    
    @Test
    @Order(1)
    void createGroupWithFirstMember_AtomicSuccess() {
        // Given
        Group group = TestDataFactory.createGroup("Test Group");
        GroupMembership membership = TestDataFactory.createMembership(
            group.getGroupId(), "user-123", "Test Group");
        membership.setRole("ADMIN");
        
        // When
        assertThatCode(() -> groupRepository.createGroupWithFirstMember(group, membership))
            .doesNotThrowAnyException();
        
        // Then - verify both items created atomically
        Optional<Group> savedGroup = groupRepository.findById(group.getGroupId());
        assertThat(savedGroup).isPresent();
        
        Optional<GroupMembership> savedMembership = groupRepository.findMembership(
            group.getGroupId(), "user-123");
        assertThat(savedMembership).isPresent();
        assertThat(savedMembership.get().getRole()).isEqualTo("ADMIN");
    }
    
    @Test
    void findGroupsByUserId_GSIQuery() {
        // When
        List<GroupMembership> result = groupRepository.findGroupsByUserId("user-123");
        
        // Then - verify GSI query returns denormalized data
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo("Test Group"); // Denormalized
        assertThat(result.get(0).getGsi1pk()).isEqualTo("USER#user-123");
    }
}
```

### 6.2 Service Testing with Mocks

```java
@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {
    
    @Mock private GroupRepository groupRepository;
    @InjectMocks private GroupServiceImpl groupService;
    
    @Test
    void getUserGroups_UsesGSIEfficiently() {
        // Given
        String userId = "user-123";
        List<GroupMembership> memberships = List.of(
            TestDataFactory.createMembership("group-1", userId, "Group One"),
            TestDataFactory.createMembership("group-2", userId, "Group Two")
        );
        
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(memberships);
        
        // When
        List<GroupDTO> result = groupService.getUserGroups(userId);
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGroupName()).isEqualTo("Group One"); // Denormalized data
        
        // Verify only ONE GSI query, no additional lookups (no N+1!)
        verify(groupRepository, times(1)).findGroupsByUserId(userId);
        verify(groupRepository, never()).findById(any());
    }
}
```

## Phase 7: Performance Optimizations (Built-in)

### 7.1 Query Performance Tracking

```java
@Component
public class QueryPerformanceTracker {
    private static final Logger logger = LoggerFactory.getLogger(QueryPerformanceTracker.class);
    private final MeterRegistry meterRegistry;
    
    public <T> T trackQuery(String operation, String table, Supplier<T> queryOperation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();
        
        try {
            T result = queryOperation.get();
            long duration = System.currentTimeMillis() - startTime;
            
            // Log slow queries (>500ms threshold)
            if (duration > 500) {
                logger.warn("Slow DynamoDB query detected: operation={}, table={}, duration={}ms", 
                    operation, table, duration);
            }
            
            return result;
            
        } finally {
            sample.stop(Timer.builder("dynamodb.query.duration")
                .tag("operation", operation)
                .tag("table", table)
                .register(meterRegistry));
        }
    }
}
```

### 7.2 DynamoDB SDK Configuration

```java
@Configuration
public class DynamoDbPerformanceConfig {
    
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
            .region(Region.US_WEST_2)
            // Connection pool optimization
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(10))
                .apiCallAttemptTimeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.builder().numRetries(3).build())
                .build())
            // HTTP client configuration
            .httpClientBuilder(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(2))
                .socketTimeout(Duration.ofSeconds(5)))
            .build();
    }
}
```

### 7.3 Hot Partition Prevention

```java
public class Group extends BaseItem {
    public Group(String groupName, boolean isPublic) {
        // Use UUID to prevent hot partitions (not sequential IDs)
        this.groupId = UUID.randomUUID().toString();
        this.setPk(InviterKeyFactory.getGroupPk(this.groupId));
        // ...
    }
}
```

## Phase 8: Deployment Configuration

### 8.1 Environment Configuration

```yaml
# application-prod.yml  
spring:
  config:
    activate:
      on-profile: prod

aws:
  dynamodb:
    region: us-west-2
    # No endpoint - use real AWS
  s3:
    region: us-west-2
    bucket: inviter-event-images-871070087012

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus

logging:
  level:
    root: WARN
    com.bbthechange.inviter: INFO
```

### 8.2 Health Checks

```java
@Component
public class DynamoDbHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        try {
            // Check if InviterTable exists and is active
            DescribeTableResponse response = dynamoDbClient.describeTable(
                DescribeTableRequest.builder().tableName("InviterTable").build()
            );
            
            TableStatus status = response.table().tableStatus();
            
            if (status == TableStatus.ACTIVE) {
                return Health.up()
                    .withDetail("table", "InviterTable")
                    .withDetail("status", status.toString())
                    .withDetail("gsiCount", response.table().globalSecondaryIndexes().size())
                    .build();
            } else {
                return Health.down()
                    .withDetail("table", "InviterTable")
                    .withDetail("status", status.toString())
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", "DynamoDB connection failed")
                .build();
        }
    }
}
```

### 8.3 Database Initialization

**DynamoDBTableInitializer.java**
```java
@Component
public class DynamoDBTableInitializer implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTableInitializer.class);
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Existing tables
        createTableIfAbsent("Users", User.class);
        createTableIfAbsent("Events", Event.class);  
        createTableIfAbsent("Invites", Invite.class);
        createTableIfAbsent("Devices", Device.class);
        
        // New InviterTable with GSI
        createInviterTableIfAbsent();
    }
    
    private void createInviterTableIfAbsent() {
        try {
            CreateTableRequest request = CreateTableRequest.builder()
                .tableName("InviterTable")
                .keySchema(
                    KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
                )
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("gsi1pk").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("gsi1sk").attributeType(ScalarAttributeType.S).build()
                )
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("UserGroupIndex")
                        .keySchema(
                            KeySchemaElement.builder().attributeName("gsi1pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("gsi1sk").keyType(KeyType.RANGE).build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L).writeCapacityUnits(5L).build())
                        .build()
                )
                .provisionedThroughput(ProvisionedThroughput.builder()
                    .readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build();
                
            dynamoDbClient.createTable(request);
            
            // Wait for table to be active
            dynamoDbClient.waiter().waitUntilTableExists(r -> r.tableName("InviterTable"));
            logger.info("Created InviterTable successfully");
            
        } catch (ResourceInUseException e) {
            logger.info("InviterTable already exists");
        } catch (DynamoDbException e) {
            logger.error("Failed to create InviterTable", e);
            throw new InitializationException("Failed to initialize database", e);
        }
    }
}
```

## API Endpoint Summary

### Group Management
- `POST /groups` - Create new group (atomic with first member)  
- `GET /groups` - Get user's groups (efficient GSI query)
- `GET /groups/{groupId}/feed` - Get group's hangouts (single query)
- `POST /groups/{groupId}/members` - Add member to group

### Enhanced Event/Hangout Management  
- `GET /events/{id}/detail` - Get event with all related data (single item collection query)
- `PATCH /events/{id}` - Update event (canonical first, then pointer records)

### Decision System (Polls) - Following same patterns
- `POST /events/{eventId}/polls` - Create poll/decision
- `POST /events/{eventId}/polls/{pollId}/options` - Add option
- `PUT /events/{eventId}/polls/{pollId}/options/{optionId}/vote` - Cast vote

### Carpooling - Following same patterns  
- `POST /events/{eventId}/carpool/cars` - Offer ride
- `POST /events/{eventId}/carpool/riders` - Claim seat
- `POST /events/{eventId}/carpool/needs-ride` - Request ride

## Implementation Timeline

### Week 1-2: Core Infrastructure
- Implement InviterKeyFactory with validation and type-safe helpers
- Create BaseItem and core model classes (Group, GroupMembership, HangoutPointer)
- Set up InviterRepository interface and implementation
- Update DynamoDBTableInitializer with InviterTable creation

### Week 3-4: Group & Permission System  
- Implement GroupService with atomic group creation
- Create GroupController with proper validation
- Set up performance tracking and query monitoring
- Remove host requirements per HANGOUT_HOST_REMOVAL_GUIDE

### Week 5-6: Event System & Decision Support
- Implement EventService with item collection pattern and pointer updates
- Add polling system (PollService, PollController)
- Add carpooling system (CarpoolService, CarpoolController)
- Create enhanced event detail endpoint

### Week 7-8: Testing, Deployment & Monitoring
- Complete TestContainers integration tests
- Set up health checks and performance monitoring  
- Configure deployment pipeline with health verification
- Performance testing and optimization

## Key Implementation Principles

1. **Follow Context Document Patterns**: Use proven single-table design patterns
2. **Type Safety via Key Patterns**: Document and use sort key contracts for safe casting
3. **Performance First**: Built-in query tracking and optimization
4. **Clean Architecture**: Proper separation between repository, service, and controller layers
5. **Comprehensive Testing**: TestContainers for repository, mocks for service, full Spring context for controllers
6. **Production Ready**: Health checks, monitoring, proper error handling, and deployment automation

This plan provides a robust, scalable, and maintainable foundation for the HangOut backend while leveraging all the benefits of the single-table DynamoDB design.