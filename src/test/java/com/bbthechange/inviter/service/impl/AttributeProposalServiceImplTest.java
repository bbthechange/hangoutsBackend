package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.AttributeProposalDTO;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.AttributeProposal;
import com.bbthechange.inviter.model.AttributeProposalStatus;
import com.bbthechange.inviter.model.AttributeProposalType;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.AttributeProposalRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeProposalServiceImplTest {

    private static final String HANGOUT_ID = "12345678-1234-1234-1234-123456789012";
    private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PROPOSER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String OTHER_USER_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String PROPOSAL_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Mock
    private AttributeProposalRepository proposalRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private AttributeProposalServiceImpl service;

    private AttributeProposal buildPendingProposal(AttributeProposalType type) {
        AttributeProposal proposal = new AttributeProposal(HANGOUT_ID, GROUP_ID, PROPOSER_ID, type, "some value");
        // Override proposalId with deterministic ID for easier assertion
        proposal.setProposalId(PROPOSAL_ID);
        return proposal;
    }

    private GroupMembership buildMembership(String groupId, String userId) {
        GroupMembership m = new GroupMembership();
        m.setGroupId(groupId);
        m.setUserId(userId);
        return m;
    }

    private Hangout buildHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(HANGOUT_ID);
        hangout.setTitle("Test Hangout");
        hangout.setAssociatedGroups(Collections.singletonList(GROUP_ID));
        return hangout;
    }

    // ============================================================================
    // createProposal tests
    // ============================================================================

    @Nested
    class CreateProposal {

        @BeforeEach
        void setUp() {
            // Default: no existing PENDING proposals, hangout exists, notification works
            when(proposalRepository.findPendingByHangoutIdAndType(eq(HANGOUT_ID), any()))
                    .thenReturn(Collections.emptyList());
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID))
                    .thenReturn(Optional.of(buildHangout()));
            UserSummaryDTO user = new UserSummaryDTO();
            user.setDisplayName("Alice");
            when(userService.getUserSummary(UUID.fromString(PROPOSER_ID)))
                    .thenReturn(Optional.of(user));
        }

        @Test
        void createProposal_NewLocationProposal_SavesAndReturnsDTO() {
            AttributeProposalDTO result = service.createProposal(
                    HANGOUT_ID, GROUP_ID, PROPOSER_ID, AttributeProposalType.LOCATION, "Central Park");

            assertThat(result).isNotNull();
            assertThat(result.getHangoutId()).isEqualTo(HANGOUT_ID);
            assertThat(result.getAttributeType()).isEqualTo(AttributeProposalType.LOCATION);
            assertThat(result.getProposedValue()).isEqualTo("Central Park");
            assertThat(result.getStatus()).isEqualTo(AttributeProposalStatus.PENDING);
            verify(proposalRepository).save(any(AttributeProposal.class));
        }

        @Test
        void createProposal_ExistingPendingProposal_SupersedesOldOne() {
            AttributeProposal existing = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findPendingByHangoutIdAndType(HANGOUT_ID, AttributeProposalType.LOCATION))
                    .thenReturn(Collections.singletonList(existing));

            service.createProposal(HANGOUT_ID, GROUP_ID, PROPOSER_ID, AttributeProposalType.LOCATION, "New Location");

            // Should save the superseded old proposal + the new one = 2 saves total
            verify(proposalRepository, times(2)).save(any(AttributeProposal.class));
            assertThat(existing.getStatus()).isEqualTo(AttributeProposalStatus.SUPERSEDED);
        }

        @Test
        void createProposal_NotifiesGroupMembers() {
            service.createProposal(HANGOUT_ID, GROUP_ID, PROPOSER_ID, AttributeProposalType.DESCRIPTION, "New desc");

            verify(notificationService).notifyAttributeProposal(eq(GROUP_ID), eq(PROPOSER_ID), anyString());
        }

        @Test
        void createProposal_NotificationFails_DoesNotThrow() {
            org.mockito.Mockito.doThrow(new RuntimeException("push failed"))
                    .when(notificationService).notifyAttributeProposal(anyString(), anyString(), anyString());

            // Should not throw
            AttributeProposalDTO result = service.createProposal(
                    HANGOUT_ID, GROUP_ID, PROPOSER_ID, AttributeProposalType.LOCATION, "Park");
            assertThat(result).isNotNull();
        }

        @Test
        void createProposal_HangoutNotFound_UsesDefaultTitle() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.empty());

            // Should not throw — just uses "a hangout" fallback in notification
            AttributeProposalDTO result = service.createProposal(
                    HANGOUT_ID, GROUP_ID, PROPOSER_ID, AttributeProposalType.LOCATION, "Park");
            assertThat(result).isNotNull();
        }

        @Test
        void createProposal_MultipleExistingProposals_AllSuperseded() {
            // Two existing PENDING proposals for the same hangout + type
            AttributeProposal existing1 = buildPendingProposal(AttributeProposalType.LOCATION);
            AttributeProposal existing2 = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findPendingByHangoutIdAndType(HANGOUT_ID, AttributeProposalType.LOCATION))
                    .thenReturn(Arrays.asList(existing1, existing2));

            service.createProposal(HANGOUT_ID, GROUP_ID, PROPOSER_ID, AttributeProposalType.LOCATION, "New Park");

            // 2 superseded saves + 1 new proposal save = 3 total
            verify(proposalRepository, times(3)).save(any(AttributeProposal.class));
            assertThat(existing1.getStatus()).isEqualTo(AttributeProposalStatus.SUPERSEDED);
            assertThat(existing2.getStatus()).isEqualTo(AttributeProposalStatus.SUPERSEDED);
        }
    }

    // ============================================================================
    // listProposals tests
    // ============================================================================

    @Nested
    class ListProposals {

        @Test
        void listProposals_MemberWithPendingProposals_ReturnsList() {
            Hangout hangout = buildHangout();
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(GROUP_ID, PROPOSER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, PROPOSER_ID)));

            AttributeProposal pending = buildPendingProposal(AttributeProposalType.LOCATION);
            AttributeProposal adopted = buildPendingProposal(AttributeProposalType.DESCRIPTION);
            adopted.setStatus(AttributeProposalStatus.ADOPTED);
            when(proposalRepository.findByHangoutId(HANGOUT_ID))
                    .thenReturn(Arrays.asList(pending, adopted));

            List<AttributeProposalDTO> result = service.listProposals(HANGOUT_ID, PROPOSER_ID);

            // Only PENDING proposals should be returned
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getStatus()).isEqualTo(AttributeProposalStatus.PENDING);
        }

        @Test
        void listProposals_HangoutNotFound_ThrowsResourceNotFoundException() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listProposals(HANGOUT_ID, PROPOSER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void listProposals_NonMember_ThrowsUnauthorizedException() {
            Hangout hangout = buildHangout();
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(GROUP_ID, PROPOSER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listProposals(HANGOUT_ID, PROPOSER_ID))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void listProposals_NoPendingProposals_ReturnsEmptyList() {
            Hangout hangout = buildHangout();
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(GROUP_ID, PROPOSER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, PROPOSER_ID)));
            when(proposalRepository.findByHangoutId(HANGOUT_ID)).thenReturn(Collections.emptyList());

            List<AttributeProposalDTO> result = service.listProposals(HANGOUT_ID, PROPOSER_ID);

            assertThat(result).isEmpty();
        }
    }

    // ============================================================================
    // addAlternative tests
    // ============================================================================

    @Nested
    class AddAlternative {

        @Test
        void addAlternative_ValidRequest_AddsAlternativeAndInitializesVoteCounts() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AttributeProposalDTO result = service.addAlternative(
                    HANGOUT_ID, PROPOSAL_ID, "Alternative Location", OTHER_USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getAlternatives()).contains("Alternative Location");
            // voteCounts[0] = original, voteCounts[1] = first alternative
            assertThat(result.getVoteCounts()).hasSize(2);
            verify(proposalRepository).save(any(AttributeProposal.class));
        }

        @Test
        void addAlternative_NonMember_ThrowsUnauthorizedException() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addAlternative(
                    HANGOUT_ID, PROPOSAL_ID, "Alt", OTHER_USER_ID))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void addAlternative_ProposalNotFound_ThrowsResourceNotFoundException() {
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.addAlternative(
                    HANGOUT_ID, PROPOSAL_ID, "Alt", OTHER_USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void addAlternative_ProposalNotPending_ThrowsValidationException() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            proposal.setStatus(AttributeProposalStatus.ADOPTED);
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));

            assertThatThrownBy(() -> service.addAlternative(
                    HANGOUT_ID, PROPOSAL_ID, "Alt", OTHER_USER_ID))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void addAlternative_MultipleAlternatives_VoteCountsGrowCorrectly() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            // Pre-add one alternative
            proposal.setAlternatives(new ArrayList<>(Collections.singletonList("First Alt")));
            proposal.setVoteCounts(new ArrayList<>(Arrays.asList(0, 0)));
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AttributeProposalDTO result = service.addAlternative(
                    HANGOUT_ID, PROPOSAL_ID, "Second Alt", OTHER_USER_ID);

            // voteCounts should now be [0, 0, 0] — original + 2 alternatives
            assertThat(result.getVoteCounts()).hasSize(3);
            assertThat(result.getAlternatives()).hasSize(2);
        }
    }

    // ============================================================================
    // vote tests
    // ============================================================================

    @Nested
    class Vote {

        @Test
        void vote_OptionZero_VotesForOriginalProposedValue() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AttributeProposalDTO result = service.vote(HANGOUT_ID, PROPOSAL_ID, 0, OTHER_USER_ID);

            assertThat(result.getVoteCounts()).isNotNull();
            assertThat(result.getVoteCounts().get(0)).isEqualTo(1);
        }

        @Test
        void vote_ValidAlternativeIndex_IncrementsCorrectSlot() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            proposal.setAlternatives(new ArrayList<>(Collections.singletonList("Alt Location")));
            proposal.setVoteCounts(new ArrayList<>(Arrays.asList(0, 0)));
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AttributeProposalDTO result = service.vote(HANGOUT_ID, PROPOSAL_ID, 1, OTHER_USER_ID);

            assertThat(result.getVoteCounts().get(1)).isEqualTo(1);
            assertThat(result.getVoteCounts().get(0)).isEqualTo(0); // original unaffected
        }

        @Test
        void vote_InvalidOptionIndex_ThrowsValidationException() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            // No alternatives, so valid range is only 0
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));

            assertThatThrownBy(() -> service.vote(HANGOUT_ID, PROPOSAL_ID, 1, OTHER_USER_ID))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void vote_NonMember_ThrowsUnauthorizedException() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.vote(HANGOUT_ID, PROPOSAL_ID, 0, OTHER_USER_ID))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void vote_NegativeIndex_ThrowsValidationException() {
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));

            assertThatThrownBy(() -> service.vote(HANGOUT_ID, PROPOSAL_ID, -1, OTHER_USER_ID))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void vote_NullVoteCountsList_PaddedToSizeWithoutNPE() {
            // Proposal has 1 alternative but voteCounts is null
            AttributeProposal proposal = buildPendingProposal(AttributeProposalType.LOCATION);
            proposal.setAlternatives(new ArrayList<>(Collections.singletonList("Alt Location")));
            proposal.setVoteCounts(null); // null — service should pad gracefully
            when(proposalRepository.findById(HANGOUT_ID, PROPOSAL_ID))
                    .thenReturn(Optional.of(proposal));
            when(groupRepository.findMembership(GROUP_ID, OTHER_USER_ID))
                    .thenReturn(Optional.of(buildMembership(GROUP_ID, OTHER_USER_ID)));
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Should not throw; voteCounts should be initialized and index 1 incremented
            AttributeProposalDTO result = service.vote(HANGOUT_ID, PROPOSAL_ID, 1, OTHER_USER_ID);

            assertThat(result.getVoteCounts()).isNotNull();
            assertThat(result.getVoteCounts()).hasSize(2); // padded to [0, 1]
            assertThat(result.getVoteCounts().get(1)).isEqualTo(1);
        }
    }

    // ============================================================================
    // autoAdoptExpiredProposals tests
    // ============================================================================

    @Nested
    class AutoAdoptExpiredProposals {

        @Test
        void autoAdoptExpiredProposals_NoAlternatives_AdoptsProposal() {
            AttributeProposal expired = buildPendingProposal(AttributeProposalType.DESCRIPTION);
            // No alternatives → silence = consent
            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Collections.singletonList(expired));
            Hangout hangout = buildHangout();
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.autoAdoptExpiredProposals();

            assertThat(expired.getStatus()).isEqualTo(AttributeProposalStatus.ADOPTED);
            verify(hangoutRepository).createHangout(any(Hangout.class));
        }

        @Test
        void autoAdoptExpiredProposals_HasAlternatives_SkipsAutoAdoption() {
            AttributeProposal expired = buildPendingProposal(AttributeProposalType.LOCATION);
            expired.setAlternatives(new ArrayList<>(Collections.singletonList("Alt")));
            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Collections.singletonList(expired));

            service.autoAdoptExpiredProposals();

            // Should not touch the hangout or mark the proposal adopted
            assertThat(expired.getStatus()).isEqualTo(AttributeProposalStatus.PENDING);
            verify(hangoutRepository, never()).createHangout(any(Hangout.class));
        }

        @Test
        void autoAdoptExpiredProposals_HangoutNotFound_MarksRejected() {
            AttributeProposal expired = buildPendingProposal(AttributeProposalType.LOCATION);
            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Collections.singletonList(expired));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.empty());
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.autoAdoptExpiredProposals();

            assertThat(expired.getStatus()).isEqualTo(AttributeProposalStatus.REJECTED);
        }

        @Test
        void autoAdoptExpiredProposals_NoExpiredProposals_DoesNothing() {
            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Collections.emptyList());

            service.autoAdoptExpiredProposals();

            verify(hangoutRepository, never()).findHangoutById(anyString());
        }

        @Test
        void autoAdoptExpiredProposals_DescriptionProposal_AppliesDescriptionToHangout() {
            AttributeProposal expired = buildPendingProposal(AttributeProposalType.DESCRIPTION);
            expired.setProposedValue("Great new description");
            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Collections.singletonList(expired));
            Hangout hangout = buildHangout();
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenAnswer(inv -> {
                Hangout h = inv.getArgument(0);
                assertThat(h.getDescription()).isEqualTo("Great new description");
                return h;
            });
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.autoAdoptExpiredProposals();

            verify(hangoutRepository).createHangout(any(Hangout.class));
        }

        @Test
        void autoAdoptExpiredProposals_LocationProposal_SetsLocationNameOnHangout() {
            AttributeProposal expired = buildPendingProposal(AttributeProposalType.LOCATION);
            expired.setProposedValue("Central Park");
            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Collections.singletonList(expired));
            Hangout hangout = buildHangout();
            hangout.setLocation(null); // No existing location
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenAnswer(inv -> {
                Hangout h = inv.getArgument(0);
                assertThat(h.getLocation()).isNotNull();
                assertThat(h.getLocation().getName()).isEqualTo("Central Park");
                return h;
            });
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.autoAdoptExpiredProposals();

            verify(hangoutRepository).createHangout(any(Hangout.class));
            assertThat(expired.getStatus()).isEqualTo(AttributeProposalStatus.ADOPTED);
        }

        @Test
        void autoAdoptExpiredProposals_ExceptionDuringAdoption_ContinuesProcessingOtherProposals() {
            AttributeProposal broken = buildPendingProposal(AttributeProposalType.LOCATION);
            String brokenHangoutId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
            broken.setHangoutId(brokenHangoutId);
            broken.setPk("EVENT#" + brokenHangoutId);

            AttributeProposal good = buildPendingProposal(AttributeProposalType.DESCRIPTION);

            when(proposalRepository.findExpiredPendingProposals(any(Long.class)))
                    .thenReturn(Arrays.asList(broken, good));
            when(hangoutRepository.findHangoutById(brokenHangoutId))
                    .thenThrow(new RuntimeException("DynamoDB error"));
            Hangout hangout = buildHangout();
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
            when(proposalRepository.save(any(AttributeProposal.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Should not throw — processes good proposal despite broken one
            service.autoAdoptExpiredProposals();

            assertThat(good.getStatus()).isEqualTo(AttributeProposalStatus.ADOPTED);
        }
    }
}
