package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.Poll;
import com.bbthechange.inviter.model.PollOption;
import com.bbthechange.inviter.model.Vote;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.impl.PointerUpdateService;
import com.bbthechange.inviter.util.HangoutPointerFactory;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Handles the lifecycle of TIME polls: EventBridge scheduling, the 5-way adoption matrix,
 * and supersession on direct hangout edits.
 *
 * See {@code context/TIME_POLL_MIGRATION_PLAN.md} §TIME evaluation rule and §Scheduling
 * model for the authoritative behaviors.
 */
@Service
public class TimePollService {

    private static final Logger logger = LoggerFactory.getLogger(TimePollService.class);

    private static final long SHORT_WINDOW_MS = TimePollScheduler.SHORT_WINDOW_MS;
    private static final long LONG_WINDOW_MS = TimePollScheduler.LONG_WINDOW_MS;

    private final HangoutRepository hangoutRepository;
    private final PointerUpdateService pointerUpdateService;
    private final GroupTimestampService groupTimestampService;
    private final FuzzyTimeService fuzzyTimeService;
    private final MomentumService momentumService;
    private final TimePollScheduler scheduler;

    @Autowired
    public TimePollService(HangoutRepository hangoutRepository,
                           PointerUpdateService pointerUpdateService,
                           GroupTimestampService groupTimestampService,
                           FuzzyTimeService fuzzyTimeService,
                           @Lazy MomentumService momentumService,
                           TimePollScheduler scheduler) {
        this.hangoutRepository = hangoutRepository;
        this.pointerUpdateService = pointerUpdateService;
        this.groupTimestampService = groupTimestampService;
        this.fuzzyTimeService = fuzzyTimeService;
        this.momentumService = momentumService;
        this.scheduler = scheduler;
    }

    // ============================================================================
    // LIFECYCLE HOOKS (called from PollServiceImpl / HangoutServiceImpl)
    // ============================================================================

    /**
     * Schedule both 24h and 48h adoption checks for a newly created TIME poll. Persists
     * the 48h target as {@code scheduledFinalAdoptionAt} on the poll.
     */
    public void onPollCreated(Poll poll) {
        if (poll == null || !"TIME".equals(poll.getAttributeType())) {
            return;
        }
        Instant createdAt = poll.getCreatedAt();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        try {
            Long fireAtMs = scheduler.scheduleInitial(poll.getEventId(), poll.getPollId(), createdAt);
            if (fireAtMs != null) {
                poll.setScheduledFinalAdoptionAt(fireAtMs);
                hangoutRepository.savePoll(poll);
            }
        } catch (Exception e) {
            logger.warn("Failed to schedule adoption for TIME poll {} — adoption will not auto-trigger: {}",
                poll.getPollId(), e.getMessage());
        }
    }

    /**
     * Slide the 48h schedule forward if {@code now + 24h > scheduledFinalAdoptionAt}. No-op
     * otherwise. Safe to call on every option-add without pre-checking.
     */
    public void onOptionAdded(Poll poll) {
        if (poll == null || !"TIME".equals(poll.getAttributeType())) {
            return;
        }
        if (!poll.isActive() || poll.getPromotedAt() != null) {
            return;
        }
        long now = System.currentTimeMillis();
        long targetMs = now + SHORT_WINDOW_MS;
        Long current = poll.getScheduledFinalAdoptionAt();
        if (current != null && current >= targetMs) {
            return; // already has ≥ 24h runway
        }
        try {
            Long newTarget = scheduler.reschedule48h(poll.getEventId(), poll.getPollId(), targetMs);
            if (newTarget != null) {
                poll.setScheduledFinalAdoptionAt(newTarget);
                hangoutRepository.savePoll(poll);
                logger.info("Slid 48h adoption for poll {} to {}", poll.getPollId(), newTarget);
            }
        } catch (Exception e) {
            logger.warn("Failed to slide adoption schedule for poll {}: {}",
                poll.getPollId(), e.getMessage());
        }
    }

    /**
     * EventBridge handler entry. Re-evaluates the full 5-way matrix and adopts the winning
     * option or no-ops. Idempotent on already-promoted polls.
     *
     * <p>Signature matches the migration plan ({@code evaluateAndAdopt(String pollId)}); the
     * {@code hangoutId} is threaded in via the scheduler payload so we don't need a GSI.
     */
    public void evaluateAndAdopt(String hangoutId, String pollId) {
        List<BaseItem> pollData = hangoutRepository.getSpecificPollData(hangoutId, pollId);
        Poll poll = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .findFirst()
            .orElse(null);
        if (poll == null) {
            logger.info("evaluateAndAdopt: poll {} not found for hangout {} — no-op", pollId, hangoutId);
            return;
        }
        if (!"TIME".equals(poll.getAttributeType())) {
            return;
        }
        if (!poll.isActive() || poll.getPromotedAt() != null) {
            logger.info("evaluateAndAdopt: poll {} already inactive/promoted — no-op", pollId);
            return;
        }

        List<PollOption> options = pollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.toList());
        List<Vote> votes = pollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.toList());

        Map<String, Long> votesByOption = votes.stream()
            .collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));

        long now = System.currentTimeMillis();
        long createdAtMs = poll.getCreatedAt() != null ? poll.getCreatedAt().toEpochMilli() : now;
        long age = now - createdAtMs;

        int totalOptions = options.size();
        long votedOptionCount = options.stream()
            .filter(opt -> votesByOption.getOrDefault(opt.getOptionId(), 0L) >= 1)
            .count();

        PollOption winner = null;
        if (totalOptions == 0) {
            return; // PENDING guard
        } else if (totalOptions == 1 && votedOptionCount == 0 && age >= LONG_WINDOW_MS) {
            winner = options.get(0); // silent consent
        } else if (totalOptions == 1 && votedOptionCount == 1 && age >= SHORT_WINDOW_MS) {
            winner = options.get(0); // fast path
        } else if (totalOptions >= 2 && votedOptionCount == 1 && age >= LONG_WINDOW_MS) {
            winner = options.stream()
                .filter(opt -> votesByOption.getOrDefault(opt.getOptionId(), 0L) >= 1)
                .findFirst()
                .orElse(null); // permissive leader
        }

        if (winner == null) {
            logger.info("evaluateAndAdopt: poll {} not READY (options={}, voted={}, ageMs={})",
                pollId, totalOptions, votedOptionCount, age);
            return;
        }

        adopt(poll, winner);
    }

    /**
     * Flip every active TIME poll on a hangout inactive and cancel both schedules per poll.
     * Called when the hangout's time is set directly (bypassing the poll flow).
     */
    public void onSupersede(String hangoutId) {
        List<BaseItem> allPollData = hangoutRepository.getAllPollData(hangoutId);
        List<Poll> activeTimePolls = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .filter(p -> "TIME".equals(p.getAttributeType())
                      && p.isActive()
                      && p.getPromotedAt() == null)
            .collect(Collectors.toList());

        if (activeTimePolls.isEmpty()) {
            return;
        }

        for (Poll poll : activeTimePolls) {
            poll.setActive(false);
            hangoutRepository.savePoll(poll);
            cancelSchedulesFor(poll.getPollId());
        }

        // Roll pointer polls list so the feed reflects the inactive state, and roll the
        // group ETag so clients refetch.
        updatePollPointers(hangoutId);
        logger.info("Superseded {} active TIME poll(s) on hangout {}", activeTimePolls.size(), hangoutId);
    }

    /**
     * Cancel both schedules for a deleted poll. Called before the poll's row goes away.
     */
    public void onPollDeleted(String pollId) {
        cancelSchedulesFor(pollId);
    }

    /**
     * Cancel schedules for every TIME poll on a hangout. Called before hangout deletion.
     */
    public void onHangoutDeleted(String hangoutId) {
        try {
            List<BaseItem> allPollData = hangoutRepository.getAllPollData(hangoutId);
            allPollData.stream()
                .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
                .map(item -> (Poll) item)
                .filter(p -> "TIME".equals(p.getAttributeType()))
                .forEach(p -> cancelSchedulesFor(p.getPollId()));
        } catch (Exception e) {
            logger.warn("Failed to cancel TIME poll schedules for hangout {}: {}",
                hangoutId, e.getMessage());
        }
    }

    // ============================================================================
    // INTERNALS
    // ============================================================================

    /**
     * Every terminal path routes through here so neither schedule can leak.
     */
    private void cancelSchedulesFor(String pollId) {
        try {
            scheduler.cancelBoth(pollId);
        } catch (Exception e) {
            logger.warn("cancelSchedulesFor({}) failed: {}", pollId, e.getMessage());
        }
    }

    private void adopt(Poll poll, PollOption winner) {
        String hangoutId = poll.getEventId();
        String pollId = poll.getPollId();
        logger.info("Adopting TIME poll {} on hangout {} via option {}", pollId, hangoutId, winner.getOptionId());

        // 1. Mark poll promoted/inactive and cancel schedules so a late firing no-ops.
        long now = System.currentTimeMillis();
        poll.setPromotedAt(now);
        poll.setActive(false);
        hangoutRepository.savePoll(poll);
        cancelSchedulesFor(pollId);

        // 2. Apply the winning option's timeInput to the hangout.
        if (winner.getTimeInput() == null) {
            logger.warn("Winning option {} has null timeInput — skipping hangout update", winner.getOptionId());
        } else {
            Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
            if (hangoutOpt.isPresent()) {
                Hangout hangout = hangoutOpt.get();
                FuzzyTimeService.TimeConversionResult t = fuzzyTimeService.convert(winner.getTimeInput());
                hangout.setTimeInput(winner.getTimeInput());
                hangout.setStartTimestamp(t.startTimestamp);
                hangout.setEndTimestamp(t.endTimestamp);
                hangoutRepository.save(hangout);

                List<String> groups = hangout.getAssociatedGroups();
                if (groups != null && !groups.isEmpty()) {
                    for (String groupId : groups) {
                        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
                            pointer -> HangoutPointerFactory.applyHangoutFields(pointer, hangout),
                            "time-poll-adoption");
                    }
                    groupTimestampService.updateGroupTimestamps(groups);
                }
            }
        }

        // 3. Refresh denormalized poll data on pointers so the feed reflects promotedAt.
        updatePollPointers(hangoutId);

        // 4. Recompute momentum so the "time added" bonus propagates.
        try {
            momentumService.recomputeMomentum(hangoutId);
        } catch (Exception e) {
            logger.warn("Failed to recompute momentum after adopting poll {} for hangout {}: {}",
                pollId, hangoutId, e.getMessage());
        }
    }

    /**
     * Repaint each associated group's pointer with the current poll/option/vote data. Same
     * pattern as {@code PollServiceImpl.updatePointersWithPolls} — kept local to avoid
     * exposing that private method, since TIME adoption reaches across poll and hangout
     * layers.
     */
    private void updatePollPointers(String hangoutId) {
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            return;
        }
        Hangout hangout = hangoutOpt.get();
        List<String> groups = hangout.getAssociatedGroups();
        if (groups == null || groups.isEmpty()) {
            return;
        }

        List<BaseItem> allPollData = hangoutRepository.getAllPollData(hangoutId);
        List<Poll> polls = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .collect(Collectors.toList());
        List<PollOption> options = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.toList());
        List<Vote> votes = allPollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.toList());

        for (String groupId : groups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                pointer.setPolls(new ArrayList<>(polls));
                pointer.setPollOptions(new ArrayList<>(options));
                pointer.setVotes(new ArrayList<>(votes));
            }, "time-poll-pointer-sync");
        }
        groupTimestampService.updateGroupTimestamps(groups);
    }
}
