package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.CapacityExceededException;
import com.bbthechange.inviter.exception.ReservationOfferNotFoundException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.ReservationOfferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ReservationOfferController
 *
 * Test Coverage:
 * - POST /hangouts/{hangoutId}/reservation-offers - Create offer
 * - GET /hangouts/{hangoutId}/reservation-offers - List offers
 * - GET /hangouts/{hangoutId}/reservation-offers/{offerId} - Get specific offer
 * - PUT /hangouts/{hangoutId}/reservation-offers/{offerId} - Update offer
 * - DELETE /hangouts/{hangoutId}/reservation-offers/{offerId} - Delete offer
 * - POST /hangouts/{hangoutId}/reservation-offers/{offerId}/complete - Complete offer
 * - POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot - Claim spot
 * - POST /hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot - Unclaim spot
 * - Validation scenarios and error handling
 */
@WebMvcTest(controllers = ReservationOfferController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles("test")
@DisplayName("ReservationOfferController Tests")
class ReservationOfferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReservationOfferService reservationOfferService;

    @MockitoBean
    private JwtService jwtService;

    private String hangoutId;
    private String offerId;
    private String userId;
    private String validJWT;
    private ReservationOfferDTO mockOfferDTO;

    @BeforeEach
    void setUp() {
        hangoutId = UUID.randomUUID().toString();
        offerId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        validJWT = "valid.jwt.token";

        // Create a mock ReservationOfferDTO for test responses
        mockOfferDTO = createMockOfferDTO();
    }

    private ReservationOfferDTO createMockOfferDTO() {
        ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);
        offer.setSection("A");
        offer.setCapacity(10);
        offer.setClaimedSpots(0);
        offer.setStatus(OfferStatus.COLLECTING);
        offer.setCreatedAt(Instant.now());
        offer.setUpdatedAt(Instant.now());

        return new ReservationOfferDTO(offer, "John Doe", "users/123/profile.jpg");
    }

    @Nested
    @DisplayName("POST /hangouts/{hangoutId}/reservation-offers - Create Offer Tests")
    class CreateOfferTests {

        @Test
        @DisplayName("Test 1: Valid request returns 201 with DTO")
        void createOffer_ValidRequest_Returns201WithDTO() throws Exception {
            // Given
            CreateReservationOfferRequest request = new CreateReservationOfferRequest();
            request.setType(OfferType.TICKET);
            request.setSection("A");
            request.setCapacity(10);

            when(reservationOfferService.createOffer(eq(hangoutId), any(CreateReservationOfferRequest.class), eq(userId)))
                    .thenReturn(mockOfferDTO);

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.offerId").value(offerId))
                    .andExpect(jsonPath("$.type").value("TICKET"))
                    .andExpect(jsonPath("$.section").value("A"));

            verify(reservationOfferService).createOffer(eq(hangoutId), any(CreateReservationOfferRequest.class), eq(userId));
        }

        @Test
        @DisplayName("Test 2: Invalid UUID returns 400")
        void createOffer_InvalidUUID_Returns400() throws Exception {
            // Given
            CreateReservationOfferRequest request = new CreateReservationOfferRequest();
            request.setType(OfferType.TICKET);
            String invalidHangoutId = "not-a-uuid";

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers", invalidHangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(reservationOfferService, never()).createOffer(any(), any(), any());
        }

        @Test
        @DisplayName("Test 3: Missing type returns 400")
        void createOffer_MissingType_Returns400() throws Exception {
            // Given - Create request without type (violates @NotNull)
            String requestJson = "{\"section\": \"A\", \"capacity\": 10}";

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(reservationOfferService, never()).createOffer(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GET /hangouts/{hangoutId}/reservation-offers - List Offers Tests")
    class GetOffersTests {

        @Test
        @DisplayName("Test 4: Valid request returns 200 with list")
        void getOffers_ValidRequest_Returns200WithList() throws Exception {
            // Given
            ReservationOfferDTO offer1 = mockOfferDTO;
            ReservationOfferDTO offer2 = createMockOfferDTO();
            offer2.setOfferId(UUID.randomUUID().toString());
            List<ReservationOfferDTO> offers = Arrays.asList(offer1, offer2);

            when(reservationOfferService.getOffers(eq(hangoutId), eq(userId)))
                    .thenReturn(offers);

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}/reservation-offers", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));

            verify(reservationOfferService).getOffers(eq(hangoutId), eq(userId));
        }
    }

    @Nested
    @DisplayName("GET /hangouts/{hangoutId}/reservation-offers/{offerId} - Get Offer Tests")
    class GetOfferTests {

        @Test
        @DisplayName("Test 5: Existing offer returns 200 with DTO")
        void getOffer_ExistingOffer_Returns200WithDTO() throws Exception {
            // Given
            when(reservationOfferService.getOffer(eq(hangoutId), eq(offerId), eq(userId)))
                    .thenReturn(mockOfferDTO);

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}/reservation-offers/{offerId}", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offerId").value(offerId))
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.displayName").value("John Doe"));

            verify(reservationOfferService).getOffer(eq(hangoutId), eq(offerId), eq(userId));
        }
    }

    @Nested
    @DisplayName("PUT /hangouts/{hangoutId}/reservation-offers/{offerId} - Update Offer Tests")
    class UpdateOfferTests {

        @Test
        @DisplayName("Test 6: Valid request returns 200 with updated DTO")
        void updateOffer_ValidRequest_Returns200WithUpdatedDTO() throws Exception {
            // Given
            UpdateReservationOfferRequest request = new UpdateReservationOfferRequest();
            request.setSection("B");
            request.setCapacity(15);

            ReservationOfferDTO updatedDTO = createMockOfferDTO();
            updatedDTO.setSection("B");
            updatedDTO.setCapacity(15);

            when(reservationOfferService.updateOffer(eq(hangoutId), eq(offerId), any(UpdateReservationOfferRequest.class), eq(userId)))
                    .thenReturn(updatedDTO);

            // When & Then
            mockMvc.perform(put("/hangouts/{hangoutId}/reservation-offers/{offerId}", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.section").value("B"))
                    .andExpect(jsonPath("$.capacity").value(15));

            verify(reservationOfferService).updateOffer(eq(hangoutId), eq(offerId), any(UpdateReservationOfferRequest.class), eq(userId));
        }
    }

    @Nested
    @DisplayName("DELETE /hangouts/{hangoutId}/reservation-offers/{offerId} - Delete Offer Tests")
    class DeleteOfferTests {

        @Test
        @DisplayName("Test 7: Existing offer returns 204")
        void deleteOffer_ExistingOffer_Returns204() throws Exception {
            // Given
            doNothing().when(reservationOfferService).deleteOffer(eq(hangoutId), eq(offerId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/hangouts/{hangoutId}/reservation-offers/{offerId}", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(reservationOfferService).deleteOffer(eq(hangoutId), eq(offerId), eq(userId));
        }
    }

    @Nested
    @DisplayName("POST /hangouts/{hangoutId}/reservation-offers/{offerId}/complete - Complete Offer Tests")
    class CompleteOfferTests {

        @Test
        @DisplayName("Test 8: Valid request returns 200 with DTO")
        void completeOffer_ValidRequest_Returns200WithDTO() throws Exception {
            // Given
            CompleteReservationOfferRequest request = new CompleteReservationOfferRequest();
            request.setConvertAll(true);
            request.setTicketCount(5);
            request.setTotalPrice(new BigDecimal("150.00"));

            ReservationOfferDTO completedDTO = createMockOfferDTO();
            completedDTO.setStatus(OfferStatus.COMPLETED);
            completedDTO.setTicketCount(5);
            completedDTO.setTotalPrice(new BigDecimal("150.00"));

            when(reservationOfferService.completeOffer(eq(hangoutId), eq(offerId), any(CompleteReservationOfferRequest.class), eq(userId)))
                    .thenReturn(completedDTO);

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers/{offerId}/complete", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.ticketCount").value(5));

            verify(reservationOfferService).completeOffer(eq(hangoutId), eq(offerId), any(CompleteReservationOfferRequest.class), eq(userId));
        }

        @Test
        @DisplayName("Test 9: Missing convertAll flag returns 400")
        void completeOffer_MissingConvertAllFlag_Returns400() throws Exception {
            // Given - Create request without convertAll (violates @NotNull)
            String requestJson = "{\"ticketCount\": 5, \"totalPrice\": 150.00}";

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers/{offerId}/complete", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(reservationOfferService, never()).completeOffer(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot - Claim Spot Tests")
    class ClaimSpotTests {

        @Test
        @DisplayName("Test 10: Successful claim returns 201 with ParticipationDTO")
        void claimSpot_SuccessfulClaim_Returns201WithParticipationDTO() throws Exception {
            // Given
            Participation participation = new Participation(hangoutId, UUID.randomUUID().toString(), userId, ParticipationType.CLAIMED_SPOT);
            participation.setCreatedAt(Instant.now());
            participation.setUpdatedAt(Instant.now());

            ParticipationDTO participationDTO = new ParticipationDTO(participation, "John Doe", "users/123/profile.jpg");

            when(reservationOfferService.claimSpot(eq(hangoutId), eq(offerId), eq(userId)))
                    .thenReturn(participationDTO);

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value("CLAIMED_SPOT"))
                    .andExpect(jsonPath("$.userId").value(userId));

            verify(reservationOfferService).claimSpot(eq(hangoutId), eq(offerId), eq(userId));
        }

        @Test
        @DisplayName("Test 12: Capacity exceeded returns 409")
        void claimSpot_CapacityExceeded_Returns409() throws Exception {
            // Given
            when(reservationOfferService.claimSpot(eq(hangoutId), eq(offerId), eq(userId)))
                    .thenThrow(new CapacityExceededException("Offer capacity exceeded"));

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isConflict());

            verify(reservationOfferService).claimSpot(eq(hangoutId), eq(offerId), eq(userId));
        }
    }

    @Nested
    @DisplayName("POST /hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot - Unclaim Spot Tests")
    class UnclaimSpotTests {

        @Test
        @DisplayName("Test 11: Successful unclaim returns 204")
        void unclaimSpot_SuccessfulUnclaim_Returns204() throws Exception {
            // Given
            doNothing().when(reservationOfferService).unclaimSpot(eq(hangoutId), eq(offerId), eq(userId));

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot", hangoutId, offerId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(reservationOfferService).unclaimSpot(eq(hangoutId), eq(offerId), eq(userId));
        }
    }
}
