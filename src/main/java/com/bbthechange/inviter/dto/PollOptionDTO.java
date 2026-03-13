package com.bbthechange.inviter.dto;

/**
 * DTO for poll option with vote count.
 */
public class PollOptionDTO {
    private String optionId;
    private String text;
    private int voteCount;
    private boolean userVoted;
    private String createdBy;
    private String structuredValue;

    public PollOptionDTO() {}

    public PollOptionDTO(String optionId, String text, int voteCount, boolean userVoted) {
        this.optionId = optionId;
        this.text = text;
        this.voteCount = voteCount;
        this.userVoted = userVoted;
    }

    public PollOptionDTO(String optionId, String text, int voteCount, boolean userVoted,
                         String createdBy, String structuredValue) {
        this.optionId = optionId;
        this.text = text;
        this.voteCount = voteCount;
        this.userVoted = userVoted;
        this.createdBy = createdBy;
        this.structuredValue = structuredValue;
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
    }
    
    public int getVoteCount() {
        return voteCount;
    }
    
    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }
    
    public boolean isUserVoted() {
        return userVoted;
    }
    
    public void setUserVoted(boolean userVoted) {
        this.userVoted = userVoted;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getStructuredValue() {
        return structuredValue;
    }

    public void setStructuredValue(String structuredValue) {
        this.structuredValue = structuredValue;
    }
}