package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.service.AuthorizationService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of PollService for poll management within events.
 */
@Service
public class PollServiceImpl implements PollService {
    
    private static final Logger logger = LoggerFactory.getLogger(PollServiceImpl.class);
    
    private final HangoutRepository hangoutRepository;
    private final AuthorizationService authorizationService;
    
    @Autowired
    public PollServiceImpl(HangoutRepository hangoutRepository, AuthorizationService authorizationService) {
        this.hangoutRepository = hangoutRepository;
        this.authorizationService = authorizationService;
    }
    
    @Override
    public Poll createPoll(String eventId, CreatePollRequest request, String userId) {
        logger.info("Creating poll '{}' for event {} by user {}", request.getTitle(), eventId, userId);
        
        // Get event and verify user can edit it
        HangoutDetailData eventData = hangoutRepository.getHangoutDetailData(eventId);
        if (eventData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = eventData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("User cannot edit hangout");
        }
        
        // Create poll
        Poll poll = new Poll(eventId, request.getTitle(), request.getDescription(), request.isMultipleChoice());
        Poll savedPoll = hangoutRepository.savePoll(poll);
        
        // Create poll options individually (following UI flow)
        for (String optionText : request.getOptions()) {
            PollOption option = new PollOption(eventId, poll.getPollId(), optionText);
            hangoutRepository.savePollOption(option);
        }
        
        logger.info("Successfully created poll {} for event {} with {} options", savedPoll.getPollId(), eventId, request.getOptions().size());
        
        return savedPoll;
    }
    
    @Override
    public List<PollWithOptionsDTO> getEventPolls(String eventId, String userId) {
        logger.debug("Getting polls for event {} for user {}", eventId, userId);
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view event polls");
        }
        
        // Get all poll data in one query using item collection pattern
        List<BaseItem> allPollData = hangoutRepository.getAllPollData(eventId);
        
        return transformToPollWithOptionsDTO(allPollData, userId);
    }
    
    @Override
    public PollDetailDTO getPollDetail(String eventId, String pollId, String userId) {
        logger.debug("Getting poll detail for poll {} in event {} for user {}", pollId, eventId, userId);
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view event polls");
        }
        
        // TODO: Find specific poll and build detailed DTO
        // Get specific poll data in one query
        List<BaseItem> specificPollData = hangoutRepository.getSpecificPollData(eventId, pollId);
        
        return transformToPollDetailDTO(specificPollData, userId);
    }
    
    @Override
    public Vote voteOnPoll(String eventId, String pollId, VoteRequest request, String userId) {
        logger.info("User {} voting on poll {} option {}", userId, pollId, request.getOptionId());
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot vote on polls in this event");
        }
        
        // Get poll data to check settings and existing votes
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);
        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        
        String optionId = request.getOptionId();
        
        // Get user's existing votes for this poll
        List<Vote> existingVotes = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .filter(vote -> vote.getUserId().equals(userId))
            .collect(Collectors.toList());
        
        if (!poll.isMultipleChoice()) {
            // SINGLE CHOICE: Simply replace existing vote
            if (!existingVotes.isEmpty()) {
                Vote existingVote = existingVotes.get(0);
                
                if (!existingVote.getOptionId().equals(optionId)) {
                    // Delete old vote, create new vote
                    hangoutRepository.deleteVote(eventId, pollId, userId, existingVote.getOptionId());
                } else {
                    throw new IllegalStateException("User already voted for this option");
                }
            }
        } else {
            // MULTIPLE CHOICE: Check if already voted for this specific option
            boolean alreadyVoted = existingVotes.stream()
                .anyMatch(vote -> vote.getOptionId().equals(optionId));
            
            if (alreadyVoted) {
                throw new IllegalStateException("User already voted for this option");
            }
        }
        
        // Create the vote
        Vote newVote = new Vote(eventId, pollId, optionId, userId, request.getVoteType());
        return hangoutRepository.saveVote(newVote);
    }
    
    @Override
    public void removeVote(String eventId, String pollId, String optionId, String userId) {
        logger.info("User {} removing vote from poll {}", userId, pollId);
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot modify votes in this event");
        }
        
        // Get poll data to find user's existing votes
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);
        
        // Find user's existing votes for this poll
        List<Vote> existingVotes = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .filter(vote -> vote.getUserId().equals(userId))
            .collect(Collectors.toList());
        
        if (existingVotes.isEmpty()) {
            logger.warn("User {} has no votes to remove from poll {}", userId, pollId);
            return; // No votes to remove
        }
        
        // If optionId is specified, remove only that vote, otherwise remove all user votes
        if (optionId != null) {
            hangoutRepository.deleteVote(eventId, pollId, userId, optionId);
        } else {
            // Remove all votes for this user on this poll (for single-choice polls)
            for (Vote vote : existingVotes) {
                hangoutRepository.deleteVote(eventId, pollId, userId, vote.getOptionId());
            }
        }
        
        logger.info("Successfully removed vote(s) for user {} from poll {}", userId, pollId);
    }
    
    @Override
    public void deletePoll(String eventId, String pollId, String userId) {
        logger.info("User {} deleting poll {} from event {}", userId, pollId, eventId);
        
        // Get hangout and verify user can edit it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("Only event hosts can delete polls");
        }
        
        hangoutRepository.deletePoll(eventId, pollId);
        logger.info("Successfully deleted poll {} from event {}", pollId, eventId);
    }
    
    @Override
    public PollOption addPollOption(String eventId, String pollId, AddPollOptionRequest request, String userId) {
        logger.info("User {} adding option '{}' to poll {} in event {}", userId, request.getText(), pollId, eventId);
        
        // Get event and verify user can edit it
        HangoutDetailData eventData = hangoutRepository.getHangoutDetailData(eventId);
        if (eventData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = eventData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("User cannot edit hangout");
        }
        
        // Create and save the new poll option
        PollOption option = new PollOption(eventId, pollId, request.getText());
        PollOption savedOption = hangoutRepository.savePollOption(option);
        
        logger.info("Successfully added option {} to poll {}", option.getOptionId(), pollId);
        return savedOption;
    }
    
    @Override
    public void deletePollOption(String eventId, String pollId, String optionId, String userId) {
        logger.info("User {} deleting option {} from poll {} in event {}", userId, optionId, pollId, eventId);
        
        // Authorization check - only hosts can delete options
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!authorizationService.canUserEditHangout(userId, hangout)) {
            throw new UnauthorizedException("Only event hosts can delete poll options");
        }
        
        // Verify poll and option exist
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(eventId, pollId);
        
        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        
        boolean optionExists = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .anyMatch(option -> option.getOptionId().equals(optionId));
        
        if (!optionExists) {
            throw new IllegalArgumentException("Poll option not found");
        }
        
        // Use transaction to delete option and all its votes
        hangoutRepository.deletePollOptionTransaction(eventId, pollId, optionId);
        
        logger.info("Successfully deleted option {} and its votes from poll {}", optionId, pollId);
    }
    
    // ============================================================================
    // DATA TRANSFORMATION METHODS (Runtime Vote Count Calculation)
    // ============================================================================
    
    private List<PollWithOptionsDTO> transformToPollWithOptionsDTO(List<BaseItem> pollData, String userId) {
        // Separate polls, options, and votes
        List<Poll> polls = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .collect(Collectors.toList());
            
        Map<String, List<PollOption>> optionsByPoll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.groupingBy(PollOption::getPollId));
            
        Map<String, List<Vote>> votesByPoll = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.groupingBy(Vote::getPollId));
        
        // Build hierarchical DTOs
        return polls.stream()
            .map(poll -> {
                List<PollOption> options = optionsByPoll.getOrDefault(poll.getPollId(), List.of());
                List<Vote> allVotes = votesByPoll.getOrDefault(poll.getPollId(), List.of());
                
                // Calculate vote counts by option at runtime
                Map<String, Long> voteCountsByOption = allVotes.stream()
                    .collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));
                
                List<PollOptionDTO> optionDTOs = options.stream()
                    .map(option -> {
                        // Runtime calculation - no denormalized count field needed
                        int voteCount = voteCountsByOption.getOrDefault(option.getOptionId(), 0L).intValue();
                        
                        boolean userVoted = allVotes.stream()
                            .anyMatch(vote -> vote.getOptionId().equals(option.getOptionId()) 
                                           && vote.getUserId().equals(userId));
                        
                        return new PollOptionDTO(option.getOptionId(), option.getText(), 
                                               voteCount, userVoted);
                    })
                    .collect(Collectors.toList());
                
                // Total votes = sum of all votes for this poll
                int totalVotes = allVotes.size();
                
                return new PollWithOptionsDTO(poll, optionDTOs, totalVotes);
            })
            .collect(Collectors.toList());
    }
    
    private PollDetailDTO transformToPollDetailDTO(List<BaseItem> pollData, String userId) {
        // Find the poll
        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Poll not found"));
        
        // Group options and votes
        List<PollOption> options = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.toList());
            
        Map<String, List<Vote>> votesByOption = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.groupingBy(Vote::getOptionId));
        
        // Build detailed option DTOs with vote details
        List<PollOptionDetailDTO> optionDTOs = options.stream()
            .map(option -> {
                List<Vote> optionVotes = votesByOption.getOrDefault(option.getOptionId(), List.of());
                boolean userVoted = optionVotes.stream()
                    .anyMatch(vote -> vote.getUserId().equals(userId));
                
                List<VoteDTO> voteDTOs = optionVotes.stream()
                    .map(VoteDTO::new)
                    .collect(Collectors.toList());
                
                // Runtime calculation - count the actual votes
                int voteCount = optionVotes.size();
                
                return new PollOptionDetailDTO(option.getOptionId(), option.getText(), 
                                             voteCount, userVoted, voteDTOs);
            })
            .collect(Collectors.toList());
        
        // Total votes = total count of all vote records
        int totalVotes = votesByOption.values().stream().mapToInt(List::size).sum();
        
        return new PollDetailDTO(poll, optionDTOs, totalVotes);
    }
}