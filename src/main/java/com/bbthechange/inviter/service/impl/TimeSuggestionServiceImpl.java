package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateTimeSuggestionRequest;
import com.bbthechange.inviter.dto.TimeSuggestionDTO;
import com.bbthechange.inviter.dto.TimeSuggestionPointerView;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.*;
import com.bbthechange.inviter.util.HangoutPointerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of TimeSuggestionService.
 *
 * Implements the spec section 8 "silence = consent" logic:
 *   - Single suggestion with ≥1 supporter, no competition → auto-adopt after shortWindow.
 *   - Single suggestion with 0 votes, no competition    → auto-adopt after longWindow.
 *   - Multiple competing suggestions                    → leave as poll.
 *
 * When a suggestion is adopted the hangout's startTimestamp is updated and
 * momentum is recomputed so that the +1 signals from the time suggestion propagate.
 */
@Service
public class TimeSuggestionServiceImpl implements TimeSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(TimeSuggestionServiceImpl.class);

    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final MomentumService momentumService;
    private final PointerUpdateService pointerUpdateService;
    private final TimeSuggestionSchedulerService timeSuggestionSchedulerService;
    private final FuzzyTimeService fuzzyTimeService;
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public TimeSuggestionServiceImpl(HangoutRepository hangoutRepository,
                                     GroupRepository groupRepository,
                                     MomentumService momentumService,
                                     PointerUpdateService pointerUpdateService,
                                     TimeSuggestionSchedulerService timeSuggestionSchedulerService,
                                     FuzzyTimeService fuzzyTimeService,
                                     GroupTimestampService groupTimestampService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.momentumService = momentumService;
        this.pointerUpdateService = pointerUpdateService;
        this.timeSuggestionSchedulerService = timeSuggestionSchedulerService;
        this.fuzzyTimeService = fuzzyTimeService;
        this.groupTimestampService = groupTimestampService;
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    @Override
    public TimeSuggestionDTO createSuggestion(String groupId, String hangoutId,
                                               CreateTimeSuggestionRequest request, String userId) {
        requireGroupMember(groupId, userId);

        Hangout hangout = requireHangout(hangoutId);

        // Suggestion only makes sense when the hangout has no time set
        if (hangout.getStartTimestamp() != null) {
            throw new ValidationException("Hangout already has a time set; no time suggestion needed");
        }

        if (request.getTimeInput() == null) {
            throw new ValidationException("timeInput is required");
        }

        // Validate the TimeInfo shape (throws ValidationException on bad input)
        fuzzyTimeService.convert(request.getTimeInput());

        TimeSuggestion suggestion = new TimeSuggestion(
                hangoutId, groupId, userId, request.getTimeInput()
        );

        hangoutRepository.saveTimeSuggestion(suggestion);
        logger.info("User {} created time suggestion {} for hangout {}", userId, suggestion.getSuggestionId(), hangoutId);

        // Propagate to pointers so the suggestion appears in the group feed
        propagateSuggestionsToPointers(hangout);

        // Schedule EventBridge adoption checks at short (24h) and long (48h) windows
        try {
            timeSuggestionSchedulerService.scheduleAdoptionChecks(
                    hangoutId, suggestion.getSuggestionId(), suggestion.getCreatedAt());
        } catch (Exception e) {
            logger.warn("Failed to schedule adoption checks for suggestion {} — adoption will not auto-trigger: {}",
                    suggestion.getSuggestionId(), e.getMessage());
        }

        return TimeSuggestionDTO.from(suggestion);
    }

    @Override
    public TimeSuggestionDTO supportSuggestion(String groupId, String hangoutId,
                                                String suggestionId, String userId) {
        requireGroupMember(groupId, userId);

        TimeSuggestion suggestion = requireSuggestion(hangoutId, suggestionId);

        if (suggestion.getStatus() != TimeSuggestionStatus.ACTIVE) {
            throw new ValidationException("Time suggestion is no longer active");
        }

        if (!suggestion.addSupporter(userId)) {
            // Idempotent — already supporting, just return current state
            logger.debug("User {} already supports suggestion {} — no change", userId, suggestionId);
            return TimeSuggestionDTO.from(suggestion);
        }

        hangoutRepository.saveTimeSuggestion(suggestion);
        logger.info("User {} supported time suggestion {} for hangout {}", userId, suggestionId, hangoutId);

        // Propagate updated support count to pointers
        propagateSuggestionsToPointers(requireHangout(hangoutId));

        return TimeSuggestionDTO.from(suggestion);
    }

    @Override
    public List<TimeSuggestionDTO> listSuggestions(String groupId, String hangoutId, String userId) {
        requireGroupMember(groupId, userId);
        requireHangout(hangoutId);

        return hangoutRepository.findActiveTimeSuggestions(hangoutId).stream()
                .map(TimeSuggestionDTO::from)
                .collect(Collectors.toList());
    }

    // ============================================================================
    // AUTO-ADOPTION (triggered by EventBridge scheduled events)
    // ============================================================================

    @Override
    public void adoptForHangout(String hangoutId, int shortWindowHours, int longWindowHours) {
        List<TimeSuggestion> active = hangoutRepository.findActiveTimeSuggestions(hangoutId);
        if (active.isEmpty()) {
            return;
        }

        // Competing suggestions — leave as poll
        if (active.size() > 1) {
            logger.debug("Hangout {} has {} competing suggestions — skipping auto-adoption", hangoutId, active.size());
            return;
        }

        TimeSuggestion candidate = active.get(0);
        Instant createdAt = candidate.getCreatedAt();
        if (createdAt == null) {
            return;
        }

        long ageHours = (Instant.now().toEpochMilli() - createdAt.toEpochMilli()) / (3600_000L);
        boolean hasSupport = candidate.supportCount() > 0;

        boolean shouldAdopt = hasSupport
                ? ageHours >= shortWindowHours
                : ageHours >= longWindowHours;

        if (!shouldAdopt) {
            logger.debug("Suggestion {} for hangout {} not yet past adoption window (age={}h, hasSupport={})",
                    candidate.getSuggestionId(), hangoutId, ageHours, hasSupport);
            return;
        }

        adoptSuggestion(candidate);
    }

    /**
     * Adopt a suggestion: mark it ADOPTED, update the hangout time, recompute momentum.
     */
    private void adoptSuggestion(TimeSuggestion suggestion) {
        String hangoutId = suggestion.getHangoutId();
        logger.info("Auto-adopting time suggestion {} for hangout {}",
                suggestion.getSuggestionId(), hangoutId);

        // 1. Mark suggestion as ADOPTED and cancel pending EventBridge schedules
        suggestion.setStatus(TimeSuggestionStatus.ADOPTED);
        hangoutRepository.saveTimeSuggestion(suggestion);
        timeSuggestionSchedulerService.cancelAdoptionChecks(suggestion.getSuggestionId());

        // 2. Apply the suggestion's timeInput to the hangout (sets timeInput,
        //    startTimestamp, and endTimestamp). Defensive null-check guards
        //    against legacy rows lacking timeInput; such rows are skipped.
        if (suggestion.getTimeInput() == null) {
            logger.warn("Adopted suggestion {} has null timeInput — skipping hangout update (likely legacy row)",
                    suggestion.getSuggestionId());
        } else {
            Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
            if (hangoutOpt.isPresent()) {
                Hangout hangout = hangoutOpt.get();
                FuzzyTimeService.TimeConversionResult t = fuzzyTimeService.convert(suggestion.getTimeInput());
                hangout.setTimeInput(suggestion.getTimeInput());
                hangout.setStartTimestamp(t.startTimestamp);
                hangout.setEndTimestamp(t.endTimestamp);
                hangoutRepository.save(hangout);

                // Update all associated pointers
                List<String> groups = hangout.getAssociatedGroups();
                if (groups != null) {
                    // applyHangoutFields clears timeSuggestions whenever the hangout has a time,
                    // so adoption inherits the clear for free.
                    for (String groupId : groups) {
                        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
                                pointer -> HangoutPointerFactory.applyHangoutFields(pointer, hangout),
                                "time-suggestion-adoption");
                    }
                    groupTimestampService.updateGroupTimestamps(groups);
                }
            }
        }

        // 3. Recompute momentum so the time-added bonus propagates
        try {
            momentumService.recomputeMomentum(hangoutId);
        } catch (Exception e) {
            logger.warn("Failed to recompute momentum after adopting suggestion for hangout {}: {}",
                    hangoutId, e.getMessage());
        }

        logger.info("Successfully adopted time suggestion {} for hangout {}", suggestion.getSuggestionId(), hangoutId);
    }

    // ============================================================================
    // AUTHORIZATION / LOOKUP / PROPAGATION HELPERS
    // ============================================================================

    /** Hard cap on how many suggestions are denormalized onto each pointer. */
    static final int MAX_SUGGESTIONS_PER_POINTER = 5;

    /**
     * Propagate active time suggestions to all associated hangout pointers.
     * Caps at {@link #MAX_SUGGESTIONS_PER_POINTER} by (supportCount desc, createdAt desc)
     * so a pointer can't grow unbounded even if the hangout accumulates many suggestions.
     */
    private void propagateSuggestionsToPointers(Hangout hangout) {
        String hangoutId = hangout.getHangoutId();
        List<String> groups = hangout.getAssociatedGroups();
        if (groups == null || groups.isEmpty()) {
            return;
        }

        List<TimeSuggestionPointerView> capped = hangoutRepository.findActiveTimeSuggestions(hangoutId).stream()
                .sorted(Comparator
                        .comparingInt(TimeSuggestion::supportCount).reversed()
                        .thenComparing(ts -> ts.getCreatedAt() != null ? ts.getCreatedAt() : Instant.EPOCH,
                                Comparator.reverseOrder()))
                .limit(MAX_SUGGESTIONS_PER_POINTER)
                .map(TimeSuggestionPointerView::from)
                .collect(Collectors.toList());

        for (String groupId : groups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
                    pointer -> pointer.setTimeSuggestions(new ArrayList<>(capped)),
                    "propagate-time-suggestions");
        }
        // Invalidate group feed caches via ETag
        groupTimestampService.updateGroupTimestamps(groups);
    }

    /**
     * Called when the hangout's time is set directly (e.g., host PATCH) so that any
     * ACTIVE suggestions become moot. Marks canonical rows REJECTED and cancels their
     * scheduled adoption checks. Pointer-side clearing is handled by
     * {@link HangoutPointerFactory#applyHangoutFields} (suggestions are cleared whenever
     * the hangout has a startTimestamp), so callers don't need a separate pointer write.
     */
    @Override
    public void invalidateActiveSuggestions(String hangoutId) {
        List<TimeSuggestion> active = hangoutRepository.findActiveTimeSuggestions(hangoutId);
        if (active.isEmpty()) {
            return;
        }
        for (TimeSuggestion ts : active) {
            ts.setStatus(TimeSuggestionStatus.REJECTED);
            hangoutRepository.saveTimeSuggestion(ts);
            try {
                timeSuggestionSchedulerService.cancelAdoptionChecks(ts.getSuggestionId());
            } catch (Exception e) {
                logger.warn("Failed to cancel adoption checks for rejected suggestion {}: {}",
                        ts.getSuggestionId(), e.getMessage());
            }
        }
        logger.info("Invalidated {} active time suggestions for hangout {} due to direct time edit",
                active.size(), hangoutId);
    }

    private void requireGroupMember(String groupId, String userId) {
        Boolean isMember = groupRepository.isUserMemberOfGroup(groupId, userId);
        if (!Boolean.TRUE.equals(isMember)) {
            throw new UnauthorizedException("User is not a member of group " + groupId);
        }
    }

    private Hangout requireHangout(String hangoutId) {
        return hangoutRepository.findHangoutById(hangoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Hangout not found: " + hangoutId));
    }

    private TimeSuggestion requireSuggestion(String hangoutId, String suggestionId) {
        return hangoutRepository.findTimeSuggestionById(hangoutId, suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Time suggestion not found: " + suggestionId));
    }
}
