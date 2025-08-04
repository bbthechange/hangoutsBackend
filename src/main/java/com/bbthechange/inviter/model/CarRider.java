package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * CarRider entity for the InviterTable.
 * Represents a person who has claimed a seat in a car for carpooling.
 * 
 * Key Pattern: PK = EVENT#{EventID}, SK = CAR#{DriverID}#RIDER#{RiderID}
 */
@DynamoDbBean
public class CarRider extends BaseItem {
    
    private String eventId;
    private String driverId;
    private String riderId;
    private String riderName;       // Denormalized for display
    private int plusOneCount;       // Additional passengers this rider is bringing
    private String notes;           // Special requests/notes from rider
    
    // Default constructor for DynamoDB
    public CarRider() {
        super();
    }

    /**
     * Create a new car rider record.
     */
    public CarRider(String eventId, String driverId, String riderId, String riderName) {
        super();
        this.eventId = eventId;
        this.driverId = driverId;
        this.riderId = riderId;
        this.riderName = riderName;
        this.plusOneCount = 0; // Default to just the rider
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getCarRiderSk(driverId, riderId));
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
    
    public String getRiderId() {
        return riderId;
    }
    
    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }
    
    public String getRiderName() {
        return riderName;
    }
    
    public void setRiderName(String riderName) {
        this.riderName = riderName;
        touch();
    }
    
    public int getPlusOneCount() {
        return plusOneCount;
    }
    
    public void setPlusOneCount(int plusOneCount) {
        this.plusOneCount = plusOneCount;
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
     * Total number of seats this rider is taking (rider + plus ones).
     */
    public int getTotalSeatsNeeded() {
        return 1 + plusOneCount;
    }
}