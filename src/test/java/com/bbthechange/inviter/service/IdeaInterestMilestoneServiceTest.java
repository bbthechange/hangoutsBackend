package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.IdeaListRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdeaInterestMilestoneService.
 *
 * Coverage:
 * - checkAndNotify: milestone detection, budget gating, conditional update race handling,
 *   recipient filtering, self-interest suppression
 * - computeInterestCount: creator implicit counting, null handling
 * - determineHighestReachedMilestone: threshold logic for all milestone tiers
 */
@ExtendWith(MockitoExtension.class)
class IdeaInterestMilestoneServiceTest {

    @Mock private GroupRepository groupRepository;
    @Mock private IdeaListRepository ideaListRepository;
    @Mock private NotificationService notificationService;
    @Mock private AdaptiveNotificationService adaptiveNotificationService;
    @Mock private UserService userService;
    @Mock private NotificationTextGenerator textGenerator;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private IdeaInterestMilestoneService service;

    private static final String GROUP_ID = "group-1";
    private static final String LIST_ID = "list-1";
    private static final String IDEA_ID = "idea-1";
    private static final String ADDER_ID = "adder-user";
    private static final String TRIGGERING_USER = "trigger-user";
    private static final String IDEA_NAME = "Sushi";

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        service = new IdeaInterestMilestoneService(
                groupRepository, ideaListRepository, notificationService,
                adaptiveNotificationService, userService, textGenerator, meterRegistry);
    }

    private IdeaListMember buildIdea(String addedBy, Set<String> interestedUserIds, String lastMilestoneSent) {
        IdeaListMember idea = new IdeaListMember();
        idea.setIdeaId(IDEA_ID);
        idea.setName(IDEA_NAME);
        idea.setAddedBy(addedBy);
        idea.setInterestedUserIds(interestedUserIds);
        idea.setLastMilestoneSent(lastMilestoneSent);
        return idea;
    }

    private List<GroupMembership> buildMembers(String... userIds) {
        List<GroupMembership> members = new ArrayList<>();
        for (String id : userIds) {
            GroupMembership m = new GroupMembership();
            m.setUserId(id);
            members.add(m);
        }
        return members;
    }

    private void stubUserName(String userId, String displayName) {
        lenient().when(userService.getUserSummary(UUID.fromString(userId)))
                .thenReturn(Optional.of(new UserSummaryDTO(UUID.fromString(userId), displayName, null)));
    }

    // =========================================================================
    // checkAndNotify tests
    // =========================================================================

    @Nested
    class CheckAndNotify {

        @Test
        void checkAndNotify_secondPersonInterested_sendsFirstInterest() {
            // Given: interestCount=2 (1 explicit triggeringUser + 1 implicit creator)
            // addedBy != triggeringUser
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validAdderId, Set.of(validTriggeringUser), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser,
                    "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                    "00000000-0000-0000-0000-000000000005");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");
            when(textGenerator.getFirstInterestBody("Alex", IDEA_NAME)).thenReturn("Alex is also into 'Sushi'");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then
            verify(notificationService).notifyIdeaInterestMilestone(
                    eq(GROUP_ID), eq(LIST_ID), eq(IDEA_ID),
                    eq(Set.of(validAdderId)),
                    eq("Alex is also into 'Sushi'"));
        }

        @Test
        void checkAndNotify_thirdPersonInterested_sendsBroadInterest() {
            // Given: interestCount=3 (2 explicit + 1 implicit creator)
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            String thirdUser = "00000000-0000-0000-0000-000000000003";
            String fourthUser = "00000000-0000-0000-0000-000000000004";
            IdeaListMember idea = buildIdea(validAdderId, Set.of(validTriggeringUser, thirdUser), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser, thirdUser, fourthUser);
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "BROAD_INTEREST", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");
            when(textGenerator.getBroadInterestAdderBody(IDEA_NAME, 3)).thenReturn("Your idea is popular");
            when(textGenerator.getBroadInterestBody(IDEA_NAME, 3)).thenReturn("3 people want to try Sushi");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: adder gets special copy
            verify(notificationService).notifyIdeaInterestMilestone(
                    eq(GROUP_ID), eq(LIST_ID), eq(IDEA_ID),
                    eq(Set.of(validAdderId)),
                    eq("Your idea is popular"));
            // Then: other members (minus triggering user, minus adder) get generic copy
            verify(notificationService).notifyIdeaInterestMilestone(
                    eq(GROUP_ID), eq(LIST_ID), eq(IDEA_ID),
                    eq(Set.of(thirdUser, fourthUser)),
                    eq("3 people want to try Sushi"));
        }

        @Test
        void checkAndNotify_groupConsensusReached_alwaysSendsRegardlessOfBudget() {
            // Given: groupSize=8, interestCount=4 (3 explicit + 1 implicit creator)
            // consensus threshold = max(4, ceil(8*0.5)) = 4
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            String user3 = "00000000-0000-0000-0000-000000000003";
            String user4 = "00000000-0000-0000-0000-000000000004";
            String user5 = "00000000-0000-0000-0000-000000000005";
            String user6 = "00000000-0000-0000-0000-000000000006";
            String user7 = "00000000-0000-0000-0000-000000000007";
            String user8 = "00000000-0000-0000-0000-000000000008";
            IdeaListMember idea = buildIdea(validAdderId,
                    Set.of(validTriggeringUser, user3, user4), null);

            List<GroupMembership> members = buildMembers(
                    validAdderId, validTriggeringUser, user3, user4, user5, user6, user7, user8);
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "GROUP_CONSENSUS", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");
            when(textGenerator.getGroupConsensusBody(IDEA_NAME)).thenReturn("Most of the group wants Sushi");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: hasBudgetForNotification should NOT be called (consensus bypasses budget)
            verify(adaptiveNotificationService, never()).hasBudgetForNotification(anyString(), anyString());
            // Notification sent to all members minus triggering user
            verify(notificationService).notifyIdeaInterestMilestone(
                    eq(GROUP_ID), eq(LIST_ID), eq(IDEA_ID),
                    argThat(recipients -> recipients.size() == 7 && !recipients.contains(validTriggeringUser)),
                    eq("Most of the group wants Sushi"));
        }

        @Test
        void checkAndNotify_milestoneAlreadySent_skips() {
            // Given: BROAD_INTEREST already sent, interestCount=3 would yield BROAD_INTEREST again
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validAdderId,
                    Set.of(validTriggeringUser, "00000000-0000-0000-0000-000000000003"), "BROAD_INTEREST");

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser,
                    "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                    "00000000-0000-0000-0000-000000000005");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: no conditional update, no notification
            verify(ideaListRepository, never()).updateLastMilestoneSentConditionally(
                    anyString(), anyString(), anyString(), anyString(), anyString());
            verify(notificationService, never()).notifyIdeaInterestMilestone(
                    anyString(), anyString(), anyString(), anyCollection(), anyString());
        }

        @Test
        void checkAndNotify_conditionalUpdateFails_skips() {
            // Given: milestone determined but conditional update returns false (race lost)
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validAdderId, Set.of(validTriggeringUser), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser,
                    "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                    "00000000-0000-0000-0000-000000000005");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null)).thenReturn(false);

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: no notification sent
            verify(notificationService, never()).notifyIdeaInterestMilestone(
                    anyString(), anyString(), anyString(), anyCollection(), anyString());
            // suppressed_duplicate metric recorded
            verify(meterRegistry).counter("idea_interest_milestone_total",
                    "status", "suppressed_duplicate", "milestone", "FIRST_INTEREST");
        }

        @Test
        void checkAndNotify_budgetExhausted_suppresses() {
            // Given: budget check returns false for non-consensus milestone
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validAdderId, Set.of(validTriggeringUser), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser,
                    "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                    "00000000-0000-0000-0000-000000000005");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(false);

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: no conditional update attempted, no notification
            verify(ideaListRepository, never()).updateLastMilestoneSentConditionally(
                    anyString(), anyString(), anyString(), anyString(), anyString());
            verify(notificationService, never()).notifyIdeaInterestMilestone(
                    anyString(), anyString(), anyString(), anyCollection(), anyString());
            // suppressed_budget metric recorded
            verify(meterRegistry).counter("idea_interest_milestone_total",
                    "status", "suppressed_budget", "milestone", "FIRST_INTEREST");
        }

        @Test
        void checkAndNotify_creatorSelfInterest_noDuplicateCount() {
            // Given: creator is in interestedUserIds, so computeInterestCount should NOT add +1
            // addedBy = "adder", interestedUserIds = {adder, trigger} -> count = 2 (not 3)
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validAdderId,
                    Set.of(validAdderId, validTriggeringUser), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser,
                    "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                    "00000000-0000-0000-0000-000000000005");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");
            when(textGenerator.getFirstInterestBody("Alex", IDEA_NAME)).thenReturn("body");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: should be FIRST_INTEREST (count=2), not BROAD_INTEREST (count=3)
            verify(ideaListRepository).updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null);
        }

        @Test
        void checkAndNotify_triggeringUserExcludedFromRecipients() {
            // Given: BROAD_INTEREST scenario, triggering user should be removed from recipients
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            String user3 = "00000000-0000-0000-0000-000000000003";
            IdeaListMember idea = buildIdea(validAdderId,
                    Set.of(validTriggeringUser, user3), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser, user3);
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "BROAD_INTEREST", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");
            when(textGenerator.getBroadInterestAdderBody(IDEA_NAME, 3)).thenReturn("adder body");
            when(textGenerator.getBroadInterestBody(IDEA_NAME, 3)).thenReturn("broad body");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: triggering user not in any recipient set
            ArgumentCaptor<Collection<String>> recipientCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(notificationService, atLeastOnce()).notifyIdeaInterestMilestone(
                    eq(GROUP_ID), eq(LIST_ID), eq(IDEA_ID),
                    recipientCaptor.capture(), anyString());
            for (Collection<String> recipients : recipientCaptor.getAllValues()) {
                assertThat(recipients).doesNotContain(validTriggeringUser);
            }
        }

        @Test
        void checkAndNotify_groupOfTwo_onlyFirstInterestPossible() {
            // Given: groupSize=2, interestCount=2
            // consensus threshold = max(4, ceil(2*0.5)) = 4, broad=3, both unreachable
            // so FIRST_INTEREST is the highest reachable
            String validAdderId = "00000000-0000-0000-0000-000000000001";
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validAdderId, Set.of(validTriggeringUser), null);

            List<GroupMembership> members = buildMembers(validAdderId, validTriggeringUser);
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");
            when(textGenerator.getFirstInterestBody("Alex", IDEA_NAME)).thenReturn("body");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: FIRST_INTEREST claimed, not BROAD_INTEREST
            verify(ideaListRepository).updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null);
        }

        @Test
        void checkAndNotify_firstInterest_adderIsTriggeringUser_doesNotClaimMilestone() {
            // Given: addedBy == triggeringUserId for FIRST_INTEREST
            String validUser = "00000000-0000-0000-0000-000000000001";
            String otherUser = "00000000-0000-0000-0000-000000000002";
            IdeaListMember idea = buildIdea(validUser, Set.of(otherUser), null);

            // interestCount = 2 (1 explicit + 1 implicit creator) -> FIRST_INTEREST
            // But addedBy == triggeringUser, so it should return early
            List<GroupMembership> members = buildMembers(validUser, otherUser,
                    "00000000-0000-0000-0000-000000000003", "00000000-0000-0000-0000-000000000004",
                    "00000000-0000-0000-0000-000000000005");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);

            // When: triggering user IS the adder
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validUser);

            // Then: no conditional update, no notification (milestone left unclaimed for next interest)
            verify(ideaListRepository, never()).updateLastMilestoneSentConditionally(
                    anyString(), anyString(), anyString(), anyString(), any());
            verify(notificationService, never()).notifyIdeaInterestMilestone(
                    anyString(), anyString(), anyString(), anyCollection(), anyString());
        }

        @Test
        void checkAndNotify_nullAddedBy_interestCountIsJustExplicit() {
            // Given: addedBy=null, 2 entries in interestedUserIds -> count = 2
            String validTriggeringUser = "00000000-0000-0000-0000-000000000002";
            String user3 = "00000000-0000-0000-0000-000000000003";
            IdeaListMember idea = buildIdea(null, Set.of(validTriggeringUser, user3), null);

            List<GroupMembership> members = buildMembers(validTriggeringUser, user3,
                    "00000000-0000-0000-0000-000000000004", "00000000-0000-0000-0000-000000000005",
                    "00000000-0000-0000-0000-000000000006");
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);
            when(adaptiveNotificationService.hasBudgetForNotification(eq(GROUP_ID), anyString())).thenReturn(true);
            // addedBy is null, so FIRST_INTEREST adder==triggering check is skipped (addedBy null guard)
            when(ideaListRepository.updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null)).thenReturn(true);
            stubUserName(validTriggeringUser, "Alex");

            // When
            service.checkAndNotify(GROUP_ID, LIST_ID, idea, validTriggeringUser);

            // Then: FIRST_INTEREST determined (count=2)
            verify(ideaListRepository).updateLastMilestoneSentConditionally(
                    GROUP_ID, LIST_ID, IDEA_ID, "FIRST_INTEREST", null);
            // addedBy is null, so FIRST_INTEREST notification to adder is skipped (null check in sendMilestoneNotification)
            verify(notificationService, never()).notifyIdeaInterestMilestone(
                    anyString(), anyString(), anyString(), anyCollection(), anyString());
        }
    }

    // =========================================================================
    // computeInterestCount tests
    // =========================================================================

    @Nested
    class ComputeInterestCount {

        @Test
        void computeInterestCount_nullInterestedUserIds_returnsOneForCreator() {
            // Given: no explicit interest, but creator counts as 1
            IdeaListMember idea = buildIdea("user1", null, null);

            // When
            int count = service.computeInterestCount(idea);

            // Then
            assertThat(count).isEqualTo(1);
        }

        @Test
        void computeInterestCount_emptySetTreatedAsNull_returnsOneForCreator() {
            // Given: IdeaListMember.setInterestedUserIds converts empty to null
            IdeaListMember idea = buildIdea("user1", new HashSet<>(), null);
            // The setter converts empty to null, so interestedUserIds is null

            // When
            int count = service.computeInterestCount(idea);

            // Then: null interestedUserIds + non-null addedBy = 1
            assertThat(count).isEqualTo(1);
        }
    }

    // =========================================================================
    // determineHighestReachedMilestone tests
    // =========================================================================

    @Nested
    class DetermineHighestReachedMilestone {

        @Test
        void determineHighestReachedMilestone_interestCountOne_returnsNull() {
            // When
            String result = service.determineHighestReachedMilestone(1, 10);

            // Then
            assertThat(result).isNull();
        }

        @Test
        void determineHighestReachedMilestone_interestCountTwo_returnsFirstInterest() {
            // When
            String result = service.determineHighestReachedMilestone(2, 10);

            // Then
            assertThat(result).isEqualTo("FIRST_INTEREST");
        }

        @Test
        void determineHighestReachedMilestone_interestCountThree_returnsBroadInterest() {
            // When
            String result = service.determineHighestReachedMilestone(3, 10);

            // Then
            assertThat(result).isEqualTo("BROAD_INTEREST");
        }

        @Test
        void determineHighestReachedMilestone_consensusReached_returnsGroupConsensus() {
            // groupSize=8, threshold = max(4, ceil(8*0.5)) = max(4,4) = 4
            // interestCount=5 >= 4 -> GROUP_CONSENSUS
            String result = service.determineHighestReachedMilestone(5, 8);

            assertThat(result).isEqualTo("GROUP_CONSENSUS");
        }

        @Test
        void determineHighestReachedMilestone_smallGroup_consensusUnreachable() {
            // groupSize=3, threshold = max(4, ceil(3*0.5)) = max(4,2) = 4
            // interestCount=3 < 4 -> BROAD_INTEREST, not GROUP_CONSENSUS
            String result = service.determineHighestReachedMilestone(3, 3);

            assertThat(result).isEqualTo("BROAD_INTEREST");
        }
    }
}
