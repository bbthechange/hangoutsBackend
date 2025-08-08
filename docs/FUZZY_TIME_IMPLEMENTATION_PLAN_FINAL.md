# FUZZY TIME FIX - IMPLEMENTATION PLAN

## Critical Issues to Fix

1. **Create TimeInput DTO** - No strongly-typed TimeInput class exists
2. **Fix FuzzyTimeService Interface** - Wrong method signature and return type
3. **Add timeInfo to HangoutPointer** - Missing denormalized field for efficient reads
4. **Eliminate N+1 Queries** - convertToSummaryDTO fetches full hangout for every pointer

## STEP 1: Create TimeInput DTO (NEW FILE)

**File**: `src/main/java/com/bbthechange/inviter/dto/TimeInput.java`
```java
package com.bbthechange.inviter.dto;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

@DynamoDbBean
public class TimeInput {
    private String periodGranularity;
    private String periodStart; // ISO 8601 with timezone
    private String startTime;   // ISO 8601 with timezone  
    private String endTime;     // ISO 8601 with timezone
    
    public TimeInput() {}
    
    public TimeInput(String periodGranularity, String periodStart, String startTime, String endTime) {
        this.periodGranularity = periodGranularity;
        this.periodStart = periodStart;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    @DynamoDbAttribute("periodGranularity")
    public String getPeriodGranularity() { return periodGranularity; }
    public void setPeriodGranularity(String periodGranularity) { this.periodGranularity = periodGranularity; }
    
    @DynamoDbAttribute("periodStart")
    public String getPeriodStart() { return periodStart; }
    public void setPeriodStart(String periodStart) { this.periodStart = periodStart; }
    
    @DynamoDbAttribute("startTime")
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    
    @DynamoDbAttribute("endTime")
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}
```

## STEP 2: Fix FuzzyTimeService Interface

**File**: `FuzzyTimeService.java` - REPLACE COMPLETELY:
```java
package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.TimeInfo;

public interface FuzzyTimeService {
    
    /**
     * Convert TimeInput to canonical timestamps.
     */
    TimeConversionResult convert(TimeInput timeInfo);
    
    /**
     * Result object containing canonical timestamps.
     */
    class TimeConversionResult {
        public final Long startTimestamp;
        public final Long endTimestamp;
        
        public TimeConversionResult(Long startTimestamp, Long endTimestamp) {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
        }
    }
}
```

## STEP 3: Add timeInfo to HangoutPointer (CRITICAL FOR PERFORMANCE)

**File**: `HangoutPointer.java` - ADD THESE FIELDS:
```java
private TimeInput timeInfo;     // Denormalized for efficient reads
private Long startTimestamp;     // Denormalized for GSI sorting
private Long endTimestamp;       // Denormalized for completeness

@DynamoDbAttribute("timeInfo")
public TimeInput getTimeInput() {
    return timeInfo;
}

public void setTimeInput(TimeInput timeInfo) {
    this.timeInfo = timeInfo;
    touch();
}

@DynamoDbAttribute("startTimestamp") 
public Long getStartTimestamp() {
    return startTimestamp;
}

public void setStartTimestamp(Long startTimestamp) {
    this.startTimestamp = startTimestamp;
    touch();
}

@DynamoDbAttribute("endTimestamp")
public Long getEndTimestamp() {
    return endTimestamp;
}

public void setEndTimestamp(Long endTimestamp) {
    this.endTimestamp = endTimestamp;
    touch();
}
```

## STEP 4: Fix HangoutServiceImpl - Denormalize timeInfo to Pointers

**File**: `HangoutServiceImpl.java:84-99` - REPLACE WITH:
```java
// Create hangout pointer records for each associated group
if (request.getAssociatedGroups() != null) {
    for (String groupId : request.getAssociatedGroups()) {
        HangoutPointer pointer = new HangoutPointer(groupId, hangout.getHangoutId(), hangout.getTitle());
        pointer.setStatus("ACTIVE");
        pointer.setLocationName(getLocationName(hangout.getLocation()));
        pointer.setParticipantCount(0);
        
        // *** CRITICAL: Denormalize ALL time information ***
        pointer.setTimeInput(hangout.getTimeInput());           // For API response
        pointer.setStartTimestamp(hangout.getStartTimestamp()); // For GSI sorting  
        pointer.setEndTimestamp(hangout.getEndTimestamp());     // For completeness
        
        // Set GSI fields for EntityTimeIndex
        pointer.setGSI1PK("GROUP#" + groupId);
        if (hangout.getStartTimestamp() != null) {
            pointer.setGSI1SK("T#" + hangout.getStartTimestamp());
        }
        
        groupRepository.saveHangoutPointer(pointer);
    }
}
```

## STEP 5: Eliminate N+1 Queries (CRITICAL FOR PERFORMANCE)

**File**: `HangoutServiceImpl.java:544-560` - REPLACE WITH:
```java
private HangoutSummaryDTO convertToSummaryDTO(HangoutPointer pointer) {
    HangoutSummaryDTO summary = new HangoutSummaryDTO(pointer);
    
    // *** NO DATABASE CALL - Use denormalized timeInfo ***
    if (pointer.getTimeInput() != null) {
        Map<String, String> timeInfo = formatTimeInfoForResponse(pointer.getTimeInput());
        summary.setTimeInfo(timeInfo);
    }
    
    return summary;
}
```

## STEP 6: Update formatTimeInfoForResponse Signature

**File**: `HangoutServiceImpl.java:562` - UPDATE METHOD:
```java
private Map<String, String> formatTimeInfoForResponse(TimeInput timeInfo) {
    if (timeInfo == null) {
        return null;
    }
    
    Map<String, String> timeInfo = new HashMap<>();
    
    // For fuzzy time: only return periodGranularity and periodStart in UTC
    if (timeInfo.getPeriodGranularity() != null) {
        timeInfo.put("periodGranularity", timeInfo.getPeriodGranularity());
        if (timeInfo.getPeriodStart() != null) {
            timeInfo.put("periodStart", convertToUtcIsoString(timeInfo.getPeriodStart()));
        }
    } 
    // For exact time: only return startTime and endTime in UTC
    else if (timeInfo.getStartTime() != null) {
        timeInfo.put("startTime", convertToUtcIsoString(timeInfo.getStartTime()));
        if (timeInfo.getEndTime() != null) {
            timeInfo.put("endTime", convertToUtcIsoString(timeInfo.getEndTime()));
        }
    }
    
    return timeInfo;
}
```

## STEP 7: Update All Model References

Replace `Map<String, String> timeInfo` with `TimeInput timeInfo` in:
- `Hangout.java`
- `CreateHangoutRequest.java`
- `UpdateHangoutRequest.java`
- `HangoutDetailDTO.java`

## Critical Success Metrics

After implementation:
1. ✅ **Zero N+1 queries**: `convertToSummaryDTO` makes no database calls
2. ✅ **Proper API format**: Fuzzy times return only `periodGranularity`/`periodStart` in UTC
3. ✅ **Type safety**: Strongly-typed `TimeInput` throughout codebase
4. ✅ **GSI functional**: EntityTimeIndex works with proper timestamp keys

**Most Critical**: Steps 3, 4, and 5 eliminate the performance bottleneck by denormalizing timeInfo to pointer records.