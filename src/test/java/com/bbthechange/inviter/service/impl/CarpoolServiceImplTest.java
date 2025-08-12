package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarpoolServiceImplTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private HangoutService hangoutService;

    @Mock
    private UserService userService;

    @InjectMocks
    private CarpoolServiceImpl carpoolService;

    private String eventId;
    private String userId;
    private String driverId;
    private Hangout hangout;
    private HangoutDetailData hangoutData;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        driverId = UUID.randomUUID().toString();
        
        hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setTitle("Test Hangout");
        
        hangoutData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    // ============================================================================
    // NEEDS RIDE REQUEST TESTS
    // ============================================================================

    @Test
    void getNeedsRideRequests_WithValidEventAndUser_ReturnsNeedsRideList() {
        // Given
        NeedsRide needsRide1 = new NeedsRide(eventId, userId, "Need a ride from downtown");
        String userId2 = UUID.randomUUID().toString();
        NeedsRide needsRide2 = new NeedsRide(eventId, userId2, "Need a ride from airport");
        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2);
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getNeedsRideListForEvent(eventId)).thenReturn(needsRideList);

        // When
        List<NeedsRideDTO> result = carpoolService.getNeedsRideRequests(eventId, userId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        assertThat(result.get(0).getNotes()).isEqualTo("Need a ride from downtown");
        assertThat(result.get(1).getUserId()).isEqualTo(userId2);
        assertThat(result.get(1).getNotes()).isEqualTo("Need a ride from airport");
        
        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService).canUserViewHangout(userId, hangout);
        verify(hangoutRepository).getNeedsRideListForEvent(eventId);
    }

    @Test
    void getNeedsRideRequests_WithNonExistentEvent_ThrowsEventNotFoundException() {
        // Given
        HangoutDetailData emptyData = new HangoutDetailData(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(emptyData);

        // When/Then
        assertThatThrownBy(() -> carpoolService.getNeedsRideRequests(eventId, userId))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessage("Event not found: " + eventId);
        
        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService, never()).canUserViewHangout(any(), any());
        verify(hangoutRepository, never()).getNeedsRideListForEvent(any());
    }

    @Test
    void getNeedsRideRequests_WithUnauthorizedUser_ThrowsUnauthorizedException() {
        // Given
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> carpoolService.getNeedsRideRequests(eventId, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Cannot view ride requests for this event");
        
        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService).canUserViewHangout(userId, hangout);
        verify(hangoutRepository, never()).getNeedsRideListForEvent(any());
    }

    @Test
    void getNeedsRideRequests_WithEmptyList_ReturnsEmptyList() {
        // Given
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.getNeedsRideListForEvent(eventId)).thenReturn(List.of());

        // When
        List<NeedsRideDTO> result = carpoolService.getNeedsRideRequests(eventId, userId);

        // Then
        assertThat(result).isEmpty();
        verify(hangoutRepository).getNeedsRideListForEvent(eventId);
    }

    @Test
    void createNeedsRideRequest_WithValidRequest_CreatesNeedsRide() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride from downtown");
        NeedsRide needsRide = new NeedsRide(eventId, userId, request.getNotes());
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveNeedsRide(any(NeedsRide.class))).thenReturn(needsRide);

        // When
        NeedsRide result = carpoolService.createNeedsRideRequest(eventId, userId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isEqualTo("Need a ride from downtown");
        
        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService).canUserViewHangout(userId, hangout);
        verify(hangoutRepository).saveNeedsRide(any(NeedsRide.class));
    }

    @Test
    void createNeedsRideRequest_WithUserHavingReservedSeat_ThrowsValidationException() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride from downtown");
        CarRider existingRider = new CarRider(eventId, driverId, userId, "Test User");
        HangoutDetailData dataWithRider = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(existingRider), List.of());
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithRider);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.createNeedsRideRequest(eventId, userId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Cannot request a ride when you already have a reserved seat");
        
        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService).canUserViewHangout(userId, hangout);
        verify(hangoutRepository, never()).saveNeedsRide(any());
    }

    @Test
    void createNeedsRideRequest_WithNullNotes_CreatesNeedsRideWithNullNotes() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest(null);
        NeedsRide needsRide = new NeedsRide(eventId, userId, null);
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveNeedsRide(any(NeedsRide.class))).thenReturn(needsRide);

        // When
        NeedsRide result = carpoolService.createNeedsRideRequest(eventId, userId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isNull();
        
        verify(hangoutRepository).saveNeedsRide(any(NeedsRide.class));
    }

    @Test
    void createNeedsRideRequest_WithEmptyNotes_CreatesNeedsRideWithEmptyNotes() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("");
        NeedsRide needsRide = new NeedsRide(eventId, userId, "");
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveNeedsRide(any(NeedsRide.class))).thenReturn(needsRide);

        // When
        NeedsRide result = carpoolService.createNeedsRideRequest(eventId, userId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNotes()).isEqualTo("");
        verify(hangoutRepository).saveNeedsRide(any(NeedsRide.class));
    }

    @Test
    void createNeedsRideRequest_WithNonExistentEvent_ThrowsEventNotFoundException() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride");
        HangoutDetailData emptyData = new HangoutDetailData(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(emptyData);

        // When/Then
        assertThatThrownBy(() -> carpoolService.createNeedsRideRequest(eventId, userId, request))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessage("Event not found: " + eventId);
        
        verify(hangoutRepository, never()).saveNeedsRide(any());
    }

    @Test
    void createNeedsRideRequest_WithUnauthorizedUser_ThrowsUnauthorizedException() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Need a ride");
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> carpoolService.createNeedsRideRequest(eventId, userId, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Cannot request ride for this event");
        
        verify(hangoutRepository, never()).saveNeedsRide(any());
    }

    @Test
    void deleteNeedsRideRequest_WithValidParameters_DeletesNeedsRide() {
        // Given
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        doNothing().when(hangoutRepository).deleteNeedsRide(eventId, userId);

        // When
        carpoolService.deleteNeedsRideRequest(eventId, userId);

        // Then
        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService).canUserViewHangout(userId, hangout);
        verify(hangoutRepository).deleteNeedsRide(eventId, userId);
    }

    @Test
    void deleteNeedsRideRequest_WithNonExistentEvent_ThrowsEventNotFoundException() {
        // Given
        HangoutDetailData emptyData = new HangoutDetailData(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(emptyData);

        // When/Then
        assertThatThrownBy(() -> carpoolService.deleteNeedsRideRequest(eventId, userId))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessage("Event not found: " + eventId);
        
        verify(hangoutRepository, never()).deleteNeedsRide(any(), any());
    }

    @Test
    void deleteNeedsRideRequest_WithUnauthorizedUser_ThrowsUnauthorizedException() {
        // Given
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> carpoolService.deleteNeedsRideRequest(eventId, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Cannot delete ride request for this event");
        
        verify(hangoutRepository, never()).deleteNeedsRide(any(), any());
    }

    // ============================================================================
    // RESERVE SEAT AUTO-DELETION TESTS
    // ============================================================================

    @Test
    void reserveSeat_WithExistingNeedsRideRequest_AutoDeletesRequest() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setNotes("Going to event");
        car.setAvailableSeats(2);
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());
        
        User user = new User();
        user.setDisplayName("Test User");
        CarRider carRider = new CarRider(eventId, driverId, userId, "Test User");
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(carRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);
        doNothing().when(hangoutRepository).deleteNeedsRide(eventId, userId);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRiderId()).isEqualTo(userId);
        assertThat(result.getDriverId()).isEqualTo(driverId);
        
        verify(hangoutRepository).deleteNeedsRide(eventId, userId);
        verify(hangoutRepository).saveCarRider(any(CarRider.class));
        verify(hangoutRepository).saveCar(any(Car.class));
    }

    @Test
    void reserveSeat_WithDeleteNeedsRideFailure_StillReservesSeat() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setNotes("Going to event");
        car.setAvailableSeats(2);
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());
        
        User user = new User();
        user.setDisplayName("Test User");
        CarRider carRider = new CarRider(eventId, driverId, userId, "Test User");
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(carRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);
        doThrow(new RuntimeException("Delete failed")).when(hangoutRepository).deleteNeedsRide(eventId, userId);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId);

        // Then - Should still succeed despite deletion failure
        assertThat(result).isNotNull();
        assertThat(result.getRiderId()).isEqualTo(userId);
        
        verify(hangoutRepository).deleteNeedsRide(eventId, userId);
        verify(hangoutRepository).saveCarRider(any(CarRider.class));
        verify(hangoutRepository).saveCar(any(Car.class));
    }

    @Test
    void reserveSeat_WithNoAvailableSeats_ThrowsNoAvailableSeatsException() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setNotes("Going to event");
        car.setAvailableSeats(0); // No available seats
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, userId))
                .isInstanceOf(NoAvailableSeatsException.class)
                .hasMessage("No available seats in this car");
        
        verify(hangoutRepository, never()).deleteNeedsRide(any(), any());
        verify(hangoutRepository, never()).saveCarRider(any());
    }

    @Test
    void reserveSeat_WithExistingReservation_ThrowsValidationException() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setNotes("Going to event");
        car.setAvailableSeats(2);
        CarRider existingRider = new CarRider(eventId, driverId, userId, "Test User");
        HangoutDetailData dataWithCarAndRider = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(existingRider), List.of());
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndRider);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessage("User already has a reservation with this driver");
        
        verify(hangoutRepository, never()).deleteNeedsRide(any(), any());
        verify(hangoutRepository, never()).saveCarRider(any());
    }
}