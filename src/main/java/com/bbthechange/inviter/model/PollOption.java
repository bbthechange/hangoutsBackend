package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.UUID;

/**
 * PollOption entity for the InviterTable.
 * Represents an option/choice within a poll.
 * 
 * Key Pattern: pk = EVENT#{EventID}, sk = POLL#{PollID}#OPTION#{OptionID}
 */
@DynamoDbBean
public class PollOption extends BaseItem {
    
    private String eventId;
    private String pollId;
    private String optionId;
    private String text;
    
    // Default constructor for DynamoDB
    public PollOption() {
        super();
    }

    /**
     * Create a new poll option.
     */
    public PollOption(String eventId, String pollId, String text) {
        super();
        this.eventId = eventId;
        this.pollId = pollId;
        this.optionId = UUID.randomUUID().toString();
        this.text = text;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getPollOptionSk(pollId, this.optionId));
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
    
    public String getOptionId() {
        return optionId;
    }
    
    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
        touch();
    }
}