package com.bbthechange.inviter.dto;

/**
 * DTO for poll option with vote count.
 */
public class PollOptionDTO {
    private String optionId;
    private String text;
    private int voteCount;
    private boolean userVoted;
    
    public PollOptionDTO() {}
    
    public PollOptionDTO(String optionId, String text, int voteCount, boolean userVoted) {
        this.optionId = optionId;
        this.text = text;
        this.voteCount = voteCount;
        this.userVoted = userVoted;
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
}