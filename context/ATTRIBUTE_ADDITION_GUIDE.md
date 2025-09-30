# Attribute Addition Guide

**Purpose**: Step-by-step checklist for adding simple attributes to User, Group, or Hangout entities with proper denormalization.

**Context**: This guide documents the pattern established when adding image paths (mainImagePath, backgroundImagePath) to the application. Future attribute additions should follow this exact pattern.

---

## Quick Reference Card

```
ATTRIBUTE ADDITION CHECKLIST

Models:
□ Canonical record + touch()
□ Pointer records + denorm comment
□ SeriesPointer static methods

DTOs:
□ Request DTOs + hasUpdates()
□ Response DTOs + all constructors
□ Feed DTOs (HangoutSummary, SeriesSummary)

Service:
□ Create: denormalize to pointers
□ Update: check changed, propagate
□ Add member: denormalize current values
□ Feed population methods

Repository:
□ Interface: add batch update method
□ Impl: 100-item batching, null handling

Testing:
□ Create with attribute
□ Update attribute
□ Verify persistence
□ Verify pointer denormalization
□ Verify feed display

Context Docs (if major):
□ *_CONTEXT.md files
```

---

## Phase 1: Models (10 min)

### Canonical Records (Source of Truth)

**Location**: `src/main/java/com/bbthechange/inviter/model/`

```java
// Pattern: Add field with touch() in setter
private String newAttribute;

public String getNewAttribute() {
    return newAttribute;
}

public void setNewAttribute(String newAttribute) {
    this.newAttribute = newAttribute;
    touch(); // CRITICAL: Update timestamp
}
```

**Apply to:**
- `User.java` - for user attributes
- `Group.java` - for group attributes
- `Hangout.java` - for hangout attributes
- `EventSeries.java` - for series attributes

### Pointer Records (Denormalized Copies)

**Decision Rule**: Does this data appear in feeds or list views? If yes, add to pointer.

```java
// Pattern: Same as canonical, but document denormalization source
private String newAttribute; // Denormalized from [Source]

public String getNewAttribute() {
    return newAttribute;
}

public void setNewAttribute(String newAttribute) {
    this.newAttribute = newAttribute;
    touch();
}
```

**Common Pointers:**

1. **HangoutPointer.java** - Group feed display, denormalized from Hangout
   - Add field with getter/setter

2. **SeriesPointer.java** - Group feed display, denormalized from EventSeries
   - Add field with getter/setter
   - **CRITICAL**: Update `fromEventSeries()` static method:
     ```java
     public static SeriesPointer fromEventSeries(EventSeries series, String groupId) {
         SeriesPointer pointer = new SeriesPointer(groupId, series.getSeriesId(), series.getSeriesTitle());
         pointer.setNewAttribute(series.getNewAttribute()); // ADD THIS
         // ... rest of method
         return pointer;
     }
     ```
   - **CRITICAL**: Update `syncWithEventSeries()` method:
     ```java
     public void syncWithEventSeries(EventSeries series) {
         setSeriesTitle(series.getSeriesTitle());
         setNewAttribute(series.getNewAttribute()); // ADD THIS
         // ... rest of method
     }
     ```

3. **GroupMembership.java** - User's groups list, denormalized from Group AND User
   - Pattern: `groupNewAttribute` and `userNewAttribute` if both Group and User have the attribute
   - If only one entity has it, use single field with source prefix

---

## Phase 2: DTOs (15 min)

### Request DTOs

**Location**: `src/main/java/com/bbthechange/inviter/dto/`

```java
// Pattern: Simple @Data class or explicit getters/setters
@Data
public class UpdateEntityRequest {
    private String existingField;
    private String newAttribute; // ADD THIS
}

// If hasUpdates() method exists, UPDATE IT:
public boolean hasUpdates() {
    return existingField != null || newAttribute != null; // ADD TO CONDITION
}
```

**Update these:**
- `UpdateProfileRequest.java` - User updates
- `CreateGroupRequest.java` - Group creation
- `UpdateGroupRequest.java` - Group updates
- `CreateHangoutRequest.java` - Hangout creation
- `UpdateHangoutRequest.java` - Hangout updates

### Response DTOs

```java
// Pattern: Add field, update ALL constructors
private String newAttribute;

// Constructor from entity
public EntityDTO(Entity entity) {
    this.id = entity.getId();
    this.newAttribute = entity.getNewAttribute(); // ADD THIS
}

// Constructor from pointer (if applicable)
public EntityDTO(EntityPointer pointer) {
    this.id = pointer.getId();
    this.newAttribute = pointer.getNewAttribute(); // ADD THIS
}
```

**Update these:**
- `GroupDTO.java` - Returns in group endpoints
- `UserProfileDTO.java` - Returns in profile endpoint (if exists)

### Feed DTOs (CRITICAL - Often Forgotten)

```java
// Pattern: Add field, update constructor from pointer
public class EntitySummaryDTO implements FeedItem {
    private String newAttribute; // Denormalized from pointer

    public EntitySummaryDTO(EntityPointer pointer) {
        this.id = pointer.getId();
        this.newAttribute = pointer.getNewAttribute(); // ADD THIS
    }

    // Add getter/setter
    public String getNewAttribute() {
        return newAttribute;
    }

    public void setNewAttribute(String newAttribute) {
        this.newAttribute = newAttribute;
    }
}
```

**Update these:**
- `HangoutSummaryDTO.java` - Hangout feed items
- `SeriesSummaryDTO.java` - Series feed items

---

## Phase 3: Service Layer (30 min)

**Golden Rule: "Canonical First, Pointers Second"**

### Pattern 1: Entity Creation (User, Group, Hangout)

```java
public EntityDTO createEntity(CreateEntityRequest request, String userId) {
    // 1. Create canonical record
    Entity entity = new Entity(request.getName());
    entity.setNewAttribute(request.getNewAttribute()); // ADD THIS

    // 2. Save canonical FIRST
    Entity saved = repository.save(entity);

    // 3. Create pointer records with denormalized data
    EntityPointer pointer = new EntityPointer(saved.getId());
    pointer.setNewAttribute(saved.getNewAttribute()); // DENORMALIZE

    return new EntityDTO(saved);
}
```

**Example from HangoutServiceImpl.createHangout()**:
```java
// When creating HangoutPointer records
for (String groupId : request.getAssociatedGroups()) {
    HangoutPointer pointer = new HangoutPointer(groupId, hangout.getHangoutId(), hangout.getTitle());
    pointer.setStatus("ACTIVE");
    pointer.setMainImagePath(hangout.getMainImagePath()); // Denormalize
    pointers.add(pointer);
}
```

### Pattern 2: Entity Update with Denormalization

```java
public EntityDTO updateEntity(String id, UpdateEntityRequest request, String userId) {
    Entity entity = repository.findById(id).orElseThrow();

    boolean attributeChanged = false;

    // 1. Check if changed (avoid unnecessary propagation)
    if (request.getNewAttribute() != null &&
        !request.getNewAttribute().equals(entity.getNewAttribute())) {
        entity.setNewAttribute(request.getNewAttribute());
        attributeChanged = true;
    }

    // 2. Save canonical FIRST
    Entity saved = repository.save(entity);

    // 3. Propagate to pointers if changed
    if (attributeChanged) {
        repository.updatePointerNewAttribute(id, saved.getNewAttribute());
        logger.info("Updated entity {} newAttribute and synchronized pointers", id);
    }

    return new EntityDTO(saved);
}
```

**Example from GroupServiceImpl.updateGroup()**:
```java
boolean imagePathsChanged = false;
if (request.getMainImagePath() != null) {
    group.setMainImagePath(request.getMainImagePath());
    imagePathsChanged = true;
    updated = true;
}

Group savedGroup = groupRepository.save(group);

if (imagePathsChanged) {
    groupRepository.updateMembershipGroupImagePaths(
        groupId,
        savedGroup.getMainImagePath(),
        savedGroup.getBackgroundImagePath()
    );
    logger.info("Updated group {} image paths and synchronized membership records", groupId);
}
```

### Pattern 3: Adding Members/Creating Pointers

```java
public void addMember(String groupId, String userId) {
    // 1. Get canonical records for denormalization
    Group group = groupRepository.findById(groupId).orElseThrow();
    User user = userRepository.findById(userId).orElseThrow();

    // 2. Create pointer with CURRENT denormalized data
    GroupMembership membership = new GroupMembership(groupId, userId);
    membership.setGroupNewAttribute(group.getNewAttribute()); // FROM GROUP
    membership.setUserNewAttribute(user.getNewAttribute());   // FROM USER

    repository.addMember(membership);
}
```

**Example from GroupServiceImpl.addMember()**:
```java
GroupMembership membership = new GroupMembership(groupId, userId, group.getGroupName());
membership.setRole(GroupRole.MEMBER);

// Denormalize group image paths to membership
membership.setGroupMainImagePath(group.getMainImagePath());
membership.setGroupBackgroundImagePath(group.getBackgroundImagePath());

// Denormalize user image path to membership
membership.setUserMainImagePath(userToAdd.getMainImagePath());

groupRepository.addMember(membership);
```

### Pattern 4: Feed Population

When creating feed DTOs, ensure you populate the new attribute from the pointer:

```java
// In GroupServiceImpl.createEntitySummaryDTO() or similar
EntitySummaryDTO dto = new EntitySummaryDTO();
dto.setId(pointer.getId());
dto.setNewAttribute(pointer.getNewAttribute()); // ADD THIS LINE

return dto;
```

**Example from GroupServiceImpl.createSeriesSummaryDTO()**:
```java
SeriesSummaryDTO dto = new SeriesSummaryDTO();
dto.setSeriesId(seriesPointer.getSeriesId());
dto.setSeriesTitle(seriesPointer.getSeriesTitle());
dto.setMainImagePath(seriesPointer.getMainImagePath()); // Populate from pointer

// ... rest of method
return dto;
```

### Pattern 5: Series Creation (Special Case)

When creating EventSeries from a Hangout, copy attributes from the primary hangout:

```java
// In EventSeriesServiceImpl
EventSeries newSeries = new EventSeries();
newSeries.setSeriesId(seriesId);
newSeries.setSeriesTitle(existingHangout.getTitle());
newSeries.setPrimaryEventId(existingHangoutId);
newSeries.setMainImagePath(existingHangout.getMainImagePath()); // Copy from primary

// When creating HangoutPointers for series parts
newPointer.setSeriesId(seriesId);
newPointer.setMainImagePath(newHangout.getMainImagePath()); // Denormalize
```

**Files to Update:**
- `UserService.java` - User profile updates
- `GroupServiceImpl.java` - Group CRUD + feed population
- `HangoutServiceImpl.java` - Hangout CRUD
- `EventSeriesServiceImpl.java` - Series CRUD (copies from primary hangout)

---

## Phase 4: Repository Layer (20 min)

### Add Interface Methods

**File**: `GroupRepository.java` (or relevant repository interface)

```java
/**
 * Update denormalized [attribute] in all [pointer] records for [entity].
 * Called when [entity] [attribute] changes to maintain data consistency.
 */
void updatePointerNewAttribute(String entityId, String newAttributeValue);
```

**Example from GroupRepository.java**:
```java
/**
 * Update denormalized group image paths in all membership records for a group.
 * Called when group images change to maintain data consistency.
 */
void updateMembershipGroupImagePaths(String groupId, String mainImagePath, String backgroundImagePath);

/**
 * Update denormalized user image path in all membership records for a user.
 * Called when user mainImagePath changes to maintain data consistency.
 */
void updateMembershipUserImagePath(String userId, String mainImagePath);
```

### Implement Batch Updates

**File**: `PolymorphicGroupRepositoryImpl.java` (or relevant repository implementation)

```java
@Override
public void updatePointerNewAttribute(String entityId, String newAttributeValue) {
    queryTracker.trackQuery("UpdatePointerNewAttribute", TABLE_NAME, () -> {
        try {
            // 1. Get all affected pointer records
            List<PointerRecord> pointers = findPointersByEntityId(entityId);
            if (pointers.isEmpty()) {
                logger.debug("No pointers to update for entity {}", entityId);
                return null;
            }

            // 2. Batch in groups of 100 (DynamoDB TransactWriteItems limit)
            int batchSize = 100;
            String timestamp = Instant.now().toString();

            for (int i = 0; i < pointers.size(); i += batchSize) {
                List<PointerRecord> batch = pointers.subList(
                    i, Math.min(i + batchSize, pointers.size())
                );

                // 3. Build TransactWriteItems for batch
                List<TransactWriteItem> transactItems = batch.stream()
                    .map(pointer -> TransactWriteItem.builder()
                        .update(Update.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointer.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointer.getSk()).build()
                            ))
                            .updateExpression("SET newAttribute = :value, updatedAt = :timestamp")
                            .expressionAttributeValues(Map.of(
                                ":value", AttributeValue.builder()
                                    .s(newAttributeValue != null ? newAttributeValue : "")
                                    .build(),
                                ":timestamp", AttributeValue.builder().s(timestamp).build()
                            ))
                            .build())
                        .build())
                    .collect(Collectors.toList());

                // 4. Execute batch
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();

                dynamoDbClient.transactWriteItems(transactRequest);
            }

            logger.info("Updated {} pointer records for entity {}", pointers.size(), entityId);
        } catch (DynamoDbException e) {
            throw new RepositoryException("Failed to update pointer newAttribute", e);
        }
        return null;
    });
}
```

**CRITICAL Notes:**
- Batch size 100 (DynamoDB TransactWriteItems limit)
- Handle nulls: `value != null ? value : ""` (DynamoDB rejects null AttributeValues)
- Update timestamp in same transaction
- Use existing query methods to find affected records
- All-or-nothing: If one operation fails, entire batch rolls back

**Example from PolymorphicGroupRepositoryImpl.java**:
```java
@Override
public void updateMembershipUserImagePath(String userId, String mainImagePath) {
    queryTracker.trackQuery("UpdateMembershipUserImagePath", TABLE_NAME, () -> {
        try {
            List<GroupMembership> memberships = findGroupsByUserId(userId);
            if (memberships.isEmpty()) {
                return null;
            }

            int batchSize = 100;
            String timestamp = Instant.now().toString();

            for (int i = 0; i < memberships.size(); i += batchSize) {
                List<GroupMembership> batch = memberships.subList(
                    i, Math.min(i + batchSize, memberships.size())
                );

                List<TransactWriteItem> transactItems = batch.stream()
                    .map(membership -> TransactWriteItem.builder()
                        .update(Update.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder()
                                    .s(InviterKeyFactory.getGroupPk(membership.getGroupId()))
                                    .build(),
                                "sk", AttributeValue.builder()
                                    .s(InviterKeyFactory.getUserSk(userId))
                                    .build()
                            ))
                            .updateExpression("SET userMainImagePath = :mainImagePath, updatedAt = :timestamp")
                            .expressionAttributeValues(Map.of(
                                ":mainImagePath", AttributeValue.builder()
                                    .s(mainImagePath != null ? mainImagePath : "")
                                    .build(),
                                ":timestamp", AttributeValue.builder().s(timestamp).build()
                            ))
                            .build())
                        .build())
                    .collect(Collectors.toList());

                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();

                dynamoDbClient.transactWriteItems(transactRequest);
            }
        } catch (DynamoDbException e) {
            throw new RepositoryException("Failed to update membership user image path", e);
        }
        return null;
    });
}
```

---

## Phase 5: Controllers (5 min)

Only update if adding new endpoints or modifying response structure:

```java
// Pattern: Return new field in response map
@PutMapping("/entity/{id}")
public ResponseEntity<?> updateEntity(@PathVariable String id,
                                      @RequestBody UpdateEntityRequest request) {
    Entity updated = service.updateEntity(id, request, getCurrentUserId());

    Map<String, Object> response = new HashMap<>();
    response.put("message", "Entity updated successfully");
    response.put("newAttribute", updated.getNewAttribute()); // ADD IF NEEDED

    return ResponseEntity.ok(response);
}
```

**Example from ProfileController.java**:
```java
@PutMapping
public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest request) {
    UUID userId = getCurrentUserId();
    User updatedUser = userService.updateProfile(userId, request);

    Map<String, Object> response = new HashMap<>();
    response.put("message", "Profile updated successfully");
    response.put("displayName", updatedUser.getDisplayName());
    response.put("mainImagePath", updatedUser.getMainImagePath()); // Added for new attribute

    return ResponseEntity.ok(response);
}
```

---

## Common Gotchas

### 1. Missing Feed DTOs
**Symptom**: Attribute appears in detail endpoint but not in feed
**Fix**: Update `HangoutSummaryDTO`, `SeriesSummaryDTO`, and feed population methods in `GroupServiceImpl`

### 2. Lambda Variable Not Final
**Error**: "local variables referenced from a lambda expression must be final or effectively final"
**Fix**: Introduce `final` variable before lambda:
```java
final String finalUserId = userId;
repository.findById(id)
    .orElseThrow(() -> new Exception("Not found: " + finalUserId));
```

### 3. DynamoDB Null Strings
**Error**: DynamoDB rejects null AttributeValues
**Fix**: `AttributeValue.builder().s(value != null ? value : "").build()`

### 4. Forgetting touch()
**Symptom**: `updatedAt` timestamp not updating
**Fix**: Call `touch()` in every setter

### 5. Wrong JSON Field Names
**Problem**: Test sends `"isPublic": false` but API expects `"public": false`
**Fix**: Check DTO field names, not getter names (Jackson uses field names)

### 6. Missing SeriesPointer Static Methods
**Symptom**: New series don't have new attribute
**Fix**: Update both `fromEventSeries()` AND `syncWithEventSeries()`

### 7. Wrong Feed Endpoint
**Problem**: Querying `/groups/{id}/feed-items` instead of `/groups/{id}/feed`
**Fix**: Use correct endpoint: `GET /groups/{groupId}/feed`

### 8. Wrong TimeInfo Format
**Problem**: Sending string `"timeInput": "tomorrow at 3pm"` instead of structured TimeInfo
**Fix**: Send proper TimeInfo object:
```json
"timeInfo": {
  "startTime": "2025-10-01T15:00:00Z",
  "periodGranularity": "day"
}
```

---

## Integration Testing

### Template Script

Use `scripts/example-attribute-integration-test.sh` as base template. Key structure:

```bash
#!/bin/bash
BASE_URL="http://localhost:8080"
PHONE="+19285251044"
PASSWORD="mypass2"

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
pass_test() {
    echo -e "${GREEN}✓ PASS${NC}: $1"
    ((TESTS_PASSED++))
}

fail_test() {
    echo -e "${RED}✗ FAIL${NC}: $1"
    echo -e "${RED}  Response: $2${NC}"
    ((TESTS_FAILED++))
}

# 1. Login
JWT_TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"phoneNumber\":\"$PHONE\",\"password\":\"$PASSWORD\"}" | \
  grep -o '"accessToken":"[^"]*' | sed 's/"accessToken":"//')

# 2. Create entity with new attribute
ENTITY_RESPONSE=$(curl -s -X POST "$BASE_URL/entities" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test Entity",
    "newAttribute": "test-value"
  }')

# 3. Verify attribute persisted
if echo "$ENTITY_RESPONSE" | grep -q "test-value"; then
    pass_test "Create entity with newAttribute"
else
    fail_test "Create entity with newAttribute" "$ENTITY_RESPONSE"
fi

# 4. Update attribute
curl -s -X PATCH "$BASE_URL/entities/$ENTITY_ID" \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newAttribute": "updated-value"}'

# 5. Verify denormalization to pointer
FEED=$(curl -s "$BASE_URL/groups/$GROUP_ID/feed" \
  -H "Authorization: Bearer $JWT_TOKEN")

if echo "$FEED" | grep -q "updated-value"; then
    pass_test "Attribute denormalized to pointer"
else
    fail_test "Attribute denormalized to pointer" "$FEED"
fi

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Failed: $TESTS_FAILED${NC}"

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed.${NC}"
    exit 1
fi
```

### Critical Test Formats

```bash
# TimeInfo (NOT string)
TOMORROW=$(date -u -v+1d '+%Y-%m-%dT15:00:00Z' 2>/dev/null || \
           date -u -d 'tomorrow 15:00:00' '+%Y-%m-%dT%H:%M:%SZ')

"timeInfo": {
  "startTime": "$TOMORROW",
  "periodGranularity": "day"
}

# Correct endpoints
GET /groups/{id}/feed  # NOT /feed-items

# JSON field names
"public": false        # NOT "isPublic"
"initialHangoutId"     # NOT "existingHangoutId" (for series creation)
"newMemberRequest"     # NOT "newHangout" (for series creation)
```

### Test Coverage

A comprehensive test should verify:
1. **Create** entity with new attribute
2. **Verify persistence** - refetch and check attribute
3. **Update** attribute value
4. **Verify update persistence**
5. **Denormalization to pointers** - check pointer records have updated value
6. **Feed display** - verify attribute appears in feed responses
7. **Batch updates** - if applicable (e.g., user attribute affecting all group memberships)

---

## File Location Map

```
Models:           src/main/java/com/bbthechange/inviter/model/
DTOs:             src/main/java/com/bbthechange/inviter/dto/
Services:         src/main/java/com/bbthechange/inviter/service/impl/
Repositories:     src/main/java/com/bbthechange/inviter/repository/
Controllers:      src/main/java/com/bbthechange/inviter/controller/
Tests:            scripts/ (integration test scripts)
Context Docs:     context/
```

---

## Context Documentation Updates

If the attribute addition is significant (affects core functionality or multiple entities), update relevant context files:

**Files to consider:**
- `context/HANGOUT_CRUD_CONTEXT.md` - If hangout attributes changed
- `context/GROUP_CRUD_CONTEXT.md` - If group attributes changed
- `context/USER_PROFILES_CONTEXT.md` - If user attributes changed
- `context/EVENT_SERIES_CONTEXT.md` - If series attributes changed

**Update sections:**
- Model field descriptions
- Denormalized field lists
- API request/response examples (if structure changed)

---

## Estimated Timeline

For a simple single-attribute addition:
- Phase 1 (Models): 10 minutes
- Phase 2 (DTOs): 15 minutes
- Phase 3 (Service): 30 minutes
- Phase 4 (Repository): 20 minutes
- Phase 5 (Controllers): 5 minutes
- Testing: 30 minutes
- Context docs: 10 minutes

**Total: ~2 hours** for a straightforward attribute addition with full test coverage.

Complex attributes (e.g., nested objects, computed values, cross-entity dependencies) may take longer.
