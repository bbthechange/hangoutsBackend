package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

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
    private String GSI1PK;          // GSI partition key for EntityTimeIndex
    private String GSI1SK;          // GSI sort key for EntityTimeIndex
    private TimeInfo timeInfo;    // Denormalized for efficient reads
    private Long startTimestamp;    // Denormalized for GSI sorting
    private Long endTimestamp;      // Denormalized for completeness
    
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
    
    public String getGSI1PK() {
        return GSI1PK;
    }
    
    public void setGSI1PK(String GSI1PK) {
        this.GSI1PK = GSI1PK;
        touch(); // Update timestamp
    }
    
    public String getGSI1SK() {
        return GSI1SK;
    }
    
    public void setGSI1SK(String GSI1SK) {
        this.GSI1SK = GSI1SK;
        touch(); // Update timestamp
    }
    
    @DynamoDbAttribute("timeInput")
    public TimeInfo getTimeInput() {
        return timeInfo;
    }
    
    public void setTimeInput(TimeInfo timeInfo) {
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
}