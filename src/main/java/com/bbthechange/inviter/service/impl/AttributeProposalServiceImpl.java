package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.AttributeProposalDTO;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.AttributeProposal;
import com.bbthechange.inviter.model.AttributeProposalStatus;
import com.bbthechange.inviter.model.AttributeProposalType;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.AttributeProposalRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.AttributeProposalService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for the attribute proposal / silence=consent flow.
 *
 * When a non-creator edits a hangout's location or description, a proposal is
 * created instead of applying the change directly. After 24 hours:
 *   - No alternatives → auto-adopted (silence = consent)
 *   - Alternatives exist → lightweight vote determines outcome
 *
 * Creator's own edits bypass this flow entirely (handled in HangoutServiceImpl).
 */
@Service
public class AttributeProposalServiceImpl implements AttributeProposalService {

    private static final Logger logger = LoggerFactory.getLogger(AttributeProposalServiceImpl.class);

    private final AttributeProposalRepository proposalRepository;
    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;
    private final UserService userService;
    private final PointerUpdateService pointerUpdateService;

    @Autowired
    public AttributeProposalServiceImpl(AttributeProposalRepository proposalRepository,
                                         HangoutRepository hangoutRepository,
                                         GroupRepository groupRepository,
                                         NotificationService notificationService,
                                         UserService userService,
                                         PointerUpdateService pointerUpdateService) {
        this.proposalRepository = proposalRepository;
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.notificationService = notificationService;
        this.userService = userService;
        this.pointerUpdateService = pointerUpdateService;
    }

    @Override
    public AttributeProposalDTO createProposal(String hangoutId, String groupId, String proposedBy,
                                                AttributeProposalType attributeType, String proposedValue) {
        // Supersede any existing PENDING proposals for this hangout + attribute type
        List<AttributeProposal> existing =
                proposalRepository.findPendingByHangoutIdAndType(hangoutId, attributeType);
        for (AttributeProposal old : existing) {
            old.setStatus(AttributeProposalStatus.SUPERSEDED);
            proposalRepository.save(old);
            logger.info("Superseded proposal {} for hangout {} attribute {}", old.getProposalId(), hangoutId, attributeType);
        }

        // Create new proposal
        AttributeProposal proposal = new AttributeProposal(hangoutId, groupId, proposedBy, attributeType, proposedValue);
        proposalRepository.save(proposal);
        logger.info("Created attribute proposal {} for hangout {} (type={}) by user {}",
                proposal.getProposalId(), hangoutId, attributeType, proposedBy);

        // Notify all group members (fire-and-forget)
        try {
            String proposerName = resolveDisplayName(proposedBy);
            Hangout hangout = hangoutRepository.findHangoutById(hangoutId).orElse(null);
            String hangoutTitle = hangout != null ? hangout.getTitle() : "a hangout";
            String attributeLabel = AttributeProposalType.LOCATION.equals(attributeType) ? "location" : "description";
            String notificationMessage = proposerName + " suggested a new " + attributeLabel + " for \"" + hangoutTitle + "\"";
            notificationService.notifyAttributeProposal(groupId, proposedBy, notificationMessage);
        } catch (Exception e) {
            logger.warn("Failed to send attribute proposal notification: {}", e.getMessage());
        }

        return AttributeProposalDTO.fromEntity(proposal);
    }

    @Override
    public List<AttributeProposalDTO> listProposals(String hangoutId, String requestingUserId) {
        // Verify user has access to the hangout via group membership
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Hangout not found: " + hangoutId));

        boolean isMember = false;
        if (hangout.getAssociatedGroups() != null) {
            for (String groupId : hangout.getAssociatedGroups()) {
                if (groupRepository.findMembership(groupId, requestingUserId).isPresent()) {
                    isMember = true;
                    break;
                }
            }
        }
        if (!isMember) {
            throw new com.bbthechange.inviter.exception.UnauthorizedException("Not a member of this hangout's group");
        }

        return proposalRepository.findByHangoutId(hangoutId).stream()
                .filter(p -> AttributeProposalStatus.PENDING.equals(p.getStatus()))
                .map(AttributeProposalDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public AttributeProposalDTO addAlternative(String hangoutId, String proposalId,
                                                String alternativeValue, String requestingUserId) {
        AttributeProposal proposal = findPendingProposal(hangoutId, proposalId);
        verifyMembership(proposal.getGroupId(), requestingUserId);

        if (proposal.getAlternatives() == null) {
            proposal.setAlternatives(new ArrayList<>());
        }
        if (proposal.getVoteCounts() == null) {
            proposal.setVoteCounts(new ArrayList<>());
        }

        proposal.getAlternatives().add(alternativeValue);
        // Initialize vote count for the new alternative to 0
        // voteCounts[0] = votes for original proposedValue, voteCounts[1..N] = votes for alternatives
        // Ensure the original has a slot too
        if (proposal.getVoteCounts().isEmpty()) {
            proposal.getVoteCounts().add(0); // slot 0 for original proposedValue
        }
        proposal.getVoteCounts().add(0); // slot for new alternative

        proposalRepository.save(proposal);
        logger.info("Added alternative to proposal {} for hangout {} by user {}", proposalId, hangoutId, requestingUserId);

        return AttributeProposalDTO.fromEntity(proposal);
    }

    @Override
    public AttributeProposalDTO vote(String hangoutId, String proposalId,
                                      int optionIndex, String requestingUserId) {
        AttributeProposal proposal = findPendingProposal(hangoutId, proposalId);
        verifyMembership(proposal.getGroupId(), requestingUserId);

        // Validate index bounds: 0 = original, 1..N = alternatives
        int maxIndex = proposal.hasAlternatives() ? proposal.getAlternatives().size() : 0;
        if (optionIndex < 0 || optionIndex > maxIndex) {
            throw new ValidationException("Invalid optionIndex: " + optionIndex
                    + ". Valid range: 0 to " + maxIndex);
        }

        // Ensure voteCounts list is large enough
        List<Integer> voteCounts = proposal.getVoteCounts();
        if (voteCounts == null) {
            voteCounts = new ArrayList<>();
            proposal.setVoteCounts(voteCounts);
        }
        while (voteCounts.size() <= optionIndex) {
            voteCounts.add(0);
        }
        voteCounts.set(optionIndex, voteCounts.get(optionIndex) + 1);

        proposalRepository.save(proposal);
        logger.info("User {} voted for option {} on proposal {} (hangout {})",
                requestingUserId, optionIndex, proposalId, hangoutId);

        return AttributeProposalDTO.fromEntity(proposal);
    }

    @Override
    public void autoAdoptExpiredProposals() {
        long nowMillis = System.currentTimeMillis();
        List<AttributeProposal> expired = proposalRepository.findExpiredPendingProposals(nowMillis);

        logger.info("Auto-adoption task: found {} expired PENDING proposals", expired.size());

        for (AttributeProposal proposal : expired) {
            try {
                if (proposal.hasAlternatives()) {
                    // Alternatives exist: vote determines outcome (don't auto-adopt silently)
                    logger.info("Proposal {} has alternatives — skipping auto-adoption, vote required",
                            proposal.getProposalId());
                    continue;
                }

                // No alternatives → auto-adopt (silence = consent)
                adoptProposal(proposal);
            } catch (Exception e) {
                logger.error("Failed to auto-adopt proposal {} for hangout {}: {}",
                        proposal.getProposalId(), proposal.getHangoutId(), e.getMessage(), e);
            }
        }
    }

    // ============================================================================
    // PRIVATE HELPERS
    // ============================================================================

    private AttributeProposal findPendingProposal(String hangoutId, String proposalId) {
        AttributeProposal proposal = proposalRepository.findById(hangoutId, proposalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Proposal not found: " + proposalId + " for hangout " + hangoutId));
        if (!AttributeProposalStatus.PENDING.equals(proposal.getStatus())) {
            throw new ValidationException("Proposal " + proposalId + " is no longer PENDING (status: " + proposal.getStatus() + ")");
        }
        return proposal;
    }

    private void verifyMembership(String groupId, String userId) {
        Optional<GroupMembership> membership = groupRepository.findMembership(groupId, userId);
        if (membership.isEmpty()) {
            throw new com.bbthechange.inviter.exception.UnauthorizedException(
                    "User " + userId + " is not a member of group " + groupId);
        }
    }

    private void adoptProposal(AttributeProposal proposal) {
        String hangoutId = proposal.getHangoutId();
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId).orElse(null);
        if (hangout == null) {
            logger.warn("Cannot adopt proposal {} — hangout {} not found", proposal.getProposalId(), hangoutId);
            proposal.setStatus(AttributeProposalStatus.REJECTED);
            proposalRepository.save(proposal);
            return;
        }

        // Apply the proposed value to the hangout
        if (AttributeProposalType.LOCATION.equals(proposal.getAttributeType())) {
            // proposedValue is the serialized location name (plain string for now)
            // If the hangout's existing location exists, update its name; otherwise set a simple address
            com.bbthechange.inviter.dto.Address location = hangout.getLocation();
            if (location == null) {
                location = new com.bbthechange.inviter.dto.Address();
            }
            location.setName(proposal.getProposedValue());
            hangout.setLocation(location);
        } else if (AttributeProposalType.DESCRIPTION.equals(proposal.getAttributeType())) {
            hangout.setDescription(proposal.getProposedValue());
        }

        hangoutRepository.createHangout(hangout); // putItem (upsert)
        logger.info("Auto-adopted proposal {} for hangout {} (type={}): applied value",
                proposal.getProposalId(), hangoutId, proposal.getAttributeType());

        // Mark proposal as ADOPTED
        proposal.setStatus(AttributeProposalStatus.ADOPTED);
        proposalRepository.save(proposal);

        // Trigger momentum recomputation if location was changed (adds a scoring signal)
        if (AttributeProposalType.LOCATION.equals(proposal.getAttributeType())) {
            // Pointer update for all associated groups (with optimistic locking retry)
            if (hangout.getAssociatedGroups() != null) {
                for (String gid : hangout.getAssociatedGroups()) {
                    pointerUpdateService.updatePointerWithRetry(gid, hangoutId,
                            pointer -> pointer.setLocation(hangout.getLocation()),
                            "attribute-proposal-adoption");
                }
            }
        }
    }

    private String resolveDisplayName(String userId) {
        try {
            return userService.getUserSummary(UUID.fromString(userId))
                    .map(u -> u.getDisplayName() != null ? u.getDisplayName() : "Someone")
                    .orElse("Someone");
        } catch (Exception e) {
            return "Someone";
        }
    }
}
