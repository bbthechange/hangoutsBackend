package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.PollService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.PollOption;
import com.bbthechange.inviter.model.Vote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * REST controller for poll management within events.
 * Handles creation, voting, and retrieval of polls.
 */
@RestController
@RequestMapping("/hangouts/{eventId}/polls")
@Validated
@Tag(name = "Polls", description = "Poll management within hangouts")
public class PollController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(PollController.class);
    
    private final PollService pollService;
    
    @Autowired
    public PollController(PollService pollService) {
        this.pollService = pollService;
    }
    
    @PostMapping
    @Operation(summary = "Create a new poll for an event")
    public ResponseEntity<Poll> createPoll(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @Valid @RequestBody CreatePollRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Creating poll '{}' for event {} by user {}", request.getTitle(), eventId, userId);
        
        Poll poll = pollService.createPoll(eventId, request, userId);
        logger.info("Successfully created poll {} for event {}", poll.getPollId(), eventId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(poll);
    }
    
    @GetMapping
    @Operation(summary = "Get all polls for an event")
    public ResponseEntity<List<PollWithOptionsDTO>> getEventPolls(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        List<PollWithOptionsDTO> polls = pollService.getEventPolls(eventId, userId);
        logger.debug("Retrieved {} polls for event {}", polls.size(), eventId);
        
        return ResponseEntity.ok(polls);
    }
    
    @GetMapping("/{pollId}")
    @Operation(summary = "Get a specific poll with its options and votes")
    public ResponseEntity<PollDetailDTO> getPoll(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid poll ID format") String pollId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        PollDetailDTO poll = pollService.getPollDetail(eventId, pollId, userId);
        
        return ResponseEntity.ok(poll);
    }
    
    @PostMapping("/{pollId}/vote")
    @Operation(summary = "Vote on a poll option")
    public ResponseEntity<Vote> voteOnPoll(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid poll ID format") String pollId,
            @Valid @RequestBody VoteRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} voting on poll {} option {}", userId, pollId, request.getOptionId());
        
        Vote vote = pollService.voteOnPoll(eventId, pollId, request, userId);
        
        return ResponseEntity.ok(vote);
    }
    
    @DeleteMapping("/{pollId}/vote")
    @Operation(summary = "Remove vote from a poll")
    public ResponseEntity<Void> removeVote(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid poll ID format") String pollId,
            @RequestParam(required = false) String optionId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} removing vote from poll {}", userId, pollId);
        
        pollService.removeVote(eventId, pollId, optionId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/{pollId}")
    @Operation(summary = "Delete a poll (admin only)")
    public ResponseEntity<Void> deletePoll(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid poll ID format") String pollId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} deleting poll {} from event {}", userId, pollId, eventId);
        
        pollService.deletePoll(eventId, pollId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{pollId}/options")
    @Operation(summary = "Add an option to an existing poll")
    public ResponseEntity<PollOption> addPollOption(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid poll ID format") String pollId,
            @Valid @RequestBody AddPollOptionRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} adding option '{}' to poll {}", userId, request.getText(), pollId);
        
        PollOption option = pollService.addPollOption(eventId, pollId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(option);
    }
    
    @DeleteMapping("/{pollId}/options/{optionId}")
    @Operation(summary = "Delete a poll option (host only)")
    public ResponseEntity<Void> deletePollOption(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid poll ID format") String pollId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid option ID format") String optionId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} requesting deletion of option {} from poll {}", userId, optionId, pollId);
        
        pollService.deletePollOption(eventId, pollId, optionId, userId);
        
        return ResponseEntity.noContent().build();
    }
}