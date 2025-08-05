package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;

/**
 * Hangout pointer entity for the InviterTable.
 * Represents a denormalized pointer to a hangout/event for group feed efficiency.
 * Contains key event details to avoid additional lookups when displaying group feeds.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = HANGOUT#{HangoutID}
 */
@DynamoDbBean
public class HangoutPointer extends BaseItem {
    
    private String groupId;
    private String hangoutId;
    private String title;
    private String status;          // Status from the main Event record
    private Instant hangoutTime;    // When the hangout is scheduled
    private String locationName;    // Denormalized location info
    private int participantCount;   // Cached count for display
    
    // Default constructor for DynamoDB
    public HangoutPointer() {
        super();
        setItemType("HANGOUT_POINTER");
    }

    /**
     * Create a new hangout pointer record.
     */
    public HangoutPointer(String groupId, String hangoutId, String title) {
        super();
        setItemType("HANGOUT_POINTER");
        this.groupId = groupId;
        this.hangoutId = hangoutId;
        this.title = title;
        this.participantCount = 0; // Will be updated as people respond
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(groupId));
        setSk(InviterKeyFactory.getHangoutSk(hangoutId));
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getHangoutId() {
        return hangoutId;
    }
    
    public void setHangoutId(String hangoutId) {
        this.hangoutId = hangoutId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
        touch(); // Update timestamp
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        touch(); // Update timestamp
    }
    
    public Instant getHangoutTime() {
        return hangoutTime;
    }
    
    public void setHangoutTime(Instant hangoutTime) {
        this.hangoutTime = hangoutTime;
        touch(); // Update timestamp
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
        touch(); // Update timestamp
    }
    
    public int getParticipantCount() {
        return participantCount;
    }
    
    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
        touch(); // Update timestamp
    }
}