package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.Vote;
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

/**
 * DTO for poll option with detailed vote information.
 */
class PollOptionDetailDTO {
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

/**
 * DTO for individual vote information.
 */
class VoteDTO {
    private String userId;
    private String userName;
    private String voteType;
    
    public VoteDTO() {}
    
    public VoteDTO(Vote vote) {
        this.userId = vote.getUserId();
        this.userName = vote.getUserName();
        this.voteType = vote.getVoteType();
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
    }
    
    public String getVoteType() {
        return voteType;
    }
    
    public void setVoteType(String voteType) {
        this.voteType = voteType;
    }
}