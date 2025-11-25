package com.bbthechange.inviter.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for UpdateHangoutRequest DTO.
 *
 * Note: Since this class uses Lombok @Data, we only verify that
 * the ticket coordination fields exist and are accessible, not
 * the Lombok-generated code itself.
 */
class UpdateHangoutRequestTest {

    @Test
    void ticketFieldsExist_AndAreAccessible() {
        // Given
        UpdateHangoutRequest request = new UpdateHangoutRequest();

        // When
        request.setTicketLink("https://tickets.example.com/event123");
        request.setTicketsRequired(true);
        request.setDiscountCode("FRIENDS20");

        // Then
        assertThat(request.getTicketLink()).isEqualTo("https://tickets.example.com/event123");
        assertThat(request.getTicketsRequired()).isTrue();
        assertThat(request.getDiscountCode()).isEqualTo("FRIENDS20");
    }

    @Test
    void ticketFields_DefaultToNull() {
        // Given
        UpdateHangoutRequest request = new UpdateHangoutRequest();

        // Then - All ticket fields should be null by default
        assertThat(request.getTicketLink()).isNull();
        assertThat(request.getTicketsRequired()).isNull();
        assertThat(request.getDiscountCode()).isNull();
    }

    @Test
    void ticketsRequired_CanBeFalse() {
        // Given
        UpdateHangoutRequest request = new UpdateHangoutRequest();

        // When
        request.setTicketsRequired(false);

        // Then - Boolean false should be preserved, not null
        assertThat(request.getTicketsRequired()).isFalse();
    }
}
