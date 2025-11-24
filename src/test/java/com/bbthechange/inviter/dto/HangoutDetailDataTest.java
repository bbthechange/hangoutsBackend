package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HangoutDetailData.
 * Tests the data container returned from repository's single-query fetch.
 */
class HangoutDetailDataTest {

    @Test
    void constructor_WithNullParticipations_SetsEmptyList() {
        // Given
        Hangout hangout = createTestHangout();

        // When - pass null for participations
        HangoutDetailData data = new HangoutDetailData(
            hangout,
            List.of(),  // polls
            List.of(),  // pollOptions
            List.of(),  // cars
            List.of(),  // votes
            List.of(),  // attendance
            List.of(),  // carRiders
            List.of(),  // needsRide
            null,       // participations - null
            List.of()   // reservationOffers
        );

        // Then - getParticipations returns empty list, not null
        assertThat(data.getParticipations()).isNotNull().isEmpty();
    }

    @Test
    void constructor_WithNullReservationOffers_SetsEmptyList() {
        // Given
        Hangout hangout = createTestHangout();

        // When - pass null for reservationOffers
        HangoutDetailData data = new HangoutDetailData(
            hangout,
            List.of(),  // polls
            List.of(),  // pollOptions
            List.of(),  // cars
            List.of(),  // votes
            List.of(),  // attendance
            List.of(),  // carRiders
            List.of(),  // needsRide
            List.of(),  // participations
            null        // reservationOffers - null
        );

        // Then - getReservationOffers returns empty list, not null
        assertThat(data.getReservationOffers()).isNotNull().isEmpty();
    }

    @Test
    void constructor_WithValidLists_PreservesData() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Hangout hangout = createTestHangout();
        hangout.setHangoutId(hangoutId);

        Poll poll = new Poll();
        poll.setPollId(UUID.randomUUID().toString());
        poll.setTitle("Test Poll");

        Participation participation = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.TICKET_NEEDED);

        ReservationOffer offer = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), userId, OfferType.TICKET);

        Car car = new Car(hangoutId, userId, "Driver", 4);

        // When
        HangoutDetailData data = new HangoutDetailData(
            hangout,
            List.of(poll),
            List.of(),
            List.of(car),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(participation),
            List.of(offer)
        );

        // Then - all data is preserved
        assertThat(data.getHangout()).isEqualTo(hangout);
        assertThat(data.getPolls()).hasSize(1);
        assertThat(data.getPolls().get(0).getTitle()).isEqualTo("Test Poll");
        assertThat(data.getCars()).hasSize(1);
        assertThat(data.getParticipations()).hasSize(1);
        assertThat(data.getParticipations().get(0).getType()).isEqualTo(ParticipationType.TICKET_NEEDED);
        assertThat(data.getReservationOffers()).hasSize(1);
        assertThat(data.getReservationOffers().get(0).getType()).isEqualTo(OfferType.TICKET);
    }

    @Test
    void constructor_WithAllNullLists_SetsAllToEmpty() {
        // Given
        Hangout hangout = createTestHangout();

        // When - pass null for all lists
        HangoutDetailData data = new HangoutDetailData(
            hangout,
            null,  // polls
            null,  // pollOptions
            null,  // cars
            null,  // votes
            null,  // attendance
            null,  // carRiders
            null,  // needsRide
            null,  // participations
            null   // reservationOffers
        );

        // Then - all lists are empty (not null)
        assertThat(data.getHangout()).isEqualTo(hangout);
        assertThat(data.getPolls()).isNotNull().isEmpty();
        assertThat(data.getPollOptions()).isNotNull().isEmpty();
        assertThat(data.getCars()).isNotNull().isEmpty();
        assertThat(data.getVotes()).isNotNull().isEmpty();
        assertThat(data.getAttendance()).isNotNull().isEmpty();
        assertThat(data.getCarRiders()).isNotNull().isEmpty();
        assertThat(data.getNeedsRide()).isNotNull().isEmpty();
        assertThat(data.getParticipations()).isNotNull().isEmpty();
        assertThat(data.getReservationOffers()).isNotNull().isEmpty();
    }

    private Hangout createTestHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(UUID.randomUUID().toString());
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);
        return hangout;
    }
}
