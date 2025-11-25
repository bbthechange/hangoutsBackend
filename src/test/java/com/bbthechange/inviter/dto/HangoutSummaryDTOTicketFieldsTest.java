package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.HangoutPointer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ticket/participation coordination fields in HangoutSummaryDTO.
 *
 * Coverage:
 * - Constructor extraction of ticket fields from HangoutPointer
 * - Null handling for ticket fields
 * - Getter methods for new fields
 */
class HangoutSummaryDTOTicketFieldsTest {

    private static final String REQUESTING_USER_ID = "user-123";

    @Nested
    class ConstructorFieldExtraction {

        @Test
        void constructor_WithPointerHavingTicketFields_ExtractsAllFields() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketLink("https://tickets.example.com");
            pointer.setTicketsRequired(true);
            pointer.setDiscountCode("SAVE20");
            pointer.setParticipationSummary(createTestParticipationSummary());

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getTicketLink()).isEqualTo("https://tickets.example.com");
            assertThat(dto.getTicketsRequired()).isTrue();
            assertThat(dto.getDiscountCode()).isEqualTo("SAVE20");
            assertThat(dto.getParticipationSummary()).isNotNull();
            assertThat(dto.getParticipationSummary().getExtraTicketCount()).isEqualTo(5);
        }

        @Test
        void constructor_WithPointerHavingNullTicketFields_SetsNullValues() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketLink(null);
            pointer.setTicketsRequired(null);
            pointer.setDiscountCode(null);
            pointer.setParticipationSummary(null);

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then - No exceptions, null values preserved
            assertThat(dto.getTicketLink()).isNull();
            assertThat(dto.getTicketsRequired()).isNull();
            assertThat(dto.getDiscountCode()).isNull();
            assertThat(dto.getParticipationSummary()).isNull();
        }

        @Test
        void constructor_WithPointerHavingPartialTicketFields_ExtractsAvailableFields() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketLink("https://tickets.example.com");
            pointer.setTicketsRequired(null);
            pointer.setDiscountCode(null);
            pointer.setParticipationSummary(null);

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getTicketLink()).isEqualTo("https://tickets.example.com");
            assertThat(dto.getTicketsRequired()).isNull();
            assertThat(dto.getDiscountCode()).isNull();
            assertThat(dto.getParticipationSummary()).isNull();
        }

        @Test
        void constructor_WithTicketsRequiredFalse_PreservesFalseValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketsRequired(false);

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then - False should be preserved, not converted to null
            assertThat(dto.getTicketsRequired()).isFalse();
        }

        @Test
        void constructor_WithEmptyStringTicketLink_PreservesEmptyString() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketLink("");

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getTicketLink()).isEqualTo("");
        }
    }

    @Nested
    class Getters {

        @Test
        void getParticipationSummary_ReturnsSetValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            ParticipationSummaryDTO summary = createTestParticipationSummary();
            pointer.setParticipationSummary(summary);

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getParticipationSummary()).isSameAs(summary);
        }

        @Test
        void getTicketLink_ReturnsSetValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketLink("https://tickets.example.com/event/123");

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getTicketLink()).isEqualTo("https://tickets.example.com/event/123");
        }

        @Test
        void getTicketsRequired_ReturnsSetValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketsRequired(true);

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getTicketsRequired()).isTrue();
        }

        @Test
        void getDiscountCode_ReturnsSetValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setDiscountCode("FRIENDS20");

            // When
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // Then
            assertThat(dto.getDiscountCode()).isEqualTo("FRIENDS20");
        }
    }

    @Nested
    class Setters {

        @Test
        void setParticipationSummary_UpdatesValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            ParticipationSummaryDTO newSummary = new ParticipationSummaryDTO();
            newSummary.setExtraTicketCount(10);

            // When
            dto.setParticipationSummary(newSummary);

            // Then
            assertThat(dto.getParticipationSummary()).isSameAs(newSummary);
            assertThat(dto.getParticipationSummary().getExtraTicketCount()).isEqualTo(10);
        }

        @Test
        void setTicketLink_UpdatesValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // When
            dto.setTicketLink("https://new-tickets.example.com");

            // Then
            assertThat(dto.getTicketLink()).isEqualTo("https://new-tickets.example.com");
        }

        @Test
        void setTicketsRequired_UpdatesValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            pointer.setTicketsRequired(false);
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // When
            dto.setTicketsRequired(true);

            // Then
            assertThat(dto.getTicketsRequired()).isTrue();
        }

        @Test
        void setDiscountCode_UpdatesValue() {
            // Given
            HangoutPointer pointer = createTestPointer();
            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, REQUESTING_USER_ID);

            // When
            dto.setDiscountCode("NEWCODE50");

            // Then
            assertThat(dto.getDiscountCode()).isEqualTo("NEWCODE50");
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private HangoutPointer createTestPointer() {
        HangoutPointer pointer = new HangoutPointer();
        pointer.setGroupId("group-123");
        pointer.setHangoutId("hangout-456");
        pointer.setTitle("Test Hangout");
        pointer.setStatus("ACTIVE");
        pointer.setParticipantCount(5);

        // Initialize collections to avoid NPEs in constructor
        pointer.setPolls(new ArrayList<>());
        pointer.setPollOptions(new ArrayList<>());
        pointer.setVotes(new ArrayList<>());
        pointer.setCars(new ArrayList<>());
        pointer.setCarRiders(new ArrayList<>());
        pointer.setNeedsRide(new ArrayList<>());
        pointer.setAttributes(new ArrayList<>());
        pointer.setInterestLevels(new ArrayList<>());

        return pointer;
    }

    private ParticipationSummaryDTO createTestParticipationSummary() {
        ParticipationSummaryDTO summary = new ParticipationSummaryDTO();
        summary.setUsersNeedingTickets(new ArrayList<>());
        summary.setUsersWithTickets(new ArrayList<>());
        summary.setUsersWithClaimedSpots(new ArrayList<>());
        summary.setExtraTicketCount(5);
        summary.setReservationOffers(new ArrayList<>());
        return summary;
    }
}
