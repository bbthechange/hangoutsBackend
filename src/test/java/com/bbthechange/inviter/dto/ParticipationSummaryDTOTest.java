package com.bbthechange.inviter.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ParticipationSummaryDTO.
 * Tests null-safety of the DTO that stores grouped participation data in HangoutPointer.
 */
class ParticipationSummaryDTOTest {

    @Test
    void constructor_InitializesAllFieldsToEmptyCollections() {
        // When
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        // Then - all list getters return empty lists (not null)
        assertThat(dto.getUsersNeedingTickets()).isNotNull().isEmpty();
        assertThat(dto.getUsersWithTickets()).isNotNull().isEmpty();
        assertThat(dto.getUsersWithClaimedSpots()).isNotNull().isEmpty();
        assertThat(dto.getReservationOffers()).isNotNull().isEmpty();
        assertThat(dto.getExtraTicketCount()).isEqualTo(0);
    }

    @Test
    void getUsersNeedingTickets_WhenSetToNull_ReturnsEmptyList() {
        // Given
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        // When
        dto.setUsersNeedingTickets(null);

        // Then - getter returns empty list, not null
        assertThat(dto.getUsersNeedingTickets()).isNotNull().isEmpty();
    }

    @Test
    void getUsersWithTickets_WhenSetToNull_ReturnsEmptyList() {
        // Given
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        // When
        dto.setUsersWithTickets(null);

        // Then - getter returns empty list, not null
        assertThat(dto.getUsersWithTickets()).isNotNull().isEmpty();
    }

    @Test
    void getUsersWithClaimedSpots_WhenSetToNull_ReturnsEmptyList() {
        // Given
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        // When
        dto.setUsersWithClaimedSpots(null);

        // Then - getter returns empty list, not null
        assertThat(dto.getUsersWithClaimedSpots()).isNotNull().isEmpty();
    }

    @Test
    void getReservationOffers_WhenSetToNull_ReturnsEmptyList() {
        // Given
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        // When
        dto.setReservationOffers(null);

        // Then - getter returns empty list, not null
        assertThat(dto.getReservationOffers()).isNotNull().isEmpty();
    }

    @Test
    void getExtraTicketCount_WhenSetToNull_ReturnsZero() {
        // Given
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        // When
        dto.setExtraTicketCount(null);

        // Then - returns 0 instead of null (prevents NPEs in arithmetic)
        assertThat(dto.getExtraTicketCount()).isEqualTo(0);
    }

    @Test
    void setters_WithValidData_PreservesData() {
        // Given
        ParticipationSummaryDTO dto = new ParticipationSummaryDTO();

        List<UserSummary> needingTickets = List.of(
            new UserSummary("user1", "Alice", "alice.jpg")
        );
        List<UserSummary> withTickets = List.of(
            new UserSummary("user2", "Bob", "bob.jpg")
        );
        List<UserSummary> claimedSpots = List.of(
            new UserSummary("user3", "Charlie", "charlie.jpg")
        );
        List<ReservationOfferDTO> offers = new ArrayList<>();
        ReservationOfferDTO offer = new ReservationOfferDTO();
        offer.setOfferId("offer1");
        offers.add(offer);

        // When
        dto.setUsersNeedingTickets(needingTickets);
        dto.setUsersWithTickets(withTickets);
        dto.setUsersWithClaimedSpots(claimedSpots);
        dto.setReservationOffers(offers);
        dto.setExtraTicketCount(5);

        // Then - all data is preserved
        assertThat(dto.getUsersNeedingTickets()).hasSize(1);
        assertThat(dto.getUsersNeedingTickets().get(0).getDisplayName()).isEqualTo("Alice");

        assertThat(dto.getUsersWithTickets()).hasSize(1);
        assertThat(dto.getUsersWithTickets().get(0).getDisplayName()).isEqualTo("Bob");

        assertThat(dto.getUsersWithClaimedSpots()).hasSize(1);
        assertThat(dto.getUsersWithClaimedSpots().get(0).getDisplayName()).isEqualTo("Charlie");

        assertThat(dto.getReservationOffers()).hasSize(1);
        assertThat(dto.getReservationOffers().get(0).getOfferId()).isEqualTo("offer1");

        assertThat(dto.getExtraTicketCount()).isEqualTo(5);
    }
}
