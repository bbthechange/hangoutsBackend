package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.UUID;

/**
 * Poll entity for the InviterTable.
 * Represents a poll/decision within an event/hangout.
 * 
 * Key Pattern: PK = EVENT#{EventID}, SK = POLL#{PollID}
 */
@DynamoDbBean
public class Poll extends BaseItem {
    
    private String eventId;
    private String pollId;
    private String title;
    private String description;
    private boolean multipleChoice; // Allow multiple selections
    private boolean isActive;
    
    // Default constructor for DynamoDB
    public Poll() {
        super();
        setItemType("POLL");
    }

    /**
     * Create a new poll for an event.
     */
    public Poll(String eventId, String title, String description, boolean multipleChoice) {
        super();
        setItemType("POLL");
        this.eventId = eventId;
        this.pollId = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.multipleChoice = multipleChoice;
        this.isActive = true;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getPollSk(this.pollId));
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getPollId() {
        return pollId;
    }
    
    public void setPollId(String pollId) {
        this.pollId = pollId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
        touch();
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        touch();
    }
    
    public boolean isMultipleChoice() {
        return multipleChoice;
    }
    
    public void setMultipleChoice(boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
        touch();
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
        touch();
    }
}