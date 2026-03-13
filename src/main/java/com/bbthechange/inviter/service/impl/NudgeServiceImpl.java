package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.NudgeDTO;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.model.NudgeType;
import com.bbthechange.inviter.service.NudgeService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Computes action-oriented nudges for hangouts based on their state and momentum.
 *
 * <p>Nudges are never stored in the database — they are computed fresh on each request.
 *
 * <h3>Nudge Logic:</h3>
 * <ul>
 *   <li>SUGGEST_TIME — hangout has no time AND ≥1 non-creator interested/going person</li>
 *   <li>ADD_LOCATION — hangout has no location AND ≥1 non-creator interested/going person</li>
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

    @Override
    public List<NudgeDTO> computeNudges(Hangout hangout, List<InterestLevel> interestLevels) {
        boolean hasNonCreatorInterest = hasNonCreatorInterest(
            interestLevels, hangout.getSuggestedBy());
        return buildNudges(
            hangout.getStartTimestamp(), hangout.getLocation(),
            hangout.getPlaceCategory(), hangout.getMomentumCategory(),
            hasNonCreatorInterest);
    }

    @Override
    public List<NudgeDTO> computeNudgesFromPointer(HangoutPointer pointer) {
        boolean hasNonCreatorInterest = hasNonCreatorInterest(
            pointer.getInterestLevels(), pointer.getSuggestedBy());
        return buildNudges(
            pointer.getStartTimestamp(), pointer.getLocation(),
            pointer.getPlaceCategory(), pointer.getMomentumCategory(),
            hasNonCreatorInterest);
    }

    private List<NudgeDTO> buildNudges(Long startTimestamp, Object location,
                                        String placeCategory, MomentumCategory momentumCategory,
                                        boolean hasNonCreatorInterest) {
        List<NudgeDTO> nudges = new ArrayList<>();

        if (startTimestamp == null && hasNonCreatorInterest) {
            nudges.add(new NudgeDTO(NudgeType.SUGGEST_TIME, "Suggest a time", null));
        }

        if (location == null && hasNonCreatorInterest) {
            nudges.add(new NudgeDTO(NudgeType.ADD_LOCATION, "Add a location", null));
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
            .filter(il -> !il.getUserId().equals(creatorUserId))
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
}
