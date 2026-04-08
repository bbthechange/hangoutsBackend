package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Vote;

/**
 * DTO for individual vote information.
 */
public class VoteDTO {
    private String userId;
    private String voteType;
    private String displayName;
    
    public VoteDTO() {}
    
    public VoteDTO(Vote vote) {
        this.userId = vote.getUserId();
        this.voteType = vote.getVoteType();
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getVoteType() {
        return voteType;
    }
    
    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}