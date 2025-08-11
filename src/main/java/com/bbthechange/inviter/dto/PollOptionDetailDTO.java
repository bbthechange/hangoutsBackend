package com.bbthechange.inviter.dto;

import java.util.List;

/**
 * DTO for poll option with detailed vote information.
 */
public class PollOptionDetailDTO {
    private String optionId;
    private String text;
    private int voteCount;
    private boolean userVoted;
    private List<VoteDTO> votes;
    
    public PollOptionDetailDTO() {}
    
    public PollOptionDetailDTO(String optionId, String text, int voteCount, boolean userVoted, List<VoteDTO> votes) {
        this.optionId = optionId;
        this.text = text;
        this.voteCount = voteCount;
        this.userVoted = userVoted;
        this.votes = votes;
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
    
    public List<VoteDTO> getVotes() {
        return votes;
    }
    
    public void setVotes(List<VoteDTO> votes) {
        this.votes = votes;
    }
}