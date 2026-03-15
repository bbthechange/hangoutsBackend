package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.NudgeDTO;
import com.bbthechange.inviter.dto.PollOptionDTO;
import com.bbthechange.inviter.dto.PollWithOptionsDTO;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.model.NudgeType;
import com.bbthechange.inviter.service.NudgeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Computes action-oriented nudges for hangouts based on their state and momentum.
 *
 * <p>Nudges are never stored in the database — they are computed fresh on each request.
 *
 * <h3>Nudge Logic:</h3>
 * <ul>
 *   <li>SUGGEST_TIME — hangout has no time AND (CONFIRMED, or ≥1 non-creator interested/going)</li>
 *   <li>ADD_LOCATION — hangout has no location AND (CONFIRMED, or ≥1 non-creator interested/going),
 *       suppressed when a location suggestion poll is resolved (READY_TO_PROMOTE)</li>
 *   <li>MAKE_RESERVATION — restaurant/food place type AND momentum is GAINING_MOMENTUM or CONFIRMED</li>
 *   <li>CONSIDER_TICKETS — event/entertainment place type AND momentum is GAINING_MOMENTUM or CONFIRMED</li>
 * </ul>
 */
@Service
public class NudgeServiceImpl implements NudgeService {

    /** Place categories considered "restaurant/food" for MAKE_RESERVATION nudge. */
    private static final Set<String> RESTAURANT_PLACE_CATEGORIES = Set.of(
        "restaurant", "bar", "food"
    );

    /** Place categories considered "event/entertainment" for CONSIDER_TICKETS nudge. */
    private static final Set<String> EVENT_PLACE_CATEGORIES = Set.of(
        "event_space", "entertainment", "concert", "theater", "sports"
    );

    private static final Set<MomentumCategory> TRACTION_CATEGORIES = Set.of(
        MomentumCategory.GAINING_MOMENTUM, MomentumCategory.CONFIRMED
    );

    private static final long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L;

    @Override
    public List<NudgeDTO> computeNudges(Hangout hangout, List<InterestLevel> interestLevels) {
        boolean hasNonCreatorInterest = hasNonCreatorInterest(
            interestLevels, hangout.getSuggestedBy());
        return buildNudges(
            hangout.getStartTimestamp(), hangout.getLocation(),
            hangout.getPlaceCategory(), hangout.getMomentumCategory(),
            hasNonCreatorInterest, SuggestionPollState.NONE);
    }

    @Override
    public List<NudgeDTO> computeNudges(Hangout hangout, List<InterestLevel> interestLevels, List<PollWithOptionsDTO> polls) {
        boolean hasNonCreatorInterest = hasNonCreatorInterest(
            interestLevels, hangout.getSuggestedBy());
        SuggestionPollState locationState = getSuggestionPollState(polls, "LOCATION");
        return buildNudges(
            hangout.getStartTimestamp(), hangout.getLocation(),
            hangout.getPlaceCategory(), hangout.getMomentumCategory(),
            hasNonCreatorInterest, locationState);
    }

    @Override
    public List<NudgeDTO> computeNudgesFromPointer(HangoutPointer pointer) {
        boolean hasNonCreatorInterest = hasNonCreatorInterest(
            pointer.getInterestLevels(), pointer.getSuggestedBy());
        return buildNudges(
            pointer.getStartTimestamp(), pointer.getLocation(),
            pointer.getPlaceCategory(), pointer.getMomentumCategory(),
            hasNonCreatorInterest, SuggestionPollState.NONE);
    }

    @Override
    public List<NudgeDTO> computeNudgesFromPointer(HangoutPointer pointer, List<PollWithOptionsDTO> polls) {
        boolean hasNonCreatorInterest = hasNonCreatorInterest(
            pointer.getInterestLevels(), pointer.getSuggestedBy());
        SuggestionPollState locationState = getSuggestionPollState(polls, "LOCATION");
        return buildNudges(
            pointer.getStartTimestamp(), pointer.getLocation(),
            pointer.getPlaceCategory(), pointer.getMomentumCategory(),
            hasNonCreatorInterest, locationState);
    }

    private List<NudgeDTO> buildNudges(Long startTimestamp, Object location,
                                        String placeCategory, MomentumCategory momentumCategory,
                                        boolean hasNonCreatorInterest,
                                        SuggestionPollState locationSuggestionState) {
        List<NudgeDTO> nudges = new ArrayList<>();

        // CONFIRMED hangouts always need time/location; BUILDING/GAINING require non-creator interest
        boolean needsCompletionNudge = momentumCategory == MomentumCategory.CONFIRMED || hasNonCreatorInterest;

        if (startTimestamp == null && needsCompletionNudge) {
            nudges.add(new NudgeDTO(NudgeType.SUGGEST_TIME, "Suggest a time", null));
        }

        if (location == null && needsCompletionNudge) {
            switch (locationSuggestionState) {
                case NONE:
                    nudges.add(new NudgeDTO(NudgeType.ADD_LOCATION, "Add a location", null));
                    break;
                case ACTIVE:
                    nudges.add(new NudgeDTO(NudgeType.ADD_LOCATION, "Vote on location suggestions", null));
                    break;
                case RESOLVED:
                    // Location effectively decided via suggestion poll — no nudge needed
                    break;
            }
        }

        if (isRestaurantType(placeCategory) && hasTraction(momentumCategory)) {
            nudges.add(new NudgeDTO(NudgeType.MAKE_RESERVATION, "Make a reservation", null));
        }

        if (isEventType(placeCategory) && hasTraction(momentumCategory)) {
            nudges.add(new NudgeDTO(NudgeType.CONSIDER_TICKETS, "Consider buying tickets", null));
        }

        return nudges;
    }

    /**
     * Returns true if at least one person (excluding the creator/suggester) is INTERESTED or GOING.
     */
    private boolean hasNonCreatorInterest(List<InterestLevel> interestLevels, String creatorUserId) {
        if (interestLevels == null || interestLevels.isEmpty()) {
            return false;
        }
        return interestLevels.stream()
            .filter(il -> creatorUserId == null || !il.getUserId().equals(creatorUserId))
            .anyMatch(il -> "GOING".equals(il.getStatus()) || "INTERESTED".equals(il.getStatus()));
    }

    private boolean isRestaurantType(String placeCategory) {
        return placeCategory != null && RESTAURANT_PLACE_CATEGORIES.contains(placeCategory.toLowerCase());
    }

    private boolean isEventType(String placeCategory) {
        return placeCategory != null && EVENT_PLACE_CATEGORIES.contains(placeCategory.toLowerCase());
    }

    private boolean hasTraction(MomentumCategory category) {
        return category != null && TRACTION_CATEGORIES.contains(category);
    }

    // ============================================================================
    // SUGGESTION POLL STATE
    // ============================================================================

    /**
     * Tri-state for how a suggestion poll affects nudge behavior.
     */
    enum SuggestionPollState {
        /** No un-promoted suggestion poll exists. */
        NONE,
        /** A suggestion poll exists and is still being voted on (PENDING or CONTESTED). */
        ACTIVE,
        /** A suggestion poll exists and is effectively decided (READY_TO_PROMOTE). */
        RESOLVED
    }

    /**
     * Determine the state of suggestion polls for the given attribute type.
     * Avoids tight coupling to AttributeSuggestionServiceImpl by inlining the status logic.
     */
    SuggestionPollState getSuggestionPollState(List<PollWithOptionsDTO> polls, String attributeType) {
        if (polls == null) return SuggestionPollState.NONE;

        long now = System.currentTimeMillis();

        List<PollWithOptionsDTO> matchingPolls = polls.stream()
            .filter(p -> attributeType.equals(p.getAttributeType()) && p.getPromotedAt() == null)
            .toList();

        if (matchingPolls.isEmpty()) return SuggestionPollState.NONE;

        // If any matching poll is resolved (unopposed, >24h old), the attribute is effectively decided
        boolean anyResolved = matchingPolls.stream()
            .anyMatch(p -> isUnopposedAndMature(p, now));

        return anyResolved ? SuggestionPollState.RESOLVED : SuggestionPollState.ACTIVE;
    }

    /**
     * A suggestion poll is effectively resolved when it's >24h old and has no opposing votes.
     */
    private boolean isUnopposedAndMature(PollWithOptionsDTO poll, long nowMillis) {
        if (poll.getCreatedAtMillis() == null || (nowMillis - poll.getCreatedAtMillis()) < TWENTY_FOUR_HOURS_MS) {
            return false;
        }
        List<PollOptionDTO> options = poll.getOptions();
        if (options == null || options.isEmpty()) return false; // no options = still pending
        if (options.size() == 1) return true; // single option, unopposed

        // Contested if multiple options have votes
        PollOptionDTO leader = options.stream()
            .max(Comparator.comparingInt(PollOptionDTO::getVoteCount))
            .orElse(null);
        if (leader == null) return true;

        boolean hasOpposition = options.stream()
            .filter(o -> !o.getOptionId().equals(leader.getOptionId()))
            .anyMatch(o -> o.getVoteCount() > 0);

        return !hasOpposition;
    }
}
