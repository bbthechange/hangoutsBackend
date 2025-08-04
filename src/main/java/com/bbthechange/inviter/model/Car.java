package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Car entity for the InviterTable.
 * Represents a car offered for carpooling to an event/hangout.
 * 
 * Key Pattern: PK = EVENT#{EventID}, SK = CAR#{DriverID}
 */
@DynamoDbBean
public class Car extends BaseItem {
    
    private String eventId;
    private String driverId;
    private String driverName;      // Denormalized for display
    private int totalCapacity;      // Total seats including driver
    private int availableSeats;     // Remaining available seats
    private String notes;           // Additional info from driver
    
    // Default constructor for DynamoDB
    public Car() {
        super();
    }

    /**
     * Create a new car offering for an event.
     */
    public Car(String eventId, String driverId, String driverName, int totalCapacity) {
        super();
        this.eventId = eventId;
        this.driverId = driverId;
        this.driverName = driverName;
        this.totalCapacity = totalCapacity;
        this.availableSeats = totalCapacity - 1; // Driver takes one seat
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getCarSk(driverId));
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getDriverId() {
        return driverId;
    }
    
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }
    
    public String getDriverName() {
        return driverName;
    }
    
    public void setDriverName(String driverName) {
        this.driverName = driverName;
        touch();
    }
    
    public int getTotalCapacity() {
        return totalCapacity;
    }
    
    public void setTotalCapacity(int totalCapacity) {
        this.totalCapacity = totalCapacity;
        touch();
    }
    
    public int getAvailableSeats() {
        return availableSeats;
    }
    
    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
        touch();
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
        touch();
    }
    
    /**
     * Check if the car has available seats.
     */
    public boolean hasAvailableSeats() {
        return availableSeats > 0;
    }
}