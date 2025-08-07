# Database Architecture - Critical Performance Rules

## ⚠️ MANDATORY READ - Performance Critical

**This document MUST be read and understood before ANY database modifications or hangout retrievals.**

## Single Table Design Overview

The HangOut system uses a **single DynamoDB table** (`InviterTable`) with a composite key structure designed for maximum query efficiency:

```
| PK (Partition Key) | SK (Sort Key) | Purpose |
|-------------------|---------------|---------|
| GROUP#{GroupID}   | HANGOUT#{HangoutID} | **POINTER RECORD** - Contains summary data |
| EVENT#{HangoutID} | METADATA            | **CANONICAL RECORD** - Contains full hangout data |
```

## Pointer Records - The Performance Foundation

**POINTER RECORDS** are the key to performance. They contain **denormalized summary data** to avoid expensive lookups:

```java
// Pointer record structure
GROUP#{GroupID} | HANGOUT#{HangoutID}
{
  "title": "Weekly Coffee Chat",           // Denormalized from canonical
  "status": "CONFIRMED",                   // Denormalized from canonical  
  "hangoutTime": "2024-01-15T10:00:00Z",  // Denormalized from canonical
  "locationName": "Downtown Cafe",         // Denormalized from canonical
  "participantCount": 8                    // Denormalized from canonical
}
```

## CRITICAL RULE: HangoutSummaryDTO

🚨 **ABSOLUTE REQUIREMENT**: `HangoutSummaryDTO` MUST be populated **EXCLUSIVELY** from pointer records.

### ✅ CORRECT Pattern
```java
public List<HangoutSummaryDTO> getGroupHangouts(String groupId) {
    // Single query gets all pointer records
    List<HangoutPointer> pointers = groupRepository.findHangoutsByGroupId(groupId);
    
    // Transform pointer data directly to DTO - NO additional queries
    return pointers.stream()
        .map(pointer -> new HangoutSummaryDTO(
            pointer.getTitle(),           // From pointer
            pointer.getStatus(),          // From pointer  
            pointer.getHangoutTime(),     // From pointer
            pointer.getLocationName(),    // From pointer
            pointer.getParticipantCount() // From pointer
        ))
        .collect(Collectors.toList());
}
```

### ❌ FORBIDDEN Patterns
```java
// NEVER DO THIS - Destroys performance
public List<HangoutSummaryDTO> getGroupHangouts(String groupId) {
    List<HangoutPointer> pointers = groupRepository.findHangoutsByGroupId(groupId);
    
    return pointers.stream()
        .map(pointer -> {
            // ❌ PERFORMANCE VIOLATION - Full hangout retrieval
            Hangout fullHangout = hangoutRepository.findById(pointer.getHangoutId());
            return new HangoutSummaryDTO(fullHangout);
        })
        .collect(Collectors.toList());
}
```

## When You Need a Field Not on the Pointer

**IF** `HangoutSummaryDTO` needs a field that's not currently on the pointer record:

### ✅ CORRECT Solution
1. **Add the field to the pointer record structure**
2. **Update all pointer creation/update operations** to include the new field
3. **Use the field from the pointer record** in HangoutSummaryDTO

### ❌ NEVER Do This
- Don't retrieve the full hangout to get one missing field
- Don't make additional database queries per item
- Don't bypass the pointer system

## Update Pattern - Canonical First, Then Pointers

When updating hangout data that appears in pointers:

```java
@Override
public void updateHangoutTitle(String hangoutId, String newTitle) {
    // Step 1: Update canonical record first
    hangoutRepository.updateTitle(hangoutId, newTitle);
    
    // Step 2: Get associated groups from canonical record  
    Hangout hangout = hangoutRepository.findById(hangoutId);
    List<String> associatedGroups = hangout.getAssociatedGroups();
    
    // Step 3: Update ALL pointer records with new title
    for (String groupId : associatedGroups) {
        groupRepository.updateHangoutPointer(groupId, hangoutId, 
            Map.of("title", newTitle));
    }
}
```

## Query Patterns

### ✅ Efficient Patterns
- **Single query** to get multiple pointer records: `findHangoutsByGroupId()`
- **Item collection pattern** for full event details: `getEventDetailData()`
- **GSI queries** for user group memberships with denormalized group names

### ❌ Anti-Patterns
- N+1 queries (pointer → full hangout for each item)
- Multiple separate queries when one item collection query would work
- Retrieving full records when pointer data is sufficient

## Key Repository Methods to Understand

Before calling these methods, understand their purpose:

- `findUpcomingHangoutsForParticipant()` - Returns pointer records, not full hangouts
- `getEventDetailData()` - Single query for ALL event data via item collection pattern
- `findHangoutsByGroupId()` - Returns pointer records with summary data

## Performance Monitoring

The system includes built-in query performance tracking. Slow queries (>500ms) are logged as warnings. If you see performance warnings, verify you're using pointer records correctly.

---

**Remember: The pointer system's power comes from denormalization. Don't defeat it by retrieving full records when summary data exists.**