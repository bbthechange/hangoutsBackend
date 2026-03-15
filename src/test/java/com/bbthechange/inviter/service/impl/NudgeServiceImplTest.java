package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.NudgeDTO;
import com.bbthechange.inviter.dto.PollOptionDTO;
import com.bbthechange.inviter.dto.PollWithOptionsDTO;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.model.NudgeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NudgeServiceImpl.
 * Tests each nudge condition: SUGGEST_TIME, ADD_LOCATION, MAKE_RESERVATION, CONSIDER_TICKETS.
 */
class NudgeServiceImplTest {

    private NudgeServiceImpl nudgeService;

    private static final String CREATOR_ID = "creator-user-id";
    private static final String OTHER_USER_ID = "other-user-id";

    @BeforeEach
    void setUp() {
        nudgeService = new NudgeServiceImpl();
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Hangout buildHangout(Long startTimestamp, Object location, String placeCategory,
                                  MomentumCategory momentum, String suggestedBy) {
        Hangout hangout = new Hangout();
        hangout.setHangoutId("hangout-1");
        hangout.setStartTimestamp(startTimestamp);
        if (location != null) {
            com.bbthechange.inviter.dto.Address addr = new com.bbthechange.inviter.dto.Address();
            addr.setName("Some Place");
            hangout.setLocation(addr);
        }
        hangout.setPlaceCategory(placeCategory);
        hangout.setMomentumCategory(momentum);
        hangout.setSuggestedBy(suggestedBy);
        return hangout;
    }

    private HangoutPointer buildPointer(Long startTimestamp, Object location, String placeCategory,
                                         MomentumCategory momentum, String suggestedBy,
                                         List<InterestLevel> interestLevels) {
        HangoutPointer pointer = new HangoutPointer();
        pointer.setHangoutId("hangout-1");
        pointer.setStartTimestamp(startTimestamp);
        if (location != null) {
            com.bbthechange.inviter.dto.Address addr = new com.bbthechange.inviter.dto.Address();
            addr.setName("Some Place");
            pointer.setLocation(addr);
        }
        pointer.setPlaceCategory(placeCategory);
        pointer.setMomentumCategory(momentum);
        pointer.setSuggestedBy(suggestedBy);
        pointer.setInterestLevels(interestLevels);
        return pointer;
    }

    private InterestLevel buildInterestLevel(String userId, String status) {
        InterestLevel il = new InterestLevel();
        il.setUserId(userId);
        il.setStatus(status);
        return il;
    }

    private PollWithOptionsDTO buildSuggestionPoll(String attributeType, Long createdAtMillis,
                                                    Long promotedAt, List<PollOptionDTO> options) {
        PollWithOptionsDTO poll = new PollWithOptionsDTO();
        poll.setPollId("poll-1");
        poll.setAttributeType(attributeType);
        poll.setCreatedAtMillis(createdAtMillis);
        poll.setPromotedAt(promotedAt);
        poll.setOptions(options != null ? options : List.of());
        return poll;
    }

    private PollOptionDTO buildPollOption(String optionId, int voteCount) {
        PollOptionDTO option = new PollOptionDTO();
        option.setOptionId(optionId);
        option.setVoteCount(voteCount);
        return option;
    }

    // ============================================================================
    // SUGGEST_TIME NUDGE
    // ============================================================================

    @Nested
    class SuggestTimeNudge {

        @Test
        void computeNudges_NoTimeAndNonCreatorInterested_ReturnsSuggestTime() {
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "INTERESTED")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudges_NoTimeAndOnlyCreatorGoing_NoSuggestTime() {
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(CREATOR_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudges_TimeSetAndNonCreatorInterested_NoSuggestTime() {
            Hangout hangout = buildHangout(1700000000L, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudges_NoTimeAndEmptyAttendance_NoSuggestTime() {
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudges_nullInterestLevels_noSuggestOrLocationNudge() {
            Hangout hangout = buildHangout(null, null, null, MomentumCategory.BUILDING, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, null);

            assertThat(nudges).extracting(NudgeDTO::getType)
                    .doesNotContain(NudgeType.SUGGEST_TIME, NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_nonCreatorWithNotGoingStatus_doesNotCountAsInterest() {
            Hangout hangout = buildHangout(null, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "NOT_GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType)
                    .doesNotContain(NudgeType.SUGGEST_TIME, NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_NoTimeAndNonCreatorGoing_ReturnsSuggestTime() {
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }
    }

    // ============================================================================
    // CONFIRMED HANGOUT — ALWAYS NEEDS TIME/LOCATION
    // ============================================================================

    @Nested
    class ConfirmedHangoutNudges {

        @Test
        void computeNudges_ConfirmedNoTimeOnlyCreator_ReturnsSuggestTime() {
            // Confirmed hangouts always need a time, even with only creator going
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.CONFIRMED, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(CREATOR_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudges_ConfirmedNoLocationOnlyCreator_ReturnsAddLocation() {
            // Confirmed hangouts always need a location, even with only creator going
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.CONFIRMED, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(CREATOR_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_ConfirmedNoTimeNoAttendance_ReturnsSuggestTime() {
            // Confirmed hangout with no attendance at all still needs a time
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.CONFIRMED, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudges_ConfirmedWithTimeAndLocation_NoCompletionNudges() {
            Hangout hangout = buildHangout(1700000000L, "loc", null, MomentumCategory.CONFIRMED, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(CREATOR_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType)
                .doesNotContain(NudgeType.SUGGEST_TIME, NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_BuildingNoTimeOnlyCreator_NoSuggestTime() {
            // BUILDING hangout with only creator — not enough interest, no nudge
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(CREATOR_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.SUGGEST_TIME);
        }
    }

    // ============================================================================
    // ADD_LOCATION NUDGE
    // ============================================================================

    @Nested
    class AddLocationNudge {

        @Test
        void computeNudges_NoLocationAndNonCreatorInterested_ReturnsAddLocation() {
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "INTERESTED")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_LocationSetAndNonCreatorInterested_NoAddLocation() {
            Hangout hangout = buildHangout(1700000000L, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "INTERESTED")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_NoLocationAndOnlyCreatorInterested_NoAddLocation() {
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(CREATOR_ID, "INTERESTED")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.ADD_LOCATION);
        }
    }

    // ============================================================================
    // ADD_LOCATION WITH SUGGESTION POLLS
    // ============================================================================

    @Nested
    class AddLocationWithSuggestionPolls {

        @Test
        void computeNudges_ActiveSuggestionPoll_ReturnsVoteMessage() {
            // PENDING poll (<24h old) should show "Vote on location suggestions"
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION",
                System.currentTimeMillis() - 1000, // just created
                null,
                List.of(buildPollOption("opt-1", 1)));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance, List.of(poll));

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.ADD_LOCATION);
            NudgeDTO locationNudge = nudges.stream()
                .filter(n -> n.getType() == NudgeType.ADD_LOCATION).findFirst().orElseThrow();
            assertThat(locationNudge.getMessage()).isEqualTo("Vote on location suggestions");
        }

        @Test
        void computeNudges_ContestedSuggestionPoll_ReturnsVoteMessage() {
            // CONTESTED poll (multiple options with votes) should show "Vote on location suggestions"
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            long moreThan24hAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION", moreThan24hAgo, null,
                List.of(buildPollOption("opt-1", 2), buildPollOption("opt-2", 1)));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance, List.of(poll));

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.ADD_LOCATION);
            NudgeDTO locationNudge = nudges.stream()
                .filter(n -> n.getType() == NudgeType.ADD_LOCATION).findFirst().orElseThrow();
            assertThat(locationNudge.getMessage()).isEqualTo("Vote on location suggestions");
        }

        @Test
        void computeNudges_ReadyToPromoteSuggestionPoll_SuppressesNudge() {
            // READY_TO_PROMOTE poll (>24h, unopposed) — location effectively decided
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            long moreThan24hAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION", moreThan24hAgo, null,
                List.of(buildPollOption("opt-1", 1)));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance, List.of(poll));

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudges_AlreadyPromotedPoll_ReturnsAddLocationMessage() {
            // Poll already promoted (promotedAt set) — treated as no active poll
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            long moreThan24hAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION", moreThan24hAgo,
                System.currentTimeMillis(), // already promoted
                List.of(buildPollOption("opt-1", 1)));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance, List.of(poll));

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.ADD_LOCATION);
            NudgeDTO locationNudge = nudges.stream()
                .filter(n -> n.getType() == NudgeType.ADD_LOCATION).findFirst().orElseThrow();
            assertThat(locationNudge.getMessage()).isEqualTo("Add a location");
        }

        @Test
        void computeNudges_DescriptionPollDoesNotAffectLocationNudge() {
            // A DESCRIPTION suggestion poll should not affect the ADD_LOCATION nudge
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            PollWithOptionsDTO poll = buildSuggestionPoll("DESCRIPTION",
                System.currentTimeMillis() - 1000, null,
                List.of(buildPollOption("opt-1", 1)));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance, List.of(poll));

            NudgeDTO locationNudge = nudges.stream()
                .filter(n -> n.getType() == NudgeType.ADD_LOCATION).findFirst().orElseThrow();
            assertThat(locationNudge.getMessage()).isEqualTo("Add a location");
        }

        @Test
        void computeNudges_NullPollsList_ReturnsAddLocationMessage() {
            Hangout hangout = buildHangout(1700000000L, null, null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance, null);

            NudgeDTO locationNudge = nudges.stream()
                .filter(n -> n.getType() == NudgeType.ADD_LOCATION).findFirst().orElseThrow();
            assertThat(locationNudge.getMessage()).isEqualTo("Add a location");
        }

        @Test
        void computeNudgesFromPointer_ActiveSuggestionPoll_ReturnsVoteMessage() {
            // Verify the pointer path also handles suggestion polls correctly
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));
            HangoutPointer pointer = buildPointer(1700000000L, null, null,
                MomentumCategory.BUILDING, CREATOR_ID, attendance);

            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION",
                System.currentTimeMillis() - 1000, null,
                List.of(buildPollOption("opt-1", 1)));

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer, List.of(poll));

            NudgeDTO locationNudge = nudges.stream()
                .filter(n -> n.getType() == NudgeType.ADD_LOCATION).findFirst().orElseThrow();
            assertThat(locationNudge.getMessage()).isEqualTo("Vote on location suggestions");
        }
    }

    // ============================================================================
    // MAKE_RESERVATION NUDGE
    // ============================================================================

    @Nested
    class MakeReservationNudge {

        @Test
        void computeNudges_RestaurantCategoryAndGainingMomentum_ReturnsMakeReservation() {
            Hangout hangout = buildHangout(1700000000L, "loc", "restaurant",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.MAKE_RESERVATION);
        }

        @Test
        void computeNudges_RestaurantCategoryAndConfirmed_ReturnsMakeReservation() {
            Hangout hangout = buildHangout(1700000000L, "loc", "restaurant",
                MomentumCategory.CONFIRMED, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.MAKE_RESERVATION);
        }

        @Test
        void computeNudges_RestaurantCategoryAndBuilding_NoMakeReservation() {
            Hangout hangout = buildHangout(1700000000L, "loc", "restaurant",
                MomentumCategory.BUILDING, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.MAKE_RESERVATION);
        }

        @Test
        void computeNudges_BarCategoryAndGainingMomentum_ReturnsMakeReservation() {
            Hangout hangout = buildHangout(1700000000L, "loc", "bar",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.MAKE_RESERVATION);
        }

        @Test
        void computeNudges_NullPlaceCategoryAndGainingMomentum_NoMakeReservation() {
            Hangout hangout = buildHangout(1700000000L, "loc", null,
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.MAKE_RESERVATION);
        }
    }

    // ============================================================================
    // CONSIDER_TICKETS NUDGE
    // ============================================================================

    @Nested
    class ConsiderTicketsNudge {

        @Test
        void computeNudges_EventCategoryAndGainingMomentum_ReturnsConsiderTickets() {
            Hangout hangout = buildHangout(1700000000L, "loc", "event_space",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_EntertainmentCategoryAndConfirmed_ReturnsConsiderTickets() {
            Hangout hangout = buildHangout(1700000000L, "loc", "entertainment",
                MomentumCategory.CONFIRMED, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_EventCategoryAndBuilding_NoConsiderTickets() {
            Hangout hangout = buildHangout(1700000000L, "loc", "event_space",
                MomentumCategory.BUILDING, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_NullMomentumAndEventCategory_NoConsiderTickets() {
            Hangout hangout = buildHangout(1700000000L, "loc", "event_space", null, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).doesNotContain(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_concertCategory_considerTicketsNudge() {
            Hangout hangout = buildHangout(1700000000L, "loc", "concert",
                MomentumCategory.CONFIRMED, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_theatreCategory_considerTicketsNudge() {
            Hangout hangout = buildHangout(1700000000L, "loc", "theater",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_sportsCategory_considerTicketsNudge() {
            Hangout hangout = buildHangout(1700000000L, "loc", "sports",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.CONSIDER_TICKETS);
        }

        @Test
        void computeNudges_unknownPlaceCategory_noTicketsOrReservationNudge() {
            Hangout hangout = buildHangout(1700000000L, "loc", "unknown_type",
                MomentumCategory.CONFIRMED, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            assertThat(nudges).extracting(NudgeDTO::getType)
                    .doesNotContain(NudgeType.MAKE_RESERVATION, NudgeType.CONSIDER_TICKETS);
        }
    }

    // ============================================================================
    // SUGGESTED_BY NULL (legacy hangouts)
    // ============================================================================

    @Nested
    class SuggestedByNull {

        @Test
        void computeNudges_NullSuggestedByBuildingNoTime_NoSuggestTime() {
            // Legacy/pre-momentum hangout with null suggestedBy and BUILDING state.
            // No non-creator interest can be determined, so no nudge.
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, null);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel("some-user", "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            // With null suggestedBy, all interest counts as non-creator, so the nudge fires
            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudgesFromPointer_NullSuggestedByBuilding_TreatsAllAsNonCreator() {
            // When suggestedBy is null, all interest levels count as non-creator interest
            List<InterestLevel> attendance = List.of(
                buildInterestLevel("some-user", "INTERESTED")
            );
            HangoutPointer pointer = buildPointer(null, "loc", null,
                MomentumCategory.BUILDING, null, attendance);

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }
    }

    // ============================================================================
    // NO NUDGES CASES
    // ============================================================================

    @Nested
    class NoNudges {

        @Test
        void computeNudges_ConfirmedWithTimeAndLocationNonRestaurant_ReturnsEmpty() {
            Hangout hangout = buildHangout(1700000000L, "loc", "park",
                MomentumCategory.CONFIRMED, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "GOING")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).isEmpty();
        }

        @Test
        void computeNudges_MultipleSatisfiedConditions_ReturnsAllMatchingNudges() {
            // No time, no location, restaurant, gaining momentum — should produce SUGGEST_TIME, ADD_LOCATION, MAKE_RESERVATION
            Hangout hangout = buildHangout(null, null, "restaurant",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);
            List<InterestLevel> attendance = List.of(
                buildInterestLevel(OTHER_USER_ID, "INTERESTED")
            );

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            assertThat(nudges).extracting(NudgeDTO::getType)
                .containsExactlyInAnyOrder(
                    NudgeType.SUGGEST_TIME,
                    NudgeType.ADD_LOCATION,
                    NudgeType.MAKE_RESERVATION
                );
        }
    }

    // ============================================================================
    // POINTER-BASED NUDGES (computeNudgesFromPointer)
    // ============================================================================

    @Nested
    class PointerBasedNudges {

        @Test
        void computeNudgesFromPointer_NoTimeAndNonCreatorInterested_ReturnsSuggestTime() {
            List<InterestLevel> interestLevels = List.of(
                buildInterestLevel(OTHER_USER_ID, "INTERESTED")
            );
            HangoutPointer pointer = buildPointer(null, "loc", null,
                MomentumCategory.BUILDING, CREATOR_ID, interestLevels);

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.SUGGEST_TIME);
        }

        @Test
        void computeNudgesFromPointer_RestaurantGainingMomentum_ReturnsMakeReservation() {
            HangoutPointer pointer = buildPointer(1700000000L, "loc", "restaurant",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID, Collections.emptyList());

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.MAKE_RESERVATION);
        }

        @Test
        void computeNudgesFromPointer_NoLocationAndNonCreatorGoing_ReturnsAddLocation() {
            List<InterestLevel> interestLevels = List.of(
                buildInterestLevel(OTHER_USER_ID, "GOING")
            );
            HangoutPointer pointer = buildPointer(1700000000L, null, null,
                MomentumCategory.BUILDING, CREATOR_ID, interestLevels);

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudgesFromPointer_NoInterestLevels_ReturnsEmpty() {
            HangoutPointer pointer = buildPointer(null, null, null,
                MomentumCategory.BUILDING, CREATOR_ID, Collections.emptyList());

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).isEmpty();
        }

        @Test
        void computeNudgesFromPointer_nullInterestLevels_noNudge() {
            HangoutPointer pointer = buildPointer(null, null, null,
                MomentumCategory.BUILDING, CREATOR_ID, null);

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).extracting(NudgeDTO::getType)
                    .doesNotContain(NudgeType.SUGGEST_TIME, NudgeType.ADD_LOCATION);
        }

        @Test
        void computeNudgesFromPointer_EventCategoryConfirmed_ReturnsConsiderTickets() {
            HangoutPointer pointer = buildPointer(1700000000L, "loc", "concert",
                MomentumCategory.CONFIRMED, CREATOR_ID, Collections.emptyList());

            List<NudgeDTO> nudges = nudgeService.computeNudgesFromPointer(pointer);

            assertThat(nudges).extracting(NudgeDTO::getType).contains(NudgeType.CONSIDER_TICKETS);
        }
    }

    // ============================================================================
    // SUGGESTION POLL STATE HELPER
    // ============================================================================

    @Nested
    class SuggestionPollStateTests {

        @Test
        void getSuggestionPollState_NullPolls_ReturnsNone() {
            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(null, "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.NONE);
        }

        @Test
        void getSuggestionPollState_EmptyPolls_ReturnsNone() {
            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(List.of(), "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.NONE);
        }

        @Test
        void getSuggestionPollState_NoMatchingAttributeType_ReturnsNone() {
            PollWithOptionsDTO poll = buildSuggestionPoll("DESCRIPTION",
                System.currentTimeMillis() - 1000, null,
                List.of(buildPollOption("opt-1", 1)));

            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(List.of(poll), "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.NONE);
        }

        @Test
        void getSuggestionPollState_PendingPoll_ReturnsActive() {
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION",
                System.currentTimeMillis() - 1000, null,
                List.of(buildPollOption("opt-1", 0)));

            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(List.of(poll), "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.ACTIVE);
        }

        @Test
        void getSuggestionPollState_ContestedPoll_ReturnsActive() {
            long moreThan24hAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION", moreThan24hAgo, null,
                List.of(buildPollOption("opt-1", 2), buildPollOption("opt-2", 1)));

            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(List.of(poll), "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.ACTIVE);
        }

        @Test
        void getSuggestionPollState_ReadyToPromotePoll_ReturnsResolved() {
            long moreThan24hAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION", moreThan24hAgo, null,
                List.of(buildPollOption("opt-1", 1)));

            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(List.of(poll), "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.RESOLVED);
        }

        @Test
        void getSuggestionPollState_AlreadyPromotedPoll_ReturnsNone() {
            // Promoted polls (promotedAt set) are ignored
            long moreThan24hAgo = System.currentTimeMillis() - (25 * 60 * 60 * 1000L);
            PollWithOptionsDTO poll = buildSuggestionPoll("LOCATION", moreThan24hAgo,
                System.currentTimeMillis(),
                List.of(buildPollOption("opt-1", 1)));

            NudgeServiceImpl.SuggestionPollState state = nudgeService.getSuggestionPollState(List.of(poll), "LOCATION");
            assertThat(state).isEqualTo(NudgeServiceImpl.SuggestionPollState.NONE);
        }
    }

    // ============================================================================
    // NUDGE DTO CONTENT
    // ============================================================================

    @Nested
    class NudgeDTOContent {

        @Test
        void computeNudges_SuggestTimeNudge_HasCorrectMessageAndNullActionUrl() {
            Hangout hangout = buildHangout(null, "loc", null, MomentumCategory.BUILDING, CREATOR_ID);
            List<InterestLevel> attendance = List.of(buildInterestLevel(OTHER_USER_ID, "INTERESTED"));

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, attendance);

            NudgeDTO suggestTime = nudges.stream()
                .filter(n -> n.getType() == NudgeType.SUGGEST_TIME)
                .findFirst().orElseThrow();
            assertThat(suggestTime.getMessage()).isEqualTo("Suggest a time");
            assertThat(suggestTime.getActionUrl()).isNull();
        }

        @Test
        void computeNudges_MakeReservationNudge_HasCorrectMessage() {
            Hangout hangout = buildHangout(1700000000L, "loc", "restaurant",
                MomentumCategory.GAINING_MOMENTUM, CREATOR_ID);

            List<NudgeDTO> nudges = nudgeService.computeNudges(hangout, Collections.emptyList());

            NudgeDTO makeRes = nudges.stream()
                .filter(n -> n.getType() == NudgeType.MAKE_RESERVATION)
                .findFirst().orElseThrow();
            assertThat(makeRes.getMessage()).isEqualTo("Make a reservation");
        }
    }
}
