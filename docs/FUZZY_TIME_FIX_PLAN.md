# Fuzzy Time Implementation - Corrective Action Plan

This document outlines the identified issues in the recent fuzzy time implementation and provides a detailed plan with code examples to correct the implementation, bringing it in line with the original design.

## 1. Summary of Issues

The current implementation deviates from the agreed-upon plan in several critical ways, leading to incorrect data storage, inefficient data retrieval, and a failure to meet the core feature requirements.

1.  **Loss of Fuzzy Time Context**: The original user input (e.g., "tomorrow evening") is immediately converted to a concrete `LocalDateTime` and then discarded. The original `timeInfo` object is never stored, making it impossible to display relative time on the client.
2.  **Improper Data Modeling**: A `Map<String, String>` was used for the `timeInfo` object instead of a strongly-typed DTO, sacrificing type safety and code clarity.
3.  **GSI Not Populated**: The `GSI1PK` and `GSI1SK` attributes on the `HangoutPointer` records are never set. This renders the `EntityTimeIndex` completely non-functional, as it contains no data to query.
4.  **Massively Inefficient Read Path**: The `HangoutServiceImpl.convertToSummaryDTO` method makes a separate database call to fetch the full details for every single hangout in a list, creating a severe N+1 query performance bottleneck.

---

## 2. Corrective Action Plan

### Step 1: Introduce a `TimeInput` DTO

First, replace all instances of `Map<String, String>` with a proper `TimeInput` DTO. This has already been started in your local changes and is the correct first step.

**`TimeInput.java`**
```java
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeInput {
    private String periodGranularity;
    private String periodStart; // ISO 8601 with timezone
    private String startTime;   // ISO 8601 with timezone
    private String endTime;     // ISO 8601 with timezone
}
```
*This class should be used in `CreateHangoutRequest`, `UpdateHangoutRequest`, and the `Hangout` model.*

### Step 2: Fix the `FuzzyTimeService`

The service should **only** be responsible for converting a `TimeInput` object into canonical timestamps. It should not modify any domain objects.

**`FuzzyTimeService.java`**
```java
@Service
public class FuzzyTimeService {

    // A simple result object to hold the calculated timestamps
    public static class TimeConversionResult {
        public final Long startTimestamp;
        public final Long endTimestamp;

        public TimeConversionResult(Long start, Long end) {
            this.startTimestamp = start;
            this.endTimestamp = end;
        }
    }

    public TimeConversionResult convert(TimeInput timeInfo) {
        if (timeInfo == null) {
            return new TimeConversionResult(null, null);
        }

        if (timeInfo.getStartTime() != null) {
            // Logic for exact times
            Instant start = Instant.parse(timeInfo.getStartTime());
            Instant end = timeInfo.getEndTime() != null ? Instant.parse(timeInfo.getEndTime()) : start;
            return new TimeConversionResult(start.getEpochSecond(), end.getEpochSecond());
        } else if (timeInfo.getPeriodGranularity() != null && timeInfo.getPeriodStart() != null) {
            // Logic for fuzzy times
            Instant periodStart = Instant.parse(timeInfo.getPeriodStart());
            long durationSeconds = getDurationForGranularity(timeInfo.getPeriodGranularity());
            Instant periodEnd = periodStart.plusSeconds(durationSeconds);
            return new TimeConversionResult(periodStart.getEpochSecond(), periodEnd.getEpochSecond());
        }

        return new TimeConversionResult(null, null);
    }

    private long getDurationForGranularity(String granularity) {
        switch (granularity.toLowerCase()) {
            case "morning": return 4 * 3600; // 4 hours
            case "afternoon": return 5 * 3600; // 5 hours
            case "evening": return 4 * 3600; // 4 hours
            case "night": return 6 * 3600; // 6 hours
            case "day": return 24 * 3600; // 24 hours
            case "weekend": return 48 * 3600; // 48 hours
            default: return 0;
        }
    }
}
```

### Step 3: Fix the `HangoutService` Write Path

This is the most critical part of the fix. The `createHangout` method must be updated to correctly populate the canonical and pointer records, including the GSI attributes.

**`HangoutServiceImpl.java` - `createHangout` method**
```java
@Override
public Hangout createHangout(CreateHangoutRequest request, String creatorId) {
    // 1. Convert TimeInput to canonical timestamps
    FuzzyTimeService.TimeConversionResult timeResult = fuzzyTimeService.convert(request.getTimeInput());

    // 2. Create the canonical Hangout record
    Hangout hangout = new Hangout();
    hangout.setHangoutId(UUID.randomUUID().toString());
    hangout.setTitle(request.getTitle());
    hangout.setDescription(request.getDescription());
    hangout.setCreator(creatorId);
    
    // *** Store ALL time information on the canonical record ***
    hangout.setTimeInput(request.getTimeInput()); // The original user input
    hangout.setStartTimestamp(timeResult.startTimestamp);
    hangout.setEndTimestamp(timeResult.endTimestamp);

    // ... set other hangout properties (visibility, etc.)

    // 3. Create Pointer Records for each associated group
    List<HangoutPointer> pointers = new ArrayList<>();
    for (String groupId : request.getAssociatedGroups()) {
        HangoutPointer pointer = new HangoutPointer();
        pointer.setGroupId(groupId);
        pointer.setHangoutId(hangout.getHangoutId());
        pointer.setTitle(hangout.getTitle()); // Denormalize title
        
        // *** This is the crucial step to populate the GSI ***
        pointer.setGSI1PK("GROUP#" + groupId);
        if (timeResult.startTimestamp != null) {
            pointer.setGSI1SK("T#" + timeResult.startTimestamp);
        }
        
        pointers.add(pointer);
    }

    // 4. Save everything in a single atomic transaction
    hangoutRepository.saveHangoutAndPointers(hangout, pointers);

    return hangout;
}
```

### Step 4: Fix the `HangoutService` Read Path

To fix the N+1 query problem, the `convertToSummaryDTO` method must be removed or refactored to **not** make extra database calls. The `GroupFeed` response should be built directly from the pointer records returned by the GSI query.

**`HangoutServiceImpl.java` - `getGroupFeed` method (Conceptual)**
```java
@Override
public GroupFeedDTO getGroupFeed(String groupId) {
    // 1. Query the GSI to get all future hangout pointers for the group
    List<HangoutPointer> pointers = hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#" + groupId);

    // 2. Convert pointers directly to DTOs - NO extra database calls
    List<HangoutSummaryDTO> hangoutSummaries = pointers.stream()
        .map(this::convertPointerToSummaryDTO)
        .collect(Collectors.toList());

    // The GroupFeedDTO should be structured to handle a single list of summaries
    // The client will be responsible for separating them if needed.
    return new GroupFeedDTO(groupId, hangoutSummaries);
}

// New conversion method that does NOT fetch from the database
private HangoutSummaryDTO convertPointerToSummaryDTO(HangoutPointer pointer) {
    HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer);
    
    // *** This part needs the `timeInfo` to be denormalized onto the pointer ***
    // To fully implement the desired API response, the `timeInfo` map/object
    // should also be a denormalized attribute on the HangoutPointer model.
    // If it is, you can set it directly:
    // dto.setTimeInfo(pointer.getTimeInput()); 
    
    return dto;
}
```

**Note on Denormalization:** For the `timeInfo` object to be available in the group feed without extra lookups, the `timeInfo` map from the canonical `Hangout` record must be denormalized (copied) to the `HangoutPointer` record when it is created or updated. This is a key principle of single-table design for performant reads.

---

## 3. Next Steps

1.  **Refactor DTOs and Models**: Replace `Map<String, String>` with the `TimeInput` class.
2.  **Refactor `FuzzyTimeService`**: Implement the service as described above to return a `TimeConversionResult`.
3.  **Refactor `HangoutService.createHangout`**: Implement the write logic to correctly populate the canonical record and the GSI attributes on the pointer records.
4.  **Refactor `HangoutService.getGroupFeed`**: Remove the N+1 query pattern and build the response directly from the GSI query results.
5.  **Update `HangoutPointer`**: Add the `timeInfo` field to the `HangoutPointer` model to support efficient reads.
6.  **Write/Update Unit Tests**: Ensure all new and refactored logic is covered by unit tests.