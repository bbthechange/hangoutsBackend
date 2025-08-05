package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.Vote;
import java.util.List;

/**
 * Service interface for poll management within events.
 */
public interface PollService {
    
    /**
     * Create a new poll for an event.
     * Only event hosts (admins of associated groups) can create polls.
     */
    Poll createPoll(String eventId, CreatePollRequest request, String userId);
    
    /**
     * Get all polls for an event with basic vote counts.
     * Users must have access to the event to view polls.
     */
    List<PollWithOptionsDTO> getEventPolls(String eventId, String userId);
    
    /**
     * Get detailed poll information including all votes.
     * Users must have access to the event to view poll details.
     */
    PollDetailDTO getPollDetail(String eventId, String pollId, String userId);
    
    /**
     * Vote on a poll option.
     * Users must have access to the event to vote.
     */
    Vote voteOnPoll(String eventId, String pollId, VoteRequest request, String userId);
    
    /**
     * Remove a vote from a poll.
     * Users can only remove their own votes.
     */
    void removeVote(String eventId, String pollId, String optionId, String userId);
    
    /**
     * Delete a poll (admin only).
     * Only event hosts can delete polls.
     */
    void deletePoll(String eventId, String pollId, String userId);
}