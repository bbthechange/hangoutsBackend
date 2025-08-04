# Carpool Feature Design Document

## Overview

This document outlines the design and implementation approach for adding a carpooling feature to the Inviter application. The carpooling system allows event hosts to enable carpooling, users to offer rides with available seats, other users to claim seats in those cars, and users to register as needing a ride.

## Design Principles

- **Atomic Seat Management**: Prevent overbooking through transactional seat claiming
- **Single Query Efficiency**: Load all carpool data with existing event query
- **Natural Key Structure**: Use driver IDs as car identifiers for intuitive APIs
- **Integrated Metadata**: Extend existing event metadata rather than separate items
- **Plus-One Support**: Allow users to bring guests who take additional seats

## Architecture Overview

### Data Integration Strategy

The carpool feature extends the existing `InviterTable` single-table design by adding new item types that share the same partition key (`EVENT#{EventID}`) as existing event data. This ensures all event-related information (basic event data, polls, carpools) can be loaded with a single DynamoDB query.

```
Single Query: EVENT#{EventID}
├── METADATA (event + polls + carpool flags)
├── POLL#{PollID} (existing polling data)
├── CAR#{DriverID} (carpool cars)
├── CAR#{DriverID}#RIDER#{RiderID} (seat claims)
└── NEEDS_RIDE#{UserID} (ride requests)
```

## Data Model

### InviterTable Extensions

The carpool feature adds new item types to the existing `InviterTable`:

| PK (Partition Key) | SK (Sort Key) | Data Attributes | Description |
|---|---|---|---|
| `EVENT#{EventID}` | `METADATA` | `...existing..., carpoolEnabled: true` | Extends existing event metadata with carpool flag |
| `EVENT#{EventID}` | `CAR#{DriverID}` | driverName, totalCapacity, availableSeats | Car offered by a driver |
| `EVENT#{EventID}` | `CAR#{DriverID}#RIDER#{RiderID}` | riderName, plusOneCount | User who claimed a seat in a car |
| `EVENT#{EventID}` | `NEEDS_RIDE#{UserID}` | userName | User who needs a ride |

### Key Design Decisions

#### 1. Driver-Centric Car Keys
Using `CAR#{DriverID}` instead of `CAR#{CarID}` provides several advantages:
- **Natural Relationships**: Direct mapping between driver and their car
- **Intuitive APIs**: `/cars/{driverId}` endpoints are more logical
- **Simplified Queries**: No need to map car IDs back to drivers
- **Business Logic**: Most operations are "driver's car" focused

#### 2. Integrated Event Metadata
The `carpoolEnabled` flag is added directly to the existing event `METADATA` item rather than creating a separate carpool metadata item:
```json
{
  "pk": "EVENT#12345",
  "sk": "METADATA",
  "name": "Summer BBQ",
  "description": "...",
  "carpoolEnabled": true
}
```

#### 3. Hierarchical Rider Structure
Riders are nested under cars using the pattern `CAR#{DriverID}#RIDER#{RiderID}`, enabling efficient queries for all riders in a specific car.

## Implementation Components

### 1. Key Management

#### InviterKeyFactory Extensions

```java
public final class InviterKeyFactory {
    // ... existing methods ...
    
    // Carpool Keys
    public static String getCarSk(String driverId) {
        return "CAR#" + driverId;
    }
    
    public static String getRiderSk(String driverId, String riderId) {
        return "CAR#" + driverId + "#RIDER#" + riderId;
    }
    
    public static String getNeedsRideSk(String userId) {
        return "NEEDS_RIDE#" + userId;
    }
    
    // Query Prefixes
    public static String getCarPrefix(String driverId) {
        return "CAR#" + driverId;
    }
    
    public static String getAllCarsPrefix() {
        return "CAR#";
    }
    
    public static String getNeedsRidePrefix() {
        return "NEEDS_RIDE#";
    }
    
    // Parsers
    public static String parseDriverIdFromCarSk(String carSk) {
        if (!carSk.startsWith("CAR#")) {
            throw new IllegalArgumentException("Invalid car SK format: " + carSk);
        }
        return carSk.substring(4); // Remove "CAR#" prefix
    }
    
    public static ParsedRiderSk parseRiderSk(String riderSk) {
        String[] parts = riderSk.split("#");
        if (parts.length != 4 || !parts[0].equals("CAR") || !parts[2].equals("RIDER")) {
            throw new IllegalArgumentException("Invalid rider SK format: " + riderSk);
        }
        return new ParsedRiderSk(parts[1], parts[3]); // driverId, riderId
    }
}

public class ParsedRiderSk {
    private final String driverId;
    private final String riderId;
    
    public ParsedRiderSk(String driverId, String riderId) {
        this.driverId = driverId;
        this.riderId = riderId;
    }
    
    // Getters
    public String getDriverId() { return driverId; }
    public String getRiderId() { return riderId; }
}
```

### 2. Data Models

#### Car Model

```java
@DynamoDbBean
public class Car extends BaseItem {
    private String driverId;
    private String driverName;
    private int totalCapacity;
    private int availableSeats;
    private String route;
    private LocalDateTime departureTime;
    private String notes;
    private LocalDateTime createdAt;
    
    public Car() {}
    
    public Car(String eventId, String driverId, String driverName, int totalCapacity) {
        this.setPk(InviterKeyFactory.getEventPk(eventId));
        this.setSk(InviterKeyFactory.getCarSk(driverId));
        this.driverId = driverId;
        this.driverName = driverName;
        this.totalCapacity = totalCapacity;
        this.availableSeats = totalCapacity - 1; // Driver takes one seat
        this.createdAt = LocalDateTime.now();
    }
    
    public Car(String eventId, String driverId, String driverName, int totalCapacity, 
               String route, LocalDateTime departureTime, String notes) {
        this(eventId, driverId, driverName, totalCapacity);
        this.route = route;
        this.departureTime = departureTime;
        this.notes = notes;
    }
    
    // Getters and setters
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    
    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
    
    public int getAvailableSeats() { return availableSeats; }
    public void setAvailableSeats(int availableSeats) { this.availableSeats = availableSeats; }
    
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    
    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

#### Rider Model

```java
@DynamoDbBean
public class Rider extends BaseItem {
    private String driverId;
    private String riderId;
    private String riderName;
    private Integer plusOneCount; // Optional - defaults to 0
    private LocalDateTime joinedAt;
    
    public Rider() {}
    
    public Rider(String eventId, String driverId, String riderId, String riderName) {
        this.setPk(InviterKeyFactory.getEventPk(eventId));
        this.setSk(InviterKeyFactory.getRiderSk(driverId, riderId));
        this.driverId = driverId;
        this.riderId = riderId;
        this.riderName = riderName;
        this.plusOneCount = 0;
        this.joinedAt = LocalDateTime.now();
    }
    
    public Rider(String eventId, String driverId, String riderId, String riderName, int plusOneCount) {
        this(eventId, driverId, riderId, riderName);
        this.plusOneCount = plusOneCount;
    }
    
    // Calculate total seats occupied by this rider
    public int getTotalSeatsOccupied() {
        return 1 + (plusOneCount != null ? plusOneCount : 0);
    }
    
    // Getters and setters
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    
    public String getRiderId() { return riderId; }
    public void setRiderId(String riderId) { this.riderId = riderId; }
    
    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }
    
    public Integer getPlusOneCount() { return plusOneCount; }
    public void setPlusOneCount(Integer plusOneCount) { this.plusOneCount = plusOneCount; }
    
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
```

#### NeedsRide Model

```java
@DynamoDbBean
public class NeedsRide extends BaseItem {
    private String userId;
    private String userName;
    private String notes;
    private Integer plusOneCount;
    private LocalDateTime requestedAt;
    
    public NeedsRide() {}
    
    public NeedsRide(String eventId, String userId, String userName) {
        this.setPk(InviterKeyFactory.getEventPk(eventId));
        this.setSk(InviterKeyFactory.getNeedsRideSk(userId));
        this.userId = userId;
        this.userName = userName;
        this.plusOneCount = 0;
        this.requestedAt = LocalDateTime.now();
    }
    
    public NeedsRide(String eventId, String userId, String userName, String notes, int plusOneCount) {
        this(eventId, userId, userName);
        this.notes = notes;
        this.plusOneCount = plusOneCount;
    }
    
    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public Integer getPlusOneCount() { return plusOneCount; }
    public void setPlusOneCount(Integer plusOneCount) { this.plusOneCount = plusOneCount; }
    
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }
}
```

### 3. Repository Layer

#### InviterRepository Extensions

```java
@Repository
public class InviterRepository {
    // ... existing methods ...
    
    // Car Operations
    public Car saveCar(Car car) {
        inviterTable.putItem(car);
        return car;
    }
    
    public Optional<Car> getCarByDriver(String eventId, String driverId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        String sk = InviterKeyFactory.getCarSk(driverId);
        
        BaseItem item = inviterTable.getItem(Key.builder()
            .partitionValue(pk)
            .sortValue(sk)
            .build());
            
        return Optional.ofNullable(item).map(i -> (Car) i);
    }
    
    public List<Car> getCarsForEvent(String eventId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        
        return inviterTable.query(r -> r
            .keyCondition(k -> k.partitionValue(pk)
                              .sortBeginsWith(InviterKeyFactory.getAllCarsPrefix())))
            .items()
            .stream()
            .filter(item -> !item.getSk().contains("#RIDER#")) // Exclude rider items
            .map(item -> (Car) item)
            .collect(Collectors.toList());
    }
    
    public void deleteCar(String eventId, String driverId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        String skPrefix = InviterKeyFactory.getCarPrefix(driverId);
        
        // 1. Query for all items to delete (car + all riders)
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
    
    // Rider Operations
    public void claimSeat(String eventId, String driverId, String riderId, String riderName, int seatsNeeded) {
        String carPk = InviterKeyFactory.getEventPk(eventId);
        String carSk = InviterKeyFactory.getCarSk(driverId);
        
        Rider rider = new Rider(eventId, driverId, riderId, riderName, seatsNeeded - 1); // -1 because base rider counts as 1
        
        // Atomic transaction: Create rider + update available seats
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
            .transactItems(
                // Create rider item
                TransactWriteItem.builder()
                    .put(Put.builder()
                        .tableName("InviterTable")
                        .item(convertToAttributeValueMap(rider))
                        .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)") // Prevent duplicate claims
                        .build())
                    .build(),
                    
                // Update car available seats
                TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName("InviterTable")
                        .key(Map.of(
                            "PK", AttributeValue.builder().s(carPk).build(),
                            "SK", AttributeValue.builder().s(carSk).build()
                        ))
                        .updateExpression("SET availableSeats = availableSeats - :seats")
                        .conditionExpression("availableSeats >= :seats")
                        .expressionAttributeValues(Map.of(
                            ":seats", AttributeValue.builder().n(String.valueOf(seatsNeeded)).build()
                        ))
                        .build())
                    .build()
            )
            .build();
            
        try {
            dynamoDbClient.transactWriteItems(request);
        } catch (TransactionCanceledException e) {
            // Handle condition failures (not enough seats or duplicate claim)
            throw new SeatClaimException("Unable to claim seat: " + e.getMessage(), e);
        }
    }
    
    public void releaseSeat(String eventId, String driverId, String riderId) {
        String carPk = InviterKeyFactory.getEventPk(eventId);
        String carSk = InviterKeyFactory.getCarSk(driverId);
        String riderSk = InviterKeyFactory.getRiderSk(driverId, riderId);
        
        // First, get the rider to know how many seats to release
        BaseItem riderItem = inviterTable.getItem(Key.builder()
            .partitionValue(carPk)
            .sortValue(riderSk)
            .build());
            
        if (riderItem == null) {
            throw new IllegalArgumentException("Rider not found");
        }
        
        Rider rider = (Rider) riderItem;
        int seatsToRelease = rider.getTotalSeatsOccupied();
        
        // Atomic transaction: Delete rider + update available seats
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
            .transactItems(
                // Delete rider item
                TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName("InviterTable")
                        .key(Map.of(
                            "PK", AttributeValue.builder().s(carPk).build(),
                            "SK", AttributeValue.builder().s(riderSk).build()
                        ))
                        .build())
                    .build(),
                    
                // Update car available seats
                TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName("InviterTable")
                        .key(Map.of(
                            "PK", AttributeValue.builder().s(carPk).build(),
                            "SK", AttributeValue.builder().s(carSk).build()
                        ))
                        .updateExpression("SET availableSeats = availableSeats + :seats")
                        .expressionAttributeValues(Map.of(
                            ":seats", AttributeValue.builder().n(String.valueOf(seatsToRelease)).build()
                        ))
                        .build())
                    .build()
            )
            .build();
            
        dynamoDbClient.transactWriteItems(request);
    }
    
    public List<Rider> getRidersForCar(String eventId, String driverId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        String skPrefix = InviterKeyFactory.getCarPrefix(driverId) + "#RIDER#";
        
        return inviterTable.query(r -> r
            .keyCondition(k -> k.partitionValue(pk).sortBeginsWith(skPrefix)))
            .items()
            .stream()
            .map(item -> (Rider) item)
            .collect(Collectors.toList());
    }
    
    // Needs Ride Operations
    public NeedsRide saveNeedsRide(NeedsRide needsRide) {
        inviterTable.putItem(needsRide);
        return needsRide;
    }
    
    public void deleteNeedsRide(String eventId, String userId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        String sk = InviterKeyFactory.getNeedsRideSk(userId);
        
        inviterTable.deleteItem(Key.builder()
            .partitionValue(pk)
            .sortValue(sk)
            .build());
    }
    
    public List<NeedsRide> getNeedsRideForEvent(String eventId) {
        String pk = InviterKeyFactory.getEventPk(eventId);
        
        return inviterTable.query(r -> r
            .keyCondition(k -> k.partitionValue(pk)
                              .sortBeginsWith(InviterKeyFactory.getNeedsRidePrefix())))
            .items()
            .stream()
            .map(item -> (NeedsRide) item)
            .collect(Collectors.toList());
    }
    
    // Helper method to convert objects to DynamoDB AttributeValue map
    private Map<String, AttributeValue> convertToAttributeValueMap(BaseItem item) {
        // Implementation depends on your existing conversion logic
        // This would typically use the Enhanced Client's internal conversion
        return TableSchema.fromBean(item.getClass()).itemToMap(item, true);
    }
}

// Custom exception for seat claiming failures
public class SeatClaimException extends RuntimeException {
    public SeatClaimException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 4. Service Layer

#### CarpoolService

```java
@Service
public class CarpoolService {
    @Autowired
    private InviterRepository inviterRepository;
    
    @Autowired
    private InviteService inviteService;
    
    @Autowired
    private UserService userService;
    
    public Car offerRide(String eventId, String driverId, String driverName, int totalCapacity, 
                        String route, LocalDateTime departureTime, String notes, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized to offer rides for this event");
        }
        
        // Verify the requesting user is the driver
        if (!driverId.equals(requestingUserId)) {
            throw new SecurityException("Users can only offer their own cars");
        }
        
        // Check if driver already has a car for this event
        Optional<Car> existingCar = inviterRepository.getCarByDriver(eventId, driverId);
        if (existingCar.isPresent()) {
            throw new IllegalStateException("Driver already has a car for this event");
        }
        
        // Validate capacity
        if (totalCapacity < 2) {
            throw new IllegalArgumentException("Car must have at least 2 seats (including driver)");
        }
        
        Car car = new Car(eventId, driverId, driverName, totalCapacity, route, departureTime, notes);
        return inviterRepository.saveCar(car);
    }
    
    public void cancelRide(String eventId, String driverId, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized for this event");
        }
        
        // Verify the requesting user is the driver
        if (!driverId.equals(requestingUserId)) {
            throw new SecurityException("Users can only cancel their own cars");
        }
        
        // Verify car exists
        Optional<Car> car = inviterRepository.getCarByDriver(eventId, driverId);
        if (car.isEmpty()) {
            throw new IllegalArgumentException("Car not found");
        }
        
        // Delete car and all associated riders
        inviterRepository.deleteCar(eventId, driverId);
    }
    
    public void claimSeat(String eventId, String driverId, String riderId, String riderName, 
                         int plusOneCount, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized for this event");
        }
        
        // Verify the requesting user is the rider
        if (!riderId.equals(requestingUserId)) {
            throw new SecurityException("Users can only claim seats for themselves");
        }
        
        // Verify driver is not trying to ride in their own car
        if (driverId.equals(riderId)) {
            throw new IllegalArgumentException("Driver cannot ride in their own car");
        }
        
        // Verify car exists
        Optional<Car> car = inviterRepository.getCarByDriver(eventId, driverId);
        if (car.isEmpty()) {
            throw new IllegalArgumentException("Car not found");
        }
        
        // Calculate total seats needed (rider + plus ones)
        int totalSeatsNeeded = 1 + plusOneCount;
        
        // Attempt atomic seat claim
        try {
            inviterRepository.claimSeat(eventId, driverId, riderId, riderName, totalSeatsNeeded);
        } catch (SeatClaimException e) {
            if (e.getMessage().contains("availableSeats")) {
                throw new IllegalStateException("Not enough available seats in the car");
            } else if (e.getMessage().contains("attribute_not_exists")) {
                throw new IllegalStateException("User has already claimed a seat in this car");
            } else {
                throw e;
            }
        }
    }
    
    public void releaseSeat(String eventId, String driverId, String riderId, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized for this event");
        }
        
        // Allow both the rider and the driver to remove riders
        if (!riderId.equals(requestingUserId) && !driverId.equals(requestingUserId)) {
            throw new SecurityException("Only the rider or driver can remove this seat claim");
        }
        
        inviterRepository.releaseSeat(eventId, driverId, riderId);
    }
    
    public NeedsRide requestRide(String eventId, String userId, String userName, String notes, 
                                int plusOneCount, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized for this event");
        }
        
        // Verify the requesting user is the one needing a ride
        if (!userId.equals(requestingUserId)) {
            throw new SecurityException("Users can only request rides for themselves");
        }
        
        NeedsRide needsRide = new NeedsRide(eventId, userId, userName, notes, plusOneCount);
        return inviterRepository.saveNeedsRide(needsRide);
    }
    
    public void cancelRideRequest(String eventId, String userId, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized for this event");
        }
        
        // Verify the requesting user is the one canceling their request
        if (!userId.equals(requestingUserId)) {
            throw new SecurityException("Users can only cancel their own ride requests");
        }
        
        inviterRepository.deleteNeedsRide(eventId, userId);
    }
    
    public CarpoolData getCarpoolDataForEvent(String eventId, String requestingUserId) {
        // Verify user is invited to event
        if (!inviteService.isUserInvitedToEvent(UUID.fromString(requestingUserId), UUID.fromString(eventId))) {
            throw new SecurityException("User not authorized for this event");
        }
        
        List<Car> cars = inviterRepository.getCarsForEvent(eventId);
        List<NeedsRide> rideRequests = inviterRepository.getNeedsRideForEvent(eventId);
        
        // Get riders for each car
        Map<String, List<Rider>> ridersByCarId = new HashMap<>();
        for (Car car : cars) {
            List<Rider> riders = inviterRepository.getRidersForCar(eventId, car.getDriverId());
            ridersByCarId.put(car.getDriverId(), riders);
        }
        
        return new CarpoolData(cars, ridersByCarId, rideRequests);
    }
}

// DTO for comprehensive carpool data
public class CarpoolData {
    private final List<Car> cars;
    private final Map<String, List<Rider>> ridersByDriverId;
    private final List<NeedsRide> rideRequests;
    
    public CarpoolData(List<Car> cars, Map<String, List<Rider>> ridersByDriverId, List<NeedsRide> rideRequests) {
        this.cars = cars;
        this.ridersByDriverId = ridersByDriverId;
        this.rideRequests = rideRequests;
    }
    
    // Getters
    public List<Car> getCars() { return cars; }
    public Map<String, List<Rider>> getRidersByDriverId() { return ridersByDriverId; }
    public List<NeedsRide> getRideRequests() { return rideRequests; }
}
```

### 5. Controller Layer

#### EventController Extensions

```java
@RestController
@RequestMapping("/events")
public class EventController {
    // ... existing methods ...
    
    @Autowired
    private CarpoolService carpoolService;
    
    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateEventSettings(
            @PathVariable UUID id, 
            @RequestBody UpdateEventSettingsRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Validate that user is invited to this event (simplified authorization)
        if (!inviteService.isUserInvitedToEvent(userId, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        try {
            // Update event settings (including carpoolEnabled flag)
            eventService.updateEventSettings(id, request);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Event settings updated successfully");
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}

// DTO for event settings updates
public class UpdateEventSettingsRequest {
    private Boolean carpoolEnabled;
    
    public Boolean getCarpoolEnabled() { return carpoolEnabled; }
    public void setCarpoolEnabled(Boolean carpoolEnabled) { this.carpoolEnabled = carpoolEnabled; }
}
```

#### CarpoolController

```java
@RestController
@RequestMapping("/events/{eventId}/carpool")
public class CarpoolController {
    @Autowired
    private CarpoolService carpoolService;
    
    // Car Management
    @PostMapping("/cars")
    public ResponseEntity<Map<String, String>> offerRide(
            @PathVariable String eventId,
            @RequestBody OfferRideRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            Car car = carpoolService.offerRide(
                eventId, 
                userId, // Driver is the requesting user
                request.getDriverName(),
                request.getTotalCapacity(),
                request.getRoute(),
                request.getDepartureTime(),
                request.getNotes(),
                userId
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Ride offered successfully");
            response.put("driverId", car.getDriverId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @DeleteMapping("/cars/{driverId}")
    public ResponseEntity<Map<String, String>> cancelRide(
            @PathVariable String eventId,
            @PathVariable String driverId,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            carpoolService.cancelRide(eventId, driverId, userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Ride canceled successfully");
            
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    // Seat Management
    @PostMapping("/riders")
    public ResponseEntity<Map<String, String>> claimSeat(
            @PathVariable String eventId,
            @RequestBody ClaimSeatRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            carpoolService.claimSeat(
                eventId,
                request.getDriverId(),
                userId, // Rider is the requesting user 
                request.getRiderName(),
                request.getPlusOneCount() != null ? request.getPlusOneCount() : 0,
                userId
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Seat claimed successfully");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @DeleteMapping("/cars/{driverId}/riders/{riderId}")
    public ResponseEntity<Map<String, String>> releaseSeat(
            @PathVariable String eventId,
            @PathVariable String driverId,
            @PathVariable String riderId,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            carpoolService.releaseSeat(eventId, driverId, riderId, userId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Seat released successfully");
            
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
    
    // Ride Requests
    @PostMapping("/needs-ride")
    public ResponseEntity<Map<String, String>> requestRide(
            @PathVariable String eventId,
            @RequestBody RequestRideRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            carpoolService.requestRide(
                eventId,
                userId, // User is requesting ride for themselves
                request.getUserName(),
                request.getNotes(),
                request.getPlusOneCount() != null ? request.getPlusOneCount() : 0,
                userId
            );
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Ride request created successfully");
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
    
    @DeleteMapping("/needs-ride/{userId}")
    public ResponseEntity<Map<String, String>> cancelRideRequest(
            @PathVariable String eventId,
            @PathVariable String userId,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = (String) httpRequest.getAttribute("userId");
        if (requestingUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        try {
            carpoolService.cancelRideRequest(eventId, userId, requestingUserId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Ride request canceled successfully");
            
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}

// Request DTOs
public class OfferRideRequest {
    private String driverName;
    private Integer totalCapacity;
    private String route;
    private LocalDateTime departureTime;
    private String notes;
    
    // Getters and setters
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    
    public Integer getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(Integer totalCapacity) { this.totalCapacity = totalCapacity; }
    
    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }
    
    public LocalDateTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

public class ClaimSeatRequest {
    private String driverId;
    private String riderName;
    private Integer plusOneCount;
    
    // Getters and setters
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    
    public String getRiderName() { return riderName; }
    public void setRiderName(String riderName) { this.riderName = riderName; }
    
    public Integer getPlusOneCount() { return plusOneCount; }
    public void setPlusOneCount(Integer plusOneCount) { this.plusOneCount = plusOneCount; }
}

public class RequestRideRequest {
    private String userName;
    private String notes;
    private Integer plusOneCount;
    
    // Getters and setters
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public Integer getPlusOneCount() { return plusOneCount; }
    public void setPlusOneCount(Integer plusOneCount) { this.plusOneCount = plusOneCount; }
}
```

## API Endpoints

### Complete API Specification

| Method | Endpoint | Description | Request Body | Response |
|--------|----------|-------------|--------------|----------|
| `PATCH` | `/events/{eventId}` | Enable/disable carpooling | `{"carpoolEnabled": true}` | `{"message": "success"}` |
| `POST` | `/events/{eventId}/carpool/cars` | Offer ride | `{"driverName": "string", "totalCapacity": 4, "route": "string", "departureTime": "ISO", "notes": "string"}` | `{"message": "success", "driverId": "uuid"}` |
| `DELETE` | `/events/{eventId}/carpool/cars/{driverId}` | Cancel ride offer | - | `{"message": "success"}` |
| `POST` | `/events/{eventId}/carpool/riders` | Claim seat in car | `{"driverId": "uuid", "riderName": "string", "plusOneCount": 1}` | `{"message": "success"}` |
| `DELETE` | `/events/{eventId}/carpool/cars/{driverId}/riders/{riderId}` | Release seat | - | `{"message": "success"}` |
| `POST` | `/events/{eventId}/carpool/needs-ride` | Request ride | `{"userName": "string", "notes": "string", "plusOneCount": 0}` | `{"message": "success"}` |
| `DELETE` | `/events/{eventId}/carpool/needs-ride/{userId}` | Cancel ride request | - | `{"message": "success"}` |

### Enhanced Event Detail Endpoint

The existing `/events/{eventId}/detail` endpoint will be extended to include carpool data alongside polls:

```java
@GetMapping("/{id}/detail")
public ResponseEntity<EventDetail> getEventDetail(@PathVariable UUID id, HttpServletRequest request) {
    String userIdStr = (String) request.getAttribute("userId");
    UUID userId = UUID.fromString(userIdStr);
    
    // 1. Get event data (existing logic)
    Optional<Event> eventOpt = eventService.getEventForUser(id, userId);
    if (eventOpt.isEmpty()) {
        return ResponseEntity.notFound().build();
    }
    
    // 2. Get polls data (existing logic)
    List<Poll> polls = pollService.getPollsForEvent(id.toString());
    
    // 3. Get carpool data (new logic)
    CarpoolData carpoolData = null;
    if (eventOpt.get().isCarpoolEnabled()) {
        carpoolData = carpoolService.getCarpoolDataForEvent(id.toString(), userIdStr);
    }
    
    // 4. Merge into response DTO
    EventDetail response = new EventDetail(eventOpt.get(), polls, carpoolData);
    
    return ResponseEntity.ok(response);
}
```

## Business Rules

### Seat Management Logic

#### Atomic Seat Claiming
- **Transaction**: Creating a rider record and decrementing available seats must be atomic
- **Conditions**: Car must have sufficient available seats
- **Plus-One Support**: `plusOneCount` field allows guests without user accounts
- **Seat Calculation**: `totalSeatsOccupied = 1 + plusOneCount`

#### Seat Release Logic
- **Transaction**: Deleting rider record and incrementing available seats must be atomic
- **Authorization**: Both rider and driver can remove riders
- **Calculation**: Seats released = rider's `totalSeatsOccupied`

#### Business Validations
- **Driver Restrictions**: Drivers cannot ride in their own cars
- **Capacity Limits**: Cars must have at least 2 total seats (including driver)
- **Duplicate Prevention**: Users cannot claim multiple seats in the same car
- **Event Authorization**: All operations require event invitation

### Data Consistency

#### Available Seats Management
The `availableSeats` field on Car entities is a calculated field that must be kept in sync:
- **Initial Value**: `totalCapacity - 1` (driver takes one seat)
- **On Seat Claim**: `availableSeats -= rider.getTotalSeatsOccupied()`
- **On Seat Release**: `availableSeats += rider.getTotalSeatsOccupied()`

#### Cascading Operations
- **Delete Car**: Removes car and all associated rider records
- **Delete Event**: Would remove all carpool data (handled by existing event deletion logic)

## Query Patterns

### Single Query Event Loading
```java
// Get all event data including carpool information
List<BaseItem> allEventData = inviterTable.query(r -> r
    .keyCondition(k -> k.partitionValue("EVENT#12345")))
    .items()
    .stream()
    .collect(Collectors.toList());

// Parse items by SK pattern
Event event = null;
List<Poll> polls = new ArrayList<>();
List<Car> cars = new ArrayList<>();
List<Rider> riders = new ArrayList<>();
List<NeedsRide> rideRequests = new ArrayList<>();

for (BaseItem item : allEventData) {
    String sk = item.getSk();
    
    if (sk.equals("METADATA")) {
        event = (Event) item;
    } else if (sk.startsWith("POLL#") && !sk.contains("#OPTION#") && !sk.contains("#VOTE#")) {
        polls.add((Poll) item);
    } else if (sk.startsWith("CAR#") && !sk.contains("#RIDER#")) {
        cars.add((Car) item);
    } else if (sk.contains("#RIDER#")) {
        riders.add((Rider) item);
    } else if (sk.startsWith("NEEDS_RIDE#")) {
        rideRequests.add((NeedsRide) item);
    }
}
```

### Specific Car Queries
```java
// Get all data for a specific car (car + riders)
List<BaseItem> carData = inviterTable.query(r -> r
    .keyCondition(k -> k.partitionValue("EVENT#12345")
                       .sortBeginsWith("CAR#user-456")))
    .items()
    .stream()
    .collect(Collectors.toList());
```

## Error Handling

### Common Error Scenarios

#### Seat Claiming Failures
- **Insufficient Seats**: Return `400 Bad Request` with message "Not enough available seats"
- **Duplicate Claims**: Return `400 Bad Request` with message "User has already claimed a seat in this car"
- **Driver Self-Ride**: Return `400 Bad Request` with message "Driver cannot ride in their own car"

#### Authorization Failures
- **Unauthorized Event Access**: Return `403 Forbidden`
- **Cross-User Operations**: Return `403 Forbidden` with message "Users can only perform actions for themselves"

#### Resource Not Found
- **Missing Car**: Return `404 Not Found` with message "Car not found"
- **Missing Rider**: Return `404 Not Found` with message "Rider not found"

### Exception Handling Strategy

```java
@ControllerAdvice
public class CarpoolExceptionHandler {
    
    @ExceptionHandler(SeatClaimException.class)
    public ResponseEntity<Map<String, String>> handleSeatClaimException(SeatClaimException e) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Seat claim failed");
        response.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurityException(SecurityException e) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Access denied");
        response.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}
```

## Testing Strategy

### Unit Tests

#### Key Factory Tests
```java
@Test
public void testCarKeyGeneration() {
    String carSk = InviterKeyFactory.getCarSk("user-123");
    assertEquals("CAR#user-123", carSk);
}

@Test
public void testRiderKeyGeneration() {
    String riderSk = InviterKeyFactory.getRiderSk("driver-456", "rider-789");
    assertEquals("CAR#driver-456#RIDER#rider-789", riderSk);
}

@Test
public void testRiderSkParsing() {
    ParsedRiderSk parsed = InviterKeyFactory.parseRiderSk("CAR#driver-456#RIDER#rider-789");
    assertEquals("driver-456", parsed.getDriverId());
    assertEquals("rider-789", parsed.getRiderId());
}
```

#### Service Layer Tests
```java
@Test
public void testAtomicSeatClaiming() {
    // Test successful seat claim
    carpoolService.claimSeat(eventId, driverId, riderId, riderName, 0, requestingUserId);
    
    // Verify rider was created and car seats decremented
    Optional<Car> car = inviterRepository.getCarByDriver(eventId, driverId);
    assertEquals(2, car.get().getAvailableSeats()); // Assuming car started with 3 available seats
}

@Test
public void testSeatClaimingOverbooking() {
    // Attempt to claim more seats than available
    assertThrows(IllegalStateException.class, () -> {
        carpoolService.claimSeat(eventId, driverId, riderId, riderName, 5, requestingUserId);
    });
}
```

### Integration Tests

#### Repository Transaction Tests
```java
@Test
@DynamoDbLocalTest
public void testAtomicSeatClaimTransaction() {
    // Setup: Create car with 2 available seats
    Car car = new Car(eventId, driverId, driverName, 3);
    inviterRepository.saveCar(car);
    
    // Test: Claim 2 seats atomically
    inviterRepository.claimSeat(eventId, driverId, riderId, riderName, 2);
    
    // Verify: Both rider created and seats decremented
    Optional<Car> updatedCar = inviterRepository.getCarByDriver(eventId, driverId);
    assertEquals(0, updatedCar.get().getAvailableSeats());
    
    List<Rider> riders = inviterRepository.getRidersForCar(eventId, driverId);
    assertEquals(1, riders.size());
    assertEquals(1, riders.get(0).getTotalSeatsOccupied());
}

@Test
@DynamoDbLocalTest  
public void testSeatClaimFailsWhenInsufficientSeats() {
    // Setup: Car with only 1 available seat
    Car car = new Car(eventId, driverId, driverName, 2);
    inviterRepository.saveCar(car);
    
    // Test: Attempt to claim 2 seats
    assertThrows(SeatClaimException.class, () -> {
        inviterRepository.claimSeat(eventId, driverId, riderId, riderName, 2);
    });
    
    // Verify: No rider created, seats unchanged
    Optional<Car> car2 = inviterRepository.getCarByDriver(eventId, driverId);
    assertEquals(1, car2.get().getAvailableSeats());
    
    List<Rider> riders = inviterRepository.getRidersForCar(eventId, driverId);
    assertEquals(0, riders.size());
}
```

#### API Endpoint Tests
```java
@Test
public void testOfferRideEndpoint() throws Exception {
    OfferRideRequest request = new OfferRideRequest();
    request.setDriverName("John Doe");
    request.setTotalCapacity(4);
    request.setRoute("Downtown to Venue");
    
    mockMvc.perform(post("/events/" + eventId + "/carpool/cars")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request))
            .header("Authorization", "Bearer " + jwtToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.message").value("Ride offered successfully"))
            .andExpect(jsonPath("$.driverId").exists());
}
```

### Test Data Setup

```java
// Helper method for creating test carpool data
public class CarpoolTestDataFactory {
    
    public static Car createTestCar(String eventId, String driverId, int capacity) {
        return new Car(eventId, driverId, "Test Driver", capacity, 
                      "Test Route", LocalDateTime.now().plusHours(2), "Test notes");
    }
    
    public static Rider createTestRider(String eventId, String driverId, String riderId, int plusOnes) {
        return new Rider(eventId, driverId, riderId, "Test Rider", plusOnes);
    }
    
    public static NeedsRide createTestRideRequest(String eventId, String userId) {
        return new NeedsRide(eventId, userId, "Test User", "Need a ride", 0);
    }
}
```

## Implementation Timeline

### Phase 1: Foundation
- [ ] Extend `InviterKeyFactory` with carpool key methods
- [ ] Create `Car`, `Rider`, `NeedsRide` models with DynamoDB annotations
- [ ] Add carpool repository methods to `InviterRepository`
- [ ] Extend event metadata to include `carpoolEnabled` flag

### Phase 2: Core Features
- [ ] Implement `CarpoolService` with business logic and authorization
- [ ] Create atomic seat claiming/releasing transaction logic
- [ ] Implement `CarpoolController` with all endpoints
- [ ] Add carpool exception handling

### Phase 3: Integration
- [ ] Extend `/events/{eventId}/detail` endpoint to include carpool data
- [ ] Update `EventDetail` DTO to include carpool information
- [ ] Add carpool data parsing to existing event loading logic
- [ ] Integration tests for all endpoints

### Phase 4: Polish
- [ ] Comprehensive error handling and validation
- [ ] Performance testing for atomic transactions
- [ ] API documentation updates
- [ ] Frontend integration support

## Future Considerations

### Enhanced Features

#### Route Matching
- **Smart Suggestions**: Suggest riders to drivers based on route similarity
- **Geographic Queries**: Add location-based GSI for route optimization
- **Route Preferences**: Allow riders to specify pickup/dropoff preferences

#### Real-time Updates
- **Push Notifications**: Notify users when seats become available
- **Live Tracking**: Share driver location with riders
- **Chat Integration**: In-app messaging between drivers and riders

#### Advanced Booking
- **Recurring Rides**: Support for regular event carpools


## Conclusion

This carpool feature design leverages DynamoDB single-table design principles to provide efficient, atomic seat management while integrating seamlessly with the existing polling system. The driver-centric key structure and natural data loading patterns ensure optimal performance and maintainability.

Key strengths of this design:
- **Atomic Operations**: Prevents overbooking through DynamoDB transactions
- **Single Query Efficiency**: All event data loads with one request
- **Natural Key Structure**: Intuitive APIs and data relationships
- **Integrated Design**: Consistent with existing polling patterns

The implementation provides a solid foundation for basic carpooling while maintaining flexibility for future enhancements.