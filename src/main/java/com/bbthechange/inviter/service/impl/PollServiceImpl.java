package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.Vote;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of PollService for poll management within events.
 */
@Service
public class PollServiceImpl implements PollService {
    
    private static final Logger logger = LoggerFactory.getLogger(PollServiceImpl.class);
    
    private final HangoutRepository hangoutRepository;
    private final HangoutService hangoutService;
    
    @Autowired
    public PollServiceImpl(HangoutRepository hangoutRepository, HangoutService hangoutService) {
        this.hangoutRepository = hangoutRepository;
        this.hangoutService = hangoutService;
    }
    
    @Override
    public Poll createPoll(String eventId, CreatePollRequest request, String userId) {
        logger.info("Creating poll '{}' for event {} by user {}", request.getTitle(), eventId, userId);
        
        // Get event and verify user can edit it
        EventDetailData eventData = hangoutRepository.getEventDetailData(eventId);
        if (eventData.getEvent() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Event event = eventData.getEvent();
        if (!hangoutService.canUserEditEvent(userId, event)) {
            throw new UnauthorizedException("Only event hosts can create polls");
        }
        
        // Create poll
        Poll poll = new Poll(eventId, request.getTitle(), request.getDescription(), request.isMultipleChoice());
        
        // TODO: Create poll options from request.getOptions()
        // This would require a PollOption model and repository methods
        
        Poll savedPoll = hangoutRepository.savePoll(poll);
        logger.info("Successfully created poll {} for event {}", savedPoll.getPollId(), eventId);
        
        return savedPoll;
    }
    
    @Override
    public List<PollWithOptionsDTO> getEventPolls(String eventId, String userId) {
        logger.debug("Getting polls for event {} for user {}", eventId, userId);
        
        // Get event and verify user can view it
        EventDetailData eventData = hangoutRepository.getEventDetailData(eventId);
        if (eventData.getEvent() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Event event = eventData.getEvent();
        if (!hangoutService.canUserViewEvent(userId, event)) {
            throw new UnauthorizedException("Cannot view event polls");
        }
        
        // TODO: Build PollWithOptionsDTO from polls and votes
        // This requires implementing poll option and vote aggregation logic
        return List.of(); // Placeholder
    }
    
    @Override
    public PollDetailDTO getPollDetail(String eventId, String pollId, String userId) {
        logger.debug("Getting poll detail for poll {} in event {} for user {}", pollId, eventId, userId);
        
        // Get event and verify user can view it
        EventDetailData eventData = hangoutRepository.getEventDetailData(eventId);
        if (eventData.getEvent() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Event event = eventData.getEvent();
        if (!hangoutService.canUserViewEvent(userId, event)) {
            throw new UnauthorizedException("Cannot view event polls");
        }
        
        // TODO: Find specific poll and build detailed DTO
        throw new UnsupportedOperationException("Poll detail retrieval not yet implemented");
    }
    
    @Override
    public Vote voteOnPoll(String eventId, String pollId, VoteRequest request, String userId) {
        logger.info("User {} voting on poll {} option {}", userId, pollId, request.getOptionId());
        
        // Get event and verify user can view it
        EventDetailData eventData = hangoutRepository.getEventDetailData(eventId);
        if (eventData.getEvent() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Event event = eventData.getEvent();
        if (!hangoutService.canUserViewEvent(userId, event)) {
            throw new UnauthorizedException("Cannot vote on polls in this event");
        }
        
        // TODO: Create vote and handle multiple choice logic
        throw new UnsupportedOperationException("Voting not yet implemented");
    }
    
    @Override
    public void removeVote(String eventId, String pollId, String optionId, String userId) {
        logger.info("User {} removing vote from poll {}", userId, pollId);
        
        // Get event and verify user can view it
        EventDetailData eventData = hangoutRepository.getEventDetailData(eventId);
        if (eventData.getEvent() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Event event = eventData.getEvent();
        if (!hangoutService.canUserViewEvent(userId, event)) {
            throw new UnauthorizedException("Cannot modify votes in this event");
        }
        
        // TODO: Remove user's vote from the poll/option
        throw new UnsupportedOperationException("Vote removal not yet implemented");
    }
    
    @Override
    public void deletePoll(String eventId, String pollId, String userId) {
        logger.info("User {} deleting poll {} from event {}", userId, pollId, eventId);
        
        // Get event and verify user can edit it
        EventDetailData eventData = hangoutRepository.getEventDetailData(eventId);
        if (eventData.getEvent() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Event event = eventData.getEvent();
        if (!hangoutService.canUserEditEvent(userId, event)) {
            throw new UnauthorizedException("Only event hosts can delete polls");
        }
        
        hangoutRepository.deletePoll(eventId, pollId);
        logger.info("Successfully deleted poll {} from event {}", pollId, eventId);
    }
}