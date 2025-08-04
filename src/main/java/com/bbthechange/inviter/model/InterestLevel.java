package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * InterestLevel entity for the InviterTable.
 * Represents a user's interest level/attendance status for an event/hangout.
 * 
 * Key Pattern: PK = EVENT#{EventID}, SK = ATTENDANCE#{UserID}
 */
@DynamoDbBean
public class InterestLevel extends BaseItem {
    
    private String eventId;
    private String userId;
    private String userName;        // Denormalized for display
    private String status;          // "GOING", "INTERESTED", "NOT_GOING"
    private String notes;           // Optional notes from the user
    
    // Default constructor for DynamoDB
    public InterestLevel() {
        super();
    }

    /**
     * Create a new interest level record.
     */
    public InterestLevel(String eventId, String userId, String userName, String status) {
        super();
        this.eventId = eventId;
        this.userId = userId;
        this.userName = userName;
        this.status = status;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getAttendanceSk(userId));
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
        touch();
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        touch();
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
        touch();
    }
}