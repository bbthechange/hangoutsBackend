# Polling Feature Design Document

## Overview

This document outlines the design and implementation approach for adding a polling feature to the Inviter application. The polling system allows users to create polls within events, add multiple options, and vote using different vote types.

## Design Principles

- **Forward-Compatible**: Design enables future event structure optimization without data migration
- **Phased Approach**: Add polling functionality while deferring existing event data migration
- **Minimal Disruption**: Maintain existing API contracts and data structures
- **Single-Table Benefits**: Leverage DynamoDB single-table design for optimal performance

## Architecture Overview

### Table Strategy: Parallel Tables (Option A)

We will maintain existing tables and add a new `InviterTable` specifically for polling data:

```
Existing Tables (unchanged):
├── Users
├── Events  
├── Invites
└── Devices

New Table:
└── InviterTable (polls, options, votes)
```

**Benefits:**
- Zero production data migration risk
- Existing API endpoints remain unchanged
- Polling feature isolated and testable
- Future single-table migration can be planned separately

## Data Model

### InviterTable Structure

The new `InviterTable` uses a single-table design with composite keys:

| PK (Partition Key) | SK (Sort Key) | Data Attributes |
|---|---|---|
| `EVENT#{EventID}` | `POLL#{PollID}` | title, createdAt |
| `EVENT#{EventID}` | `POLL#{PollID}#OPTION#{OptionID}` | text, createdAt |
| `EVENT#{EventID}` | `POLL#{PollID}#VOTE#{UserID}#OPTION#{OptionID}` | voteType, userName, userId |

### Key Length Analysis

- **PK**: `EVENT#{EventID}` = 42 bytes (well under 2048 byte limit)
- **Longest SK**: `POLL#{PollID}#VOTE#{UserID}#OPTION#{OptionID}` = 127 bytes (well under 1024 byte limit)

### Global Secondary Index (Future Enhancement)

**UserVoteIndex GSI**: *Not implemented in initial release - can be added later if needed*

For future queries like "all polls a user has voted in", we can add:
- **GSI1PK**: `USER#{UserID}`  
- **GSI1SK**: `EVENT#{EventID}#POLL#{PollID}`

Note: Vote records include `userId` field to support this GSI when needed. DynamoDB allows adding GSIs to existing tables without downtime.

## Implementation Components

### 1. Key Management

#### InviterKeyFactory Class

```java
public final class InviterKeyFactory {
    private static final String DELIMITER = "#";
    
    // Event Keys
    public static String getEventPk(String eventId) {
        return "EVENT" + DELIMITER + eventId;
    }
    
    // Poll Keys
    public static String getPollSk(String pollId) {
        return "POLL" + DELIMITER + pollId;
    }
    
    public static String getPollOptionSk(String pollId, String optionId) {
        return String.join(DELIMITER, "POLL", pollId, "OPTION", optionId);
    }
    
    // Vote Keys
    public static String getVoteSk(String pollId, String userId, String optionId) {
        return String.join(DELIMITER, "POLL", pollId, "VOTE", userId, "OPTION", optionId);
    }
    
    // Parsers
    public static ParsedVoteSk parseVoteSk(String voteSk) {
        String[] parts = voteSk.split(DELIMITER);
        if (parts.length != 6) {
            throw new IllegalArgumentException("Invalid vote SK format: " + voteSk);
        }
        return new ParsedVoteSk(parts[1], parts[3], parts[5]); // pollId, userId, optionId
    }
    
    // Query Prefixes
    public static String getPollPrefix(String pollId) {
        return "POLL" + DELIMITER + pollId;
    }
}
```

### 2. Data Models

#### BaseItem Class

```java
@DynamoDbBean
public abstract class BaseItem {
    private String pk;
    private String sk;
    
    @DynamoDbPartitionKey
    public String getPk() { return pk; }
    public void setPk(String pk) { this.pk = pk; }
    
    @DynamoDbSortKey
    public String getSk() { return sk; }
    public void setSk(String sk) { this.sk = sk; }
}
```

#### Poll Model

```java
@DynamoDbBean
public class Poll extends BaseItem {
    private String pollId;
    private String title;
    private LocalDateTime createdAt;
    
    public Poll() {}
    
    public Poll(String eventId, String title) {
        this.pollId = UUID.randomUUID().toString();
        this.setPk(InviterKeyFactory.getEventPk(eventId));
        this.setSk(InviterKeyFactory.getPollSk(this.pollId));
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

#### PollOption Model

```java
@DynamoDbBean
public class PollOption extends BaseItem {
    private String optionId;
    private String pollId;
    private String text;
    private LocalDateTime createdAt;
    
    public PollOption() {}
    
    public PollOption(String eventId, String pollId, String text) {
        this.optionId = UUID.randomUUID().toString();
        this.pollId = pollId;
        this.setPk(InviterKeyFactory.getEventPk(eventId));
        this.setSk(InviterKeyFactory.getPollOptionSk(pollId, this.optionId));
        this.text = text;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and setters
}
```

#### Vote Model

```java
@DynamoDbBean
public class Vote extends BaseItem {
    private String pollId;
    private String userId;
    private String optionId;
    private VoteType voteType;
    private String userName;
    
    public Vote() {}
    
    public Vote(String eventId, String pollId, String userId, String optionId, VoteType voteType, String userName) {
        this.pollId = pollId;
        this.userId = userId;
        this.optionId = optionId;
        this.voteType = voteType;
        this.userName = userName;
        
        // Set primary keys
        this.setPk(InviterKeyFactory.getEventPk(eventId));
        this.setSk(InviterKeyFactory.getVoteSk(pollId, userId, optionId));
    }
    
    // Getters and setters
}
```

#### VoteType Enum

```java
public enum VoteType {
    PREFERENCE,
    YES,
    NO,
    MAYBE
}
```

### 3. Repository Layer

#### InviterRepository

```java
@Repository
public class InviterRepository {
    private final DynamoDbTable<BaseItem> inviterTable;
    
    @Autowired
    public InviterRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.inviterTable = dynamoDbEnhancedClient.table("InviterTable", TableSchema.fromBean(BaseItem.class));
    }
    
    // Poll Operations
    public Poll savePoll(Poll poll) {
        inviterTable.putItem(poll);
        return poll;
    }
    
    public List<Poll> getPollsForEvent(String eventId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        
        return inviterTable.query(r -> r.keyCondition(k -> k.partitionValue(pk).sortBeginsWith("POLL#")))
            .items()
            .stream()
            .filter(item -> !item.getSk().contains("#OPTION#") && !item.getSk().contains("#VOTE#"))
            .map(item -> (Poll) item)
            .collect(Collectors.toList());
    }
    
    // Batch Delete Operations
    public void deletePoll(String eventId, String pollId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        String skPrefix = InviterKeyFactory.getPollPrefix(pollId);
        
        // 1. Query for all items to delete
        List<BaseItem> itemsToDelete = inviterTable.query(r -> r
            .keyCondition(k -> k.partitionValue(pk).sortBeginsWith(skPrefix)))
            .items()
            .stream()
            .collect(Collectors.toList());
        
        if (itemsToDelete.isEmpty()) {
            return;
        }
        
        // 2. Create batch delete request
        WriteBatch.Builder<BaseItem> batchBuilder = WriteBatch.builder(BaseItem.class)
            .mappedTableResource(inviterTable);
            
        for (BaseItem item : itemsToDelete) {
            batchBuilder.addDeleteItem(r -> r.key(k -> k
                .partitionValue(item.getPk())
                .sortValue(item.getSk())));
        }
        
        // 3. Execute batch delete
        BatchWriteItemEnhancedRequest request = BatchWriteItemEnhancedRequest.builder()
            .writeBatches(batchBuilder.build())
            .build();
            
        dynamoDbEnhancedClient.batchWriteItem(request);
    }
    
    // Similar methods for options and votes...
}
```

### 4. Service Layer

#### PollService

```java
@Service
public class PollService {
    @Autowired
    private InviterRepository inviterRepository;
    
    @Autowired
    private InviteService inviteService;
    
    public Poll createPoll(String eventId, String title, String userId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(userId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized to create polls for this event");
        }
        
        Poll poll = new Poll(eventId, title);
        return inviterRepository.savePoll(poll);
    }
    
    public void deletePoll(String eventId, String pollId, String userId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(userId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized to delete polls for this event");
        }
        
        inviterRepository.deletePoll(eventId, pollId);
    }
    
    // Additional service methods...
}
```

### 5. Controller Layer

#### PollController

```java
@RestController
@RequestMapping("/events/{eventId}/polls")
public class PollController {
    @Autowired
    private PollService pollService;
    
    @PostMapping
    public ResponseEntity<Map<String, String>> createPoll(
            @PathVariable String eventId,
            @RequestBody CreatePollRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            Poll poll = pollService.createPoll(eventId, request.getTitle(), userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("pollId", poll.getPollId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
    
    @DeleteMapping("/{pollId}")
    public ResponseEntity<Map<String, String>> deletePoll(
            @PathVariable String eventId,
            @PathVariable String pollId,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            pollService.deletePoll(eventId, pollId, userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Poll deleted successfully");
            
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
    
    // Additional endpoints for options and votes...
}
```

## API Endpoints

### Complete API Specification

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `GET` | `/events/{eventId}` | Get event (unchanged) | - | Event object |
| `GET` | `/events/{eventId}/detail` | Get event with embedded polls | - | EventDetail with polls array |
| `POST` | `/events/{eventId}/polls` | Create new poll | `{"title": "string"}` | `{"pollId": "uuid"}` |
| `DELETE` | `/events/{eventId}/polls/{pollId}` | Delete poll and all votes | - | `{"message": "success"}` |
| `POST` | `/events/{eventId}/polls/{pollId}/options` | Add poll option | `{"text": "string"}` | `{"optionId": "uuid"}` |
| `DELETE` | `/events/{eventId}/polls/{pollId}/options/{optionId}` | Delete option and votes | - | `{"message": "success"}` |
| `PUT` | `/events/{eventId}/polls/{pollId}/options/{optionId}/vote` | Cast/update vote | `{"voteType": "PREFERENCE"}` | `{"message": "success"}` |
| `DELETE` | `/events/{eventId}/polls/{pollId}/options/{optionId}/vote` | Remove vote | - | `{"message": "success"}` |

### Backwards Compatible Endpoint Strategy

To maintain backwards compatibility, we'll keep the existing endpoint unchanged and add a new endpoint for enhanced data:

```java
// Existing endpoint - unchanged for backwards compatibility
@GetMapping("/{id}")
public ResponseEntity<Event> getEvent(@PathVariable UUID id, HttpServletRequest request) {
    // Current implementation stays exactly the same
    String userIdStr = (String) request.getAttribute("userId");
    if (userIdStr == null) {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
    
    UUID userId = UUID.fromString(userIdStr);
    Optional<Event> eventOpt = eventService.getEventForUser(id, userId);
    
    if (eventOpt.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    
    return new ResponseEntity<>(eventOpt.get(), HttpStatus.OK);
}

// New endpoint for enhanced data with polls
@GetMapping("/{id}/detail")
public ResponseEntity<EventDetail> getEventDetail(@PathVariable UUID id, HttpServletRequest request) {
    String userIdStr = (String) request.getAttribute("userId");
    if (userIdStr == null) {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
    
    UUID userId = UUID.fromString(userIdStr);
    
    // 1. Get event data (reuse existing logic)
    Optional<Event> eventOpt = eventService.getEventForUser(id, userId);
    if (eventOpt.isEmpty()) {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }
    
    // 2. Get poll data (parallel query)
    List<Poll> polls = pollService.getPollsForEvent(id.toString());
    
    // 3. Merge into response DTO
    EventDetail response = new EventDetail(eventOpt.get(), polls);
    
    return ResponseEntity.ok(response);
}
```

**Benefits of this approach:**
- **Zero Breaking Changes**: Existing web and iOS apps continue working
- **Future-Ready**: New frontend features can use `/detail` endpoint
- **Clear Intent**: Explicit separation between basic and detailed views
- **Safe Migration**: Teams can migrate to new endpoint at their own pace

## Database Schema Updates

### Table Initialization

Update `DynamoDBTableInitializer` to create the new table:

```java
@Override
public void run(ApplicationArguments args) throws Exception {
    // Existing tables
    createTableIfNotExists("Users", User.class);
    createTableIfNotExists("Events", Event.class);  
    createTableIfNotExists("Invites", Invite.class);
    createTableIfNotExists("Devices", Device.class);
    
    // New polling table
    createTableIfNotExists("InviterTable", BaseItem.class);
}

private <T> void createTableWithGSIs(String tableName, Class<T> entityClass) {
    // ... existing logic ...
    
    switch (tableName) {
        // ... existing cases ...
        
        case "InviterTable":
            // No GSIs in initial implementation
            // UserVoteIndex GSI can be added later if needed
            break;
    }
    
    // ... rest of method ...
}
```

## Authorization Model

### Simplified Permission System

With the upcoming host logic changes, authorization is straightforward:

- **Poll Creation**: Any user invited to the event
- **Poll Deletion**: Any user invited to the event  
- **Option Management**: Any user invited to the event
- **Voting**: Any user invited to the event

**Validation Pattern:**
```java
if (!inviteService.isUserInvitedToEvent(userId, eventId)) {
    throw new SecurityException("User not authorized for this event");
}
```

## Business Rules

### Voting Logic

- **Multiple Votes**: Users can vote multiple times on the same option with different vote types
- **Vote Updates**: `PUT` requests overwrite existing votes for that user/option/voteType combination
- **Vote Deletion**: `DELETE` removes specific user votes for an option

### Data Consistency

- **Cascading Deletes**: Deleting polls removes all options and votes
- **Cascading Deletes**: Deleting options removes all associated votes
- **Atomic Operations**: Use batch operations for multi-item deletions

## Testing Strategy

### Unit Tests

1. **InviterKeyFactory**: Test all key generation and parsing methods
2. **PollService**: Test business logic and authorization
3. **InviterRepository**: Test DynamoDB operations with TestContainers

### Integration Tests

1. **API Endpoints**: Test complete request/response cycles
2. **Batch Operations**: Test poll deletion with multiple votes
3. **Parallel Queries**: Test event endpoint with embedded polls

### Test Data Setup

```java
// Example test data creation
Poll testPoll = new Poll("event-123", "What time should we meet?");
PollOption option1 = new PollOption("event-123", testPoll.getPollId(), "7:00 PM");
PollOption option2 = new PollOption("event-123", testPoll.getPollId(), "8:00 PM");
Vote vote1 = new Vote("event-123", testPoll.getPollId(), "user-456", option1.getOptionId(), VoteType.PREFERENCE, "John Doe");
```

## Implementation Timeline

### Phase 1: Foundation
- [ ] Create `BaseItem`, `Poll`, `PollOption`, `Vote` models
- [ ] Implement `InviterKeyFactory` with comprehensive tests
- [ ] Update `DynamoDBTableInitializer` for new table
- [ ] Create `InviterRepository` with basic CRUD operations

### Phase 2: Core Features
- [ ] Implement `PollService` with business logic
- [ ] Create poll and option management endpoints
- [ ] Add voting endpoints
- [ ] Implement batch delete operations

### Phase 3: Integration
- [ ] New `/events/{eventId}/detail` endpoint with polls (keeps existing endpoint unchanged)
- [ ] `EventDetail` DTO for enhanced response
- [ ] Integration tests for all endpoints
- [ ] Performance testing for batch operations
- [ ] Documentation updates

### Phase 4: Polish
- [ ] Error handling and edge cases
- [ ] API documentation updates
- [ ] Frontend integration support
- [ ] Production deployment

## Future Considerations

### Event Structure Optimization

This design enables future migration to a pure single-table model:

1. **Phase 1**: Current implementation (parallel tables)
2. **Phase 2**: Migrate existing Events/Users/Invites to InviterTable format
3. **Phase 3**: Consolidate to single table, remove legacy tables

### Performance Optimizations

- **Caching**: Add Redis caching for frequently accessed polls
- **Pagination**: Implement pagination for events with many polls
- **Indexes**: Add additional GSIs if query patterns evolve

### Feature Extensions

- **Poll Templates**: Predefined poll types (time selection, yes/no, rating)
- **Anonymous Voting**: Option to hide voter identities
- **Poll Deadlines**: Automatic poll closure based on event timing
- **Vote Analytics**: Aggregated voting statistics and trends

## Conclusion

This design provides a robust, scalable foundation for the polling feature while maintaining compatibility with the existing system. The parallel table approach minimizes risk while the single-table design within `InviterTable` ensures optimal performance for polling operations.

The Key Factory pattern eliminates complexity concerns, and the Enhanced Client approach maintains code consistency throughout the application. With simplified authorization rules, the implementation can focus on core functionality and user experience.