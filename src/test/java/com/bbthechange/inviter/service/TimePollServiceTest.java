package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.PollOption;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.model.Vote;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.impl.PointerUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimePollServiceTest {

    @Mock private HangoutRepository hangoutRepository;
    @Mock private PointerUpdateService pointerUpdateService;
    @Mock private GroupTimestampService groupTimestampService;
    @Mock private FuzzyTimeService fuzzyTimeService;
    @Mock private MomentumService momentumService;
    @Mock private TimePollScheduler scheduler;

    @InjectMocks
    private TimePollService service;

    private String hangoutId;
    private String pollId;

    @BeforeEach
    void setUp() {
        hangoutId = UUID.randomUUID().toString();
        pollId = UUID.randomUUID().toString();
    }

    // ============================================================================
    // onPollCreated / onOptionAdded — scheduling
    // ============================================================================

    @Test
    void onPollCreated_schedulesBothAndPersistsTarget() {
        Poll poll = timePoll();
        when(scheduler.scheduleInitial(eq(hangoutId), eq(pollId), any())).thenReturn(12345L);

        service.onPollCreated(poll);

        assertThat(poll.getScheduledFinalAdoptionAt()).isEqualTo(12345L);
        verify(hangoutRepository).savePoll(poll);
    }

    @Test
    void onPollCreated_ignoresNonTimePolls() {
        Poll poll = new Poll(hangoutId, "title", "desc", false);
        poll.setAttributeType("LOCATION");
        poll.setPollId(pollId);

        service.onPollCreated(poll);

        verify(scheduler, never()).scheduleInitial(anyString(), anyString(), any());
    }

    @Test
    void onOptionAdded_slidesWhenRunwayTooShort() {
        Poll poll = timePoll();
        long now = System.currentTimeMillis();
        poll.setScheduledFinalAdoptionAt(now + 60_000L); // < 24h remaining
        when(scheduler.reschedule48h(eq(hangoutId), eq(pollId), anyLong())).thenAnswer(inv -> inv.getArgument(2));

        service.onOptionAdded(poll);

        ArgumentCaptor<Long> fireAt = ArgumentCaptor.forClass(Long.class);
        verify(scheduler).reschedule48h(eq(hangoutId), eq(pollId), fireAt.capture());
        assertThat(fireAt.getValue()).isGreaterThan(now + 23L * 60 * 60 * 1000);
        verify(hangoutRepository).savePoll(poll);
    }

    @Test
    void onOptionAdded_noSlideWhenRunwayOk() {
        Poll poll = timePoll();
        poll.setScheduledFinalAdoptionAt(System.currentTimeMillis() + 40L * 60 * 60 * 1000);

        service.onOptionAdded(poll);

        verify(scheduler, never()).reschedule48h(anyString(), anyString(), anyLong());
    }

    @Test
    void onOptionAdded_skipsAlreadyPromotedPoll() {
        Poll poll = timePoll();
        poll.setPromotedAt(1L);

        service.onOptionAdded(poll);

        verify(scheduler, never()).reschedule48h(anyString(), anyString(), anyLong());
    }

    // ============================================================================
    // evaluateAndAdopt — 5-way matrix
    // ============================================================================

    @Nested
    class EvaluatorMatrix {

        @Test
        void zeroOptions_isPendingGuard() {
            Poll poll = pollAgedHours(49);
            mockPollData(poll, List.of(), List.of());

            service.evaluateAndAdopt(hangoutId, pollId);

            verify(hangoutRepository, never()).save(any(Hangout.class));
        }

        @Test
        void singleOptionZeroVotes_readyAt48h() {
            Poll poll = pollAgedHours(49);
            PollOption opt = option();
            mockPollData(poll, List.of(opt), List.of());
            mockHangoutAdoption(opt.getTimeInput());

            service.evaluateAndAdopt(hangoutId, pollId);

            assertAdopted(poll);
        }

        @Test
        void singleOptionZeroVotes_notReadyBefore48h() {
            Poll poll = pollAgedHours(47);
            mockPollData(poll, List.of(option()), List.of());

            service.evaluateAndAdopt(hangoutId, pollId);

            assertNotAdopted(poll);
        }

        @Test
        void singleOptionOneVote_fastPathAt24h() {
            Poll poll = pollAgedHours(25);
            PollOption opt = option();
            mockPollData(poll, List.of(opt), List.of(voteFor(opt)));
            mockHangoutAdoption(opt.getTimeInput());

            service.evaluateAndAdopt(hangoutId, pollId);

            assertAdopted(poll);
        }

        @Test
        void singleOptionOneVote_notReadyBefore24h() {
            Poll poll = pollAgedHours(23);
            PollOption opt = option();
            mockPollData(poll, List.of(opt), List.of(voteFor(opt)));

            service.evaluateAndAdopt(hangoutId, pollId);

            assertNotAdopted(poll);
        }

        @Test
        void multiOptionsOneVote_readyAt48h_permissiveLeader() {
            Poll poll = pollAgedHours(49);
            PollOption winner = option();
            PollOption loser = option();
            mockPollData(poll, List.of(loser, winner), List.of(voteFor(winner)));
            mockHangoutAdoption(winner.getTimeInput());

            service.evaluateAndAdopt(hangoutId, pollId);

            assertAdopted(poll);
        }

        @Test
        void multiOptionsTwoVoted_contestedNeverReady() {
            Poll poll = pollAgedHours(49);
            PollOption a = option();
            PollOption b = option();
            mockPollData(poll, List.of(a, b), List.of(voteFor(a), voteFor(b)));

            service.evaluateAndAdopt(hangoutId, pollId);

            assertNotAdopted(poll);
        }

        @Test
        void idempotent_onAlreadyPromotedPoll() {
            Poll poll = timePoll();
            poll.setPromotedAt(999L);
            mockPollData(poll, List.of(), List.of());

            service.evaluateAndAdopt(hangoutId, pollId);

            verify(hangoutRepository, never()).savePoll(any());
            verify(hangoutRepository, never()).save(any(Hangout.class));
        }
    }

    // ============================================================================
    // onSupersede / onPollDeleted / onHangoutDeleted
    // ============================================================================

    @Test
    void onSupersede_flipsActivePollsInactiveAndCancelsSchedules() {
        Poll active = timePoll();
        active.setPollId(UUID.randomUUID().toString());
        Poll inactive = timePoll();
        inactive.setPollId(UUID.randomUUID().toString());
        inactive.setActive(false);
        Poll promoted = timePoll();
        promoted.setPollId(UUID.randomUUID().toString());
        promoted.setPromotedAt(1L);

        when(hangoutRepository.getAllPollData(hangoutId)).thenReturn(List.of(active, inactive, promoted));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(new Hangout()));

        service.onSupersede(hangoutId);

        assertThat(active.isActive()).isFalse();
        verify(hangoutRepository).savePoll(active);
        verify(scheduler).cancelBoth(active.getPollId());
        // inactive and promoted were skipped
        verify(hangoutRepository, never()).savePoll(inactive);
        verify(hangoutRepository, never()).savePoll(promoted);
    }

    @Test
    void onPollDeleted_cancelsSchedules() {
        service.onPollDeleted(pollId);
        verify(scheduler).cancelBoth(pollId);
    }

    @Test
    void onHangoutDeleted_cancelsSchedulesForEveryTimePoll() {
        Poll a = timePoll();
        a.setPollId(UUID.randomUUID().toString());
        Poll b = timePoll();
        b.setPollId(UUID.randomUUID().toString());
        Poll locationPoll = new Poll(hangoutId, "x", "y", false);
        locationPoll.setAttributeType("LOCATION");
        locationPoll.setPollId(UUID.randomUUID().toString());

        when(hangoutRepository.getAllPollData(hangoutId))
            .thenReturn(List.of(a, b, locationPoll));

        service.onHangoutDeleted(hangoutId);

        verify(scheduler).cancelBoth(a.getPollId());
        verify(scheduler).cancelBoth(b.getPollId());
        verify(scheduler, never()).cancelBoth(locationPoll.getPollId());
    }

    // ============================================================================
    // helpers
    // ============================================================================

    private Poll timePoll() {
        Poll poll = new Poll(hangoutId, "title", "desc", false);
        poll.setAttributeType("TIME");
        poll.setPollId(pollId);
        // reset pollId in case the caller wants a shared instance — each test can reassign
        return poll;
    }

    private Poll pollAgedHours(long hours) {
        Poll poll = timePoll();
        poll.setCreatedAt(Instant.now().minusSeconds(hours * 3600));
        return poll;
    }

    private PollOption option() {
        PollOption opt = new PollOption(hangoutId, pollId, "text");
        TimeInfo info = new TimeInfo();
        opt.setTimeInput(info);
        return opt;
    }

    private Vote voteFor(PollOption opt) {
        return new Vote(hangoutId, pollId, opt.getOptionId(), UUID.randomUUID().toString(), "YES");
    }

    @SuppressWarnings("unchecked")
    private void mockPollData(Poll poll, List<PollOption> options, List<Vote> votes) {
        java.util.List<BaseItem> items = new java.util.ArrayList<>();
        items.add(poll);
        items.addAll(options);
        items.addAll(votes);
        when(hangoutRepository.getSpecificPollData(hangoutId, pollId)).thenReturn(items);
    }

    private void mockHangoutAdoption(TimeInfo winningInput) {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(fuzzyTimeService.convert(winningInput))
            .thenReturn(new FuzzyTimeService.TimeConversionResult(1000L, 2000L));
    }

    private void assertAdopted(Poll poll) {
        assertThat(poll.getPromotedAt()).isNotNull();
        assertThat(poll.isActive()).isFalse();
        verify(hangoutRepository).savePoll(poll);
        verify(scheduler).cancelBoth(pollId);
        verify(hangoutRepository).save(any(Hangout.class));
        verify(momentumService).recomputeMomentum(hangoutId);
    }

    private void assertNotAdopted(Poll poll) {
        assertThat(poll.getPromotedAt()).isNull();
        verify(hangoutRepository, never()).savePoll(any());
        verify(hangoutRepository, never()).save(any(Hangout.class));
    }
}
