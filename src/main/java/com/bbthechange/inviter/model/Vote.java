package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Vote entity for the InviterTable.
 * Represents a user's vote on a poll option.
 * 
 * Key Pattern: PK = EVENT#{EventID}, SK = POLL#{PollID}#VOTE#{UserID}#OPTION#{OptionID}
 */
@DynamoDbBean
public class Vote extends BaseItem {
    
    private String eventId;
    private String pollId;
    private String optionId;
    private String userId;
    private String userName;        // Denormalized for display
    private String voteType;        // Could be "YES", "NO", "MAYBE" or custom values
    
    // Default constructor for DynamoDB
    public Vote() {
        super();
    }

    /**
     * Create a new vote record.
     */
    public Vote(String eventId, String pollId, String optionId, String userId, String userName, String voteType) {
        super();
        this.eventId = eventId;
        this.pollId = pollId;
        this.optionId = optionId;
        this.userId = userId;
        this.userName = userName;
        this.voteType = voteType;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getVoteSk(pollId, userId, optionId));
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
    
    public String getVoteType() {
        return voteType;
    }
    
    public void setVoteType(String voteType) {
        this.voteType = voteType;
        touch();
    }
}