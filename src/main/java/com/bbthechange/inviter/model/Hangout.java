package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hangout entity for the InviterTable.
 * Represents the canonical record for a hangout/event.
 * 
 * Key Pattern: PK = EVENT#{HangoutID}, SK = METADATA
 */
@DynamoDbBean
public class Hangout extends BaseItem {
    
    private String hangoutId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private Long version;
    private List<String> associatedGroups; // Groups this hangout is associated with
    private boolean carpoolEnabled; // Whether carpooling features are enabled
    private Map<String, String> timeInput; // Original fuzzy time input from client
    private Long startTimestamp; // Canonical UTC Unix timestamp (seconds since epoch) for start time
    private Long endTimestamp; // Canonical UTC Unix timestamp (seconds since epoch) for end time
    
    // Default constructor for DynamoDB
    public Hangout() {
        super();
        setItemType(InviterKeyFactory.HANGOUT_PREFIX);
        this.associatedGroups = new ArrayList<>();
        this.carpoolEnabled = false;
        this.version = 1L;
    }

    /**
     * Create a new hangout with generated UUID.
     */
    public Hangout(String title, String description, LocalDateTime startTime, LocalDateTime endTime,
                  Address location, EventVisibility visibility, String mainImagePath) {
        super();
        setItemType(InviterKeyFactory.HANGOUT_PREFIX);
        this.hangoutId = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.visibility = visibility;
        this.mainImagePath = mainImagePath;
        this.version = 1L;
        this.associatedGroups = new ArrayList<>();
        this.carpoolEnabled = false;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(this.hangoutId));
        setSk(InviterKeyFactory.getMetadataSk());
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        touch(); // Update timestamp
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        touch(); // Update timestamp
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        touch(); // Update timestamp
    }
    
    public Address getLocation() {
        return location;
    }
    
    public void setLocation(Address location) {
        this.location = location;
        touch(); // Update timestamp
    }
    
    public EventVisibility getVisibility() {
        return visibility;
    }
    
    public void setVisibility(EventVisibility visibility) {
        this.visibility = visibility;
        touch(); // Update timestamp
    }
    
    public String getMainImagePath() {
        return mainImagePath;
    }
    
    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        touch(); // Update timestamp
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public List<String> getAssociatedGroups() {
        return associatedGroups;
    }
    
    public void setAssociatedGroups(List<String> associatedGroups) {
        this.associatedGroups = associatedGroups != null ? associatedGroups : new ArrayList<>();
        touch(); // Update timestamp
    }
    
    public boolean isCarpoolEnabled() {
        return carpoolEnabled;
    }
    
    public void setCarpoolEnabled(boolean carpoolEnabled) {
        this.carpoolEnabled = carpoolEnabled;
        touch(); // Update timestamp
    }
    
    /**
     * Increment version for optimistic locking.
     */
    public void incrementVersion() {
        this.version = (this.version != null) ? this.version + 1 : 1L;
        touch();
    }
    
    /**
     * Add a group to the associated groups list.
     */
    public void addAssociatedGroup(String groupId) {
        if (this.associatedGroups == null) {
            this.associatedGroups = new ArrayList<>();
        }
        if (!this.associatedGroups.contains(groupId)) {
            this.associatedGroups.add(groupId);
            touch();
        }
    }
    
    /**
     * Remove a group from the associated groups list.
     */
    public void removeAssociatedGroup(String groupId) {
        if (this.associatedGroups != null) {
            this.associatedGroups.remove(groupId);
            touch();
        }
    }
    
    public Map<String, String> getTimeInput() {
        return timeInput;
    }
    
    public void setTimeInput(Map<String, String> timeInput) {
        this.timeInput = timeInput;
        touch(); // Update timestamp
    }
    
    public Long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        touch(); // Update timestamp
    }
    
    public Long getEndTimestamp() {
        return endTimestamp;
    }
    
    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        touch(); // Update timestamp
    }
}