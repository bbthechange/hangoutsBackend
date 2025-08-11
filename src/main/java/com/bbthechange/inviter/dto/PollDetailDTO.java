package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Poll;
import java.util.List;

/**
 * DTO for detailed poll information with all votes.
 */
public class PollDetailDTO {
    
    private String pollId;
    private String title;
    private String description;
    private boolean multipleChoice;
    private List<PollOptionDetailDTO> options;
    private int totalVotes;
    
    public PollDetailDTO() {}
    
    public PollDetailDTO(Poll poll, List<PollOptionDetailDTO> options, int totalVotes) {
        this.pollId = poll.getPollId();
        this.title = poll.getTitle();
        this.description = poll.getDescription();
        this.multipleChoice = poll.isMultipleChoice();
        this.options = options;
        this.totalVotes = totalVotes;
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
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isMultipleChoice() {
        return multipleChoice;
    }
    
    public void setMultipleChoice(boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
    }
    
    public List<PollOptionDetailDTO> getOptions() {
        return options;
    }
    
    public void setOptions(List<PollOptionDetailDTO> options) {
        this.options = options;
    }
    
    public int getTotalVotes() {
        return totalVotes;
    }
    
    public void setTotalVotes(int totalVotes) {
        this.totalVotes = totalVotes;
    }
}