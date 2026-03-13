package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.GroupNotificationTracker;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.repository.GroupNotificationTrackerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdaptiveNotificationService.
 *
 * Coverage:
 * - shouldSendNotification: concrete actions always notify, explicit confirmations always notify,
 *   state changes respect weekly budget, same-state no-op
 * - recordNotificationSent: increments weekly counter
 * - rolloverIfNeeded: rolls over week counter into rolling average
 * - computeWeeklyBudget: new group defaults, budget based on rolling average
 * - currentWeekKey: returns well-formed ISO week string
 * - message builder static helpers
 */
@ExtendWith(MockitoExtension.class)
class AdaptiveNotificationServiceTest {

    @Mock
    private GroupNotificationTrackerRepository trackerRepository;

    @InjectMocks
    private AdaptiveNotificationService service;

    private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";

    // ============================================================================
    // HELPERS
    // ============================================================================

    private GroupNotificationTracker freshTracker(String weekKey, int sentThisWeek, double rollingAvg) {
        GroupNotificationTracker t = new GroupNotificationTracker();
        t.setGroupId(GROUP_ID);
        t.setPk("GROUP#" + GROUP_ID);
        t.setSk("NOTIFICATION_TRACKER");
        t.setWeekKey(weekKey);
        t.setNotificationsSentThisWeek(sentThisWeek);
        t.setRollingWeeklyAverage(rollingAvg);
        return t;
    }

    // ============================================================================
    // shouldSendNotification
    // ============================================================================

    @Nested
    class ShouldSendNotification {

        @Test
        void concreteAction_alwaysReturnsTrue_regardlessOfBudget() {
            // Group tracker with budget already exhausted (2/2 sent)
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 2, 2.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_CONCRETE_ACTION,
                    MomentumCategory.BUILDING,
                    MomentumCategory.CONFIRMED);

            assertThat(result).isTrue();
        }

        @Test
        void explicitConfirmation_alwaysReturnsTrue_regardlessOfBudget() {
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 5, 5.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_CONFIRMED,
                    MomentumCategory.BUILDING,
                    MomentumCategory.CONFIRMED);

            assertThat(result).isTrue();
        }

        @Test
        void momentumStateChange_withinBudget_returnsTrue() {
            // New group → default budget of 2, no notifications sent yet
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING,
                    MomentumCategory.BUILDING,
                    MomentumCategory.GAINING_MOMENTUM);

            assertThat(result).isTrue();
        }

        @Test
        void momentumStateChange_budgetExhausted_returnsFalse() {
            // Already sent 2 notifications, budget is 2
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 2, 0.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING,
                    MomentumCategory.BUILDING,
                    MomentumCategory.GAINING_MOMENTUM);

            assertThat(result).isFalse();
        }

        @Test
        void nullGroupId_returnsFalse() {
            boolean result = service.shouldSendNotification(
                    null,
                    AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING,
                    MomentumCategory.BUILDING,
                    MomentumCategory.GAINING_MOMENTUM);

            assertThat(result).isFalse();
            verifyNoInteractions(trackerRepository);
        }

        @Test
        void sameCategory_returnsFalse() {
            // No state change — no notification regardless
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 0.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_GENERAL,
                    MomentumCategory.BUILDING,
                    MomentumCategory.BUILDING);

            assertThat(result).isFalse();
        }

        @Test
        void shouldSendNotification_withinBudget_returnsTrue() {
            // rollingAverage=2.0 → budget=ceil(2.0 * 1.5)=3; sentThisWeek=1 → still under budget
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 1, 2.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_GENERAL,
                    MomentumCategory.BUILDING,
                    MomentumCategory.GAINING_MOMENTUM);

            assertThat(result).isTrue();
        }

        @Test
        void shouldSendNotification_budgetExhausted_returnsFalse() {
            // rollingAverage=2.0 → budget=3; sentThisWeek=3 → at budget, no more
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 3, 2.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_GENERAL,
                    MomentumCategory.BUILDING,
                    MomentumCategory.GAINING_MOMENTUM);

            assertThat(result).isFalse();
        }

        @Test
        void shouldSendNotification_newGroup_defaultBudgetIsTwo() {
            // No tracker found → loadOrCreate returns fresh tracker with 0 sent → budget=2 → true
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());

            boolean result = service.shouldSendNotification(
                    GROUP_ID,
                    AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING,
                    MomentumCategory.BUILDING,
                    MomentumCategory.GAINING_MOMENTUM);

            assertThat(result).isTrue();
        }
    }

    // ============================================================================
    // recordNotificationSent
    // ============================================================================

    @Nested
    class RecordNotificationSent {

        @Test
        void recordNotificationSent_incrementsCounterAndSaves() {
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 1, 1.5);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            service.recordNotificationSent(GROUP_ID, AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING);

            ArgumentCaptor<GroupNotificationTracker> captor = ArgumentCaptor.forClass(GroupNotificationTracker.class);
            verify(trackerRepository).save(captor.capture());

            GroupNotificationTracker saved = captor.getValue();
            assertThat(saved.getNotificationsSentThisWeek()).isEqualTo(2);
            assertThat(saved.getSignalCounts())
                    .containsEntry(AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING, 1);
        }

        @Test
        void recordNotificationSent_updatesSignalCounts() {
            // Fresh tracker with no prior signal counts
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 0.0);
            when(trackerRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(tracker));

            service.recordNotificationSent(GROUP_ID, AdaptiveNotificationService.SIGNAL_GENERAL);

            ArgumentCaptor<GroupNotificationTracker> captor = ArgumentCaptor.forClass(GroupNotificationTracker.class);
            verify(trackerRepository).save(captor.capture());
            assertThat(captor.getValue().getSignalCounts())
                    .containsEntry(AdaptiveNotificationService.SIGNAL_GENERAL, 1);
        }

        @Test
        void recordNotificationSent_nullGroupId_doesNothing() {
            service.recordNotificationSent(null, AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING);
            verifyNoInteractions(trackerRepository);
        }
    }

    // ============================================================================
    // rolloverIfNeeded
    // ============================================================================

    @Nested
    class RolloverIfNeeded {

        @Test
        void sameWeekKey_noRollover() {
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 3, 2.0);
            int before = tracker.getNotificationsSentThisWeek();

            service.rolloverIfNeeded(tracker);

            assertThat(tracker.getNotificationsSentThisWeek()).isEqualTo(before);
        }

        @Test
        void differentWeekKey_resetsCounterAndUpdatesAverage() {
            // Tracker from a past week
            GroupNotificationTracker tracker = freshTracker("2020-W01", 4, 2.0);

            service.rolloverIfNeeded(tracker);

            assertThat(tracker.getNotificationsSentThisWeek()).isEqualTo(0);
            assertThat(tracker.getWeekKey()).isEqualTo(service.currentWeekKey());
            // rolling average should have updated (4 folded in)
            assertThat(tracker.getRollingWeeklyAverage()).isGreaterThan(0.0);
        }

        @Test
        void firstRollover_withZeroAverage_setsAverageToWeekCount() {
            // Brand new tracker from a past week
            GroupNotificationTracker tracker = freshTracker("2020-W01", 3, 0.0);

            service.rolloverIfNeeded(tracker);

            // When old avg is 0, new avg = weekCount
            assertThat(tracker.getRollingWeeklyAverage()).isEqualTo(3.0);
        }

        @Test
        void rolloverIfNeeded_newWeek_exponentialMovingAverage() {
            // rollingAverage=4.0, sentThisWeek=8 → newAvg = (1/8)*8 + (7/8)*4.0 = 1.0 + 3.5 = 4.5
            GroupNotificationTracker tracker = freshTracker("2026-W01", 8, 4.0);

            service.rolloverIfNeeded(tracker);

            assertThat(tracker.getRollingWeeklyAverage()).isCloseTo(4.5, org.assertj.core.data.Offset.offset(0.001));
            assertThat(tracker.getNotificationsSentThisWeek()).isEqualTo(0);
            assertThat(tracker.getWeekKey()).isEqualTo(service.currentWeekKey());
        }
    }

    // ============================================================================
    // computeWeeklyBudget
    // ============================================================================

    @Nested
    class ComputeWeeklyBudget {

        @Test
        void newGroup_returnsDefaultBudget() {
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 0.0);

            int budget = service.computeWeeklyBudget(tracker);

            assertThat(budget).isEqualTo(2); // DEFAULT_WEEKLY_BUDGET
        }

        @Test
        void groupWithHistory_returnsBudgetBasedOnAverage() {
            // Rolling average of 4 → budget = ceil(4 * 1.5) = 6
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 4.0);

            int budget = service.computeWeeklyBudget(tracker);

            assertThat(budget).isEqualTo(6);
        }

        @Test
        void groupWithLowHistory_returnsAtLeastDefaultBudget() {
            // Rolling average of 0.5 → ceil(0.5 * 1.5) = 1, but minimum is 2
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 0.5);

            int budget = service.computeWeeklyBudget(tracker);

            assertThat(budget).isGreaterThanOrEqualTo(2);
        }

        @Test
        void computeWeeklyBudget_averageOfTwo_returnsThree() {
            // rollingAverage=2.0 → ceil(2.0 * 1.5)=3
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 2.0);

            int budget = service.computeWeeklyBudget(tracker);

            assertThat(budget).isEqualTo(3);
        }

        @Test
        void computeWeeklyBudget_averageOfOne_returnsMinimumTwo() {
            // rollingAverage=1.0 → ceil(1.0 * 1.5)=2 which equals the minimum
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 1.0);

            int budget = service.computeWeeklyBudget(tracker);

            assertThat(budget).isEqualTo(2);
        }

        @Test
        void computeWeeklyBudget_highAverage_scalesUp() {
            // rollingAverage=6.0 → ceil(6.0 * 1.5)=9
            GroupNotificationTracker tracker = freshTracker(service.currentWeekKey(), 0, 6.0);

            int budget = service.computeWeeklyBudget(tracker);

            assertThat(budget).isEqualTo(9);
        }
    }

    // ============================================================================
    // currentWeekKey
    // ============================================================================

    @Nested
    class CurrentWeekKey {

        @Test
        void currentWeekKey_returnsWellFormedString() {
            String key = service.currentWeekKey();

            // Expected format: "YYYY-WNN"
            assertThat(key).matches("\\d{4}-W\\d{2}");
        }
    }

    // ============================================================================
    // Static message builders
    // ============================================================================

    @Nested
    class MessageBuilders {

        @Test
        void gainingTractionMessage_singlePerson_usesIsSingular() {
            String msg = AdaptiveNotificationService.gainingTractionMessage("Beach Day", 1);
            assertThat(msg).contains("1 person is");
        }

        @Test
        void gainingTractionMessage_multiplePeople_usesArePlural() {
            String msg = AdaptiveNotificationService.gainingTractionMessage("Beach Day", 5);
            assertThat(msg).contains("5 people are");
        }

        @Test
        void ticketPurchasedMessage_withName_includesName() {
            String msg = AdaptiveNotificationService.ticketPurchasedMessage("Alex", "Concert");
            assertThat(msg).isEqualTo("Alex bought tickets for 'Concert'");
        }

        @Test
        void ticketPurchasedMessage_nullName_genericMessage() {
            String msg = AdaptiveNotificationService.ticketPurchasedMessage(null, "Concert");
            assertThat(msg).contains("Concert");
            assertThat(msg).doesNotContain("null");
        }

        @Test
        void ticketPurchasedMessage_blankActorName_usesFallback() {
            String msg = AdaptiveNotificationService.ticketPurchasedMessage("  ", "Concert Night");
            assertThat(msg).isEqualTo("Tickets were purchased for 'Concert Night'");
        }

        @Test
        void actionNudgeMessage_includesDayAndTitle() {
            String msg = AdaptiveNotificationService.actionNudgeMessage("Pool Party", "Friday");
            assertThat(msg).contains("Pool Party");
            assertThat(msg).contains("Friday");
        }

        @Test
        void emptyWeekMessage_returnsExpectedText() {
            String msg = AdaptiveNotificationService.emptyWeekMessage();
            assertThat(msg).contains("Nothing planned next week");
        }
    }
}
