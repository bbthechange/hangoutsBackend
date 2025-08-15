package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.CarpoolService;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Car;
import com.bbthechange.inviter.model.CarRider;
import com.bbthechange.inviter.model.NeedsRide;
import com.bbthechange.inviter.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = CarpoolController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles("test")
class CarpoolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CarpoolService carpoolService;
    
    @MockBean 
    private JwtService jwtService;

    private String eventId;
    private String userId;
    private String validJWT;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        validJWT = "valid.jwt.token";
    }

    // ============================================================================
    // NEEDS RIDE REQUEST ENDPOINT TESTS
    // ============================================================================

    @Test
    void getNeedsRideRequests_WithValidEventId_ReturnsListOfRequests() throws Exception {
        // Given
        NeedsRideDTO request1 = new NeedsRideDTO(userId, "Need a ride from downtown");
        NeedsRideDTO request2 = new NeedsRideDTO(UUID.randomUUID().toString(), "Need a ride from airport");
        List<NeedsRideDTO> requests = Arrays.asList(request1, request2);

        when(carpoolService.getNeedsRideRequests(eq(eventId), eq(userId))).thenReturn(requests);

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(userId))
                .andExpect(jsonPath("$[0].notes").value("Need a ride from downtown"))
                .andExpect(jsonPath("$[1].notes").value("Need a ride from airport"));

        verify(carpoolService).getNeedsRideRequests(eq(eventId), eq(userId));
    }

    @Test
    void getNeedsRideRequests_WithEventNotFound_ReturnsNotFound() throws Exception {
        // Given
        when(carpoolService.getNeedsRideRequests(eq(eventId), eq(userId)))
                .thenThrow(new EventNotFoundException("Event not found: " + eventId));

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(carpoolService).getNeedsRideRequests(eq(eventId), eq(userId));
    }

    @Test
    void getNeedsRideRequests_WithUnauthorizedUser_ReturnsUnauthorized() throws Exception {
        // Given
        when(carpoolService.getNeedsRideRequests(eq(eventId), eq(userId)))
                .thenThrow(new UnauthorizedException("Cannot view ride requests for this event"));

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(carpoolService).getNeedsRideRequests(eq(eventId), eq(userId));
    }

    @Test
    void getNeedsRideRequests_WithEmptyList_ReturnsEmptyArray() throws Exception {
        // Given
        when(carpoolService.getNeedsRideRequests(eq(eventId), eq(userId))).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(carpoolService).getNeedsRideRequests(eq(eventId), eq(userId));
    }

    @Test
    void createNeedsRideRequest_WithValidRequest_ReturnsCreatedNeedsRide() throws Exception {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride from downtown");
        NeedsRide needsRide = new NeedsRide(eventId, userId, request.getNotes());

        when(carpoolService.createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class)))
                .thenReturn(needsRide);

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.notes").value("Need a ride from downtown"));

        verify(carpoolService).createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class));
    }

    @Test
    void createNeedsRideRequest_WithNullNotes_ReturnsCreatedNeedsRide() throws Exception {
        // Given
        NeedsRideRequest request = new NeedsRideRequest(null);
        NeedsRide needsRide = new NeedsRide(eventId, userId, null);

        when(carpoolService.createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class)))
                .thenReturn(needsRide);

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.notes").doesNotExist());

        verify(carpoolService).createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class));
    }

    @Test
    void createNeedsRideRequest_WithEmptyNotes_ReturnsCreatedNeedsRide() throws Exception {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("");
        NeedsRide needsRide = new NeedsRide(eventId, userId, "");

        when(carpoolService.createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class)))
                .thenReturn(needsRide);

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.notes").value(""));

        verify(carpoolService).createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class));
    }

    @Test
    void createNeedsRideRequest_WithExistingReservation_ReturnsBadRequest() throws Exception {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride");
        when(carpoolService.createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class)))
                .thenThrow(new ValidationException("Cannot request a ride when you already have a reserved seat"));

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(carpoolService).createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class));
    }

    @Test
    void createNeedsRideRequest_WithEventNotFound_ReturnsNotFound() throws Exception {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride");
        when(carpoolService.createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class)))
                .thenThrow(new EventNotFoundException("Event not found: " + eventId));

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(carpoolService).createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class));
    }

    @Test
    void createNeedsRideRequest_WithUnauthorizedUser_ReturnsUnauthorized() throws Exception {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride");
        when(carpoolService.createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class)))
                .thenThrow(new UnauthorizedException("Cannot request ride for this event"));

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(carpoolService).createNeedsRideRequest(eq(eventId), eq(userId), any(NeedsRideRequest.class));
    }

    @Test
    void createNeedsRideRequest_WithTooLongNotes_ReturnsBadRequest() throws Exception {
        // Given - Notes longer than 500 characters should be rejected by validation
        String longNotes = "a".repeat(501);
        NeedsRideRequest request = new NeedsRideRequest(longNotes);

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Service should not be called due to validation failure
        verify(carpoolService, never()).createNeedsRideRequest(any(), any(), any());
    }

    @Test
    void deleteNeedsRideRequest_WithValidRequest_ReturnsNoContent() throws Exception {
        // Given
        doNothing().when(carpoolService).deleteNeedsRideRequest(eq(eventId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(carpoolService).deleteNeedsRideRequest(eq(eventId), eq(userId));
    }

    @Test
    void deleteNeedsRideRequest_WithEventNotFound_ReturnsNotFound() throws Exception {
        // Given
        doThrow(new EventNotFoundException("Event not found: " + eventId))
                .when(carpoolService).deleteNeedsRideRequest(eq(eventId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        verify(carpoolService).deleteNeedsRideRequest(eq(eventId), eq(userId));
    }

    @Test
    void deleteNeedsRideRequest_WithUnauthorizedUser_ReturnsUnauthorized() throws Exception {
        // Given
        doThrow(new UnauthorizedException("Cannot delete ride request for this event"))
                .when(carpoolService).deleteNeedsRideRequest(eq(eventId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/events/{eventId}/carpool/riderequests", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(carpoolService).deleteNeedsRideRequest(eq(eventId), eq(userId));
    }

    // ============================================================================
    // PATH VALIDATION TESTS
    // ============================================================================

    @Test
    void getNeedsRideRequests_WithInvalidEventIdFormat_ReturnsBadRequest() throws Exception {
        // Given
        String invalidEventId = "invalid-uuid";

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/riderequests", invalidEventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Service should not be called due to validation failure
        verify(carpoolService, never()).getNeedsRideRequests(any(), any());
    }

    @Test
    void createNeedsRideRequest_WithInvalidEventIdFormat_ReturnsBadRequest() throws Exception {
        // Given
        String invalidEventId = "not-a-uuid";
        NeedsRideRequest request = new NeedsRideRequest("Need a ride");

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/riderequests", invalidEventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Service should not be called due to validation failure
        verify(carpoolService, never()).createNeedsRideRequest(any(), any(), any());
    }

    @Test
    void deleteNeedsRideRequest_WithInvalidEventIdFormat_ReturnsBadRequest() throws Exception {
        // Given
        String invalidEventId = "12345";

        // When & Then
        mockMvc.perform(delete("/events/{eventId}/carpool/riderequests", invalidEventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // Service should not be called due to validation failure
        verify(carpoolService, never()).deleteNeedsRideRequest(any(), any());
    }

    // ============================================================================
    // CAR OFFER ENDPOINT TESTS
    // ============================================================================

    @Test
    void offerCar_WithValidRequest_ReturnsCreatedCar() throws Exception {
        // Given
        OfferCarRequest request = new OfferCarRequest();
        request.setTotalCapacity(4);
        request.setNotes("Comfortable sedan");

        Car mockCar = new Car(eventId, userId, "Test Driver", 4);
        mockCar.setNotes("Comfortable sedan");
        when(carpoolService.offerCar(eq(eventId), any(OfferCarRequest.class), eq(userId)))
                .thenReturn(mockCar);

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/cars", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.driverId").value(userId))
                .andExpect(jsonPath("$.totalCapacity").value(4));

        verify(carpoolService).offerCar(eq(eventId), any(OfferCarRequest.class), eq(userId));
    }

    @Test
    void getEventCars_WithValidEventId_ReturnsListOfCars() throws Exception {
        // Given
        CarWithRidersDTO car1 = new CarWithRidersDTO();
        CarWithRidersDTO car2 = new CarWithRidersDTO();
        List<CarWithRidersDTO> cars = Arrays.asList(car1, car2);

        when(carpoolService.getEventCars(eq(eventId), eq(userId))).thenReturn(cars);

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/cars", eventId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(carpoolService).getEventCars(eq(eventId), eq(userId));
    }

    @Test
    void reserveSeat_WithValidRequest_ReturnsCarRider() throws Exception {
        // Given
        String driverId = UUID.randomUUID().toString();
        CarRider mockRider = new CarRider(eventId, driverId, userId, "Test Rider");
        when(carpoolService.reserveSeat(eq(eventId), eq(driverId), eq(userId)))
                .thenReturn(mockRider);

        // When & Then
        mockMvc.perform(post("/events/{eventId}/carpool/cars/{driverId}/reserve", eventId, driverId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riderId").value(userId));

        verify(carpoolService).reserveSeat(eq(eventId), eq(driverId), eq(userId));
    }

    @Test
    void releaseSeat_WithValidRequest_ReturnsNoContent() throws Exception {
        // Given
        String driverId = UUID.randomUUID().toString();
        doNothing().when(carpoolService).releaseSeat(eq(eventId), eq(driverId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/events/{eventId}/carpool/cars/{driverId}/reserve", eventId, driverId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(carpoolService).releaseSeat(eq(eventId), eq(driverId), eq(userId));
    }

    @Test
    void getCarDetail_WithValidIds_ReturnsCarDetail() throws Exception {
        // Given
        String driverId = UUID.randomUUID().toString();
        CarDetailDTO mockDetail = new CarDetailDTO();
        when(carpoolService.getCarDetail(eq(eventId), eq(driverId), eq(userId)))
                .thenReturn(mockDetail);

        // When & Then
        mockMvc.perform(get("/events/{eventId}/carpool/cars/{driverId}", eventId, driverId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(carpoolService).getCarDetail(eq(eventId), eq(driverId), eq(userId));
    }

    @Test
    void updateCarOffer_WithValidRequest_ReturnsUpdatedCar() throws Exception {
        // Given
        String driverId = UUID.randomUUID().toString();
        UpdateCarRequest request = new UpdateCarRequest();
        request.setTotalCapacity(4);
        request.setNotes("Updated notes");

        Car mockCar = new Car(eventId, driverId, "Test Driver", 4);
        mockCar.setAvailableSeats(2);
        mockCar.setNotes("Updated notes");
        when(carpoolService.updateCarOffer(eq(eventId), eq(driverId), any(UpdateCarRequest.class), eq(userId)))
                .thenReturn(mockCar);

        // When & Then
        mockMvc.perform(put("/events/{eventId}/carpool/cars/{driverId}", eventId, driverId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(2));

        verify(carpoolService).updateCarOffer(eq(eventId), eq(driverId), any(UpdateCarRequest.class), eq(userId));
    }

    @Test
    void cancelCarOffer_WithValidRequest_ReturnsNoContent() throws Exception {
        // Given
        String driverId = UUID.randomUUID().toString();
        doNothing().when(carpoolService).cancelCarOffer(eq(eventId), eq(driverId), eq(userId));

        // When & Then
        mockMvc.perform(delete("/events/{eventId}/carpool/cars/{driverId}", eventId, driverId)
                .header("Authorization", "Bearer " + validJWT)
                .requestAttr("userId", userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        verify(carpoolService).cancelCarOffer(eq(eventId), eq(driverId), eq(userId));
    }
}