package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.AddAlternativeRequest;
import com.bbthechange.inviter.dto.AttributeProposalDTO;
import com.bbthechange.inviter.dto.ProposalVoteRequest;
import com.bbthechange.inviter.service.AttributeProposalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the attribute proposal / silence=consent flow.
 *
 * Proposals are scoped under both groupId and hangoutId.
 * The groupId is used for membership checks; hangoutId for data access.
 *
 * Endpoints:
 *   GET    /groups/{groupId}/hangouts/{hangoutId}/proposals
 *   POST   /groups/{groupId}/hangouts/{hangoutId}/proposals/{proposalId}/alternatives
 *   POST   /groups/{groupId}/hangouts/{hangoutId}/proposals/{proposalId}/vote
 */
@RestController
@Validated
public class AttributeProposalController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AttributeProposalController.class);

    private static final String UUID_REGEX = "[0-9a-f-]{36}";

    private final AttributeProposalService attributeProposalService;

    @Autowired
    public AttributeProposalController(AttributeProposalService attributeProposalService) {
        this.attributeProposalService = attributeProposalService;
    }

    /**
     * List all active (PENDING) proposals for a hangout.
     * Caller must be a member of the group.
     */
    @GetMapping("/groups/{groupId}/hangouts/{hangoutId}/proposals")
    public ResponseEntity<List<AttributeProposalDTO>> listProposals(
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid hangout ID format") String hangoutId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        List<AttributeProposalDTO> proposals = attributeProposalService.listProposals(hangoutId, userId);
        return ResponseEntity.ok(proposals);
    }

    /**
     * Suggest an alternative value for an existing proposal.
     * This switches the proposal to lightweight poll mode.
     */
    @PostMapping("/groups/{groupId}/hangouts/{hangoutId}/proposals/{proposalId}/alternatives")
    public ResponseEntity<AttributeProposalDTO> addAlternative(
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid proposal ID format") String proposalId,
            @Valid @RequestBody AddAlternativeRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        AttributeProposalDTO updated = attributeProposalService.addAlternative(
                hangoutId, proposalId, request.getValue(), userId);
        logger.info("User {} added alternative to proposal {} on hangout {}", userId, proposalId, hangoutId);
        return ResponseEntity.ok(updated);
    }

    /**
     * Cast a vote on a proposal or one of its alternatives.
     * optionIndex 0 = original proposed value; 1..N = alternatives.
     */
    @PostMapping("/groups/{groupId}/hangouts/{hangoutId}/proposals/{proposalId}/vote")
    public ResponseEntity<AttributeProposalDTO> vote(
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = UUID_REGEX, message = "Invalid proposal ID format") String proposalId,
            @Valid @RequestBody ProposalVoteRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        AttributeProposalDTO updated = attributeProposalService.vote(
                hangoutId, proposalId, request.getOptionIndex(), userId);
        logger.info("User {} voted (optionIndex={}) on proposal {} for hangout {}",
                userId, request.getOptionIndex(), proposalId, hangoutId);
        return ResponseEntity.ok(updated);
    }
}
