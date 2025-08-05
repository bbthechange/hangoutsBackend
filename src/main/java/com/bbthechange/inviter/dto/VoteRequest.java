package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for voting on a poll option.
 */
public class VoteRequest {
    
    @NotBlank(message = "Option ID is required")
    @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid option ID format")
    private String optionId;
    
    @Pattern(regexp = "YES|NO|MAYBE", message = "Vote type must be YES, NO, or MAYBE")
    private String voteType = "YES";
    
    public VoteRequest() {}
    
    public VoteRequest(String optionId, String voteType) {
        this.optionId = optionId;
        this.voteType = voteType;
    }
    
    public String getOptionId() {
        return optionId;
    }
    
    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }
    
    public String getVoteType() {
        return voteType;
    }
    
    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }
}