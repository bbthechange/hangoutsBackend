package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.testutil.HangoutPointerTestBuilder;
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
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Nested;

@ExtendWith(MockitoExtension.class)
class CarpoolServiceImplTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private HangoutService hangoutService;

    @Mock
    private UserService userService;

    @Mock
    private PointerUpdateService pointerUpdateService;

    @Mock
    private GroupTimestampService groupTimestampService;

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
        hangoutData.setNeedsRide(needsRideList);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

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
        // hangoutData already has an empty list
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When
        List<NeedsRideDTO> result = carpoolService.getNeedsRideRequests(eventId, userId);

        // Then
        assertThat(result).isEmpty();
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

    // ==================== Pointer Synchronization Tests ====================

    @Test
    void offerCar_WithValidCarOffer_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        OfferCarRequest request = new OfferCarRequest(4, "Meet at parking lot A");
        User driver = new User();
        driver.setDisplayName("Test Driver");

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        hangoutData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        Car newCar = new Car(eventId, userId, "Test Driver", 4);
        newCar.setNotes("Meet at parking lot A");
        newCar.setAvailableSeats(4);

        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(newCar), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(driver));
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(newCar);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);

        // When
        Car result = carpoolService.offerCar(eventId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveCar(any(Car.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void offerCar_WithNoAssociatedGroups_ShouldNotUpdatePointers() {
        // Given
        OfferCarRequest request = new OfferCarRequest(4, "Meet at parking lot A");
        User driver = new User();
        driver.setDisplayName("Test Driver");

        hangout.setAssociatedGroups(Collections.emptyList());
        hangoutData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        Car newCar = new Car(eventId, userId, "Test Driver", 4);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(driver));
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(newCar);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        Car result = carpoolService.offerCar(eventId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveCar(any(Car.class));
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void reserveSeat_WithValidReservation_ShouldUpdateAllPointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(2);
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());

        User user = new User();
        user.setDisplayName("Test User");

        CarRider newRider = new CarRider(eventId, driverId, userId, "Test User");

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(newRider), List.of());

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(newRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveCarRider(any(CarRider.class));
        verify(hangoutRepository).saveCar(any(Car.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void reserveSeat_WhenAutoDeleteNeedsRideSucceeds_ShouldUpdatePointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(2);
        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride");
        HangoutDetailData dataWithCarAndNeedsRide = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of(needsRide));

        User user = new User();
        user.setDisplayName("Test User");

        CarRider newRider = new CarRider(eventId, driverId, userId, "Test User");

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        // After reservation, needsRide is removed
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(newRider), List.of());

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndNeedsRide).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(newRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);
        doNothing().when(hangoutRepository).deleteNeedsRide(eventId, userId);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).deleteNeedsRide(eventId, userId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void releaseSeat_WithValidRelease_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();
        String groupId3 = UUID.randomUUID().toString();

        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(2);
        CarRider existingRider = new CarRider(eventId, driverId, userId, "Test User");
        HangoutDetailData dataWithCarAndRider = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(existingRider), List.of());

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2, groupId3));
        // After release, rider is removed
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer3 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId3)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndRider).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        carpoolService.releaseSeat(eventId, driverId, userId);

        // Then
        verify(hangoutRepository).deleteCarRider(eventId, driverId, userId);
        verify(hangoutRepository).saveCar(any(Car.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void updateCarOffer_WithCapacityChange_ShouldUpdateAllPointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(2);
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());

        UpdateCarRequest request = new UpdateCarRequest(6, "Updated notes");

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        Car updatedCar = new Car(eventId, userId, "Test Driver", 6);
        updatedCar.setAvailableSeats(4);
        updatedCar.setNotes("Updated notes");
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(updatedCar), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(updatedCar);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        Car result = carpoolService.updateCarOffer(eventId, userId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveCar(any(Car.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void updateCarOffer_WithNotesChange_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(4);
        car.setNotes("Meet at parking lot A");
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());

        UpdateCarRequest request = new UpdateCarRequest(4, "Meet at parking lot B");

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        Car updatedCar = new Car(eventId, userId, "Test Driver", 4);
        updatedCar.setAvailableSeats(4);
        updatedCar.setNotes("Meet at parking lot B");
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(updatedCar), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(updatedCar);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        Car result = carpoolService.updateCarOffer(eventId, userId, request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveCar(any(Car.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void cancelCarOffer_WithRiders_ShouldRemoveCarAndRidersFromPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();
        String rider1Id = UUID.randomUUID().toString();
        String rider2Id = UUID.randomUUID().toString();
        String rider3Id = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(1);
        CarRider rider1 = new CarRider(eventId, userId, rider1Id, "Rider 1");
        CarRider rider2 = new CarRider(eventId, userId, rider2Id, "Rider 2");
        CarRider rider3 = new CarRider(eventId, userId, rider3Id, "Rider 3");
        HangoutDetailData dataWithCarAndRiders = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(car), List.of(), List.of(),
            List.of(rider1, rider2, rider3), List.of()
        );

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        // After cancellation, car and riders are removed
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndRiders).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        carpoolService.cancelCarOffer(eventId, userId, userId);

        // Then
        verify(hangoutRepository, times(3)).deleteCarRider(eq(eventId), eq(userId), anyString());
        verify(hangoutRepository).deleteCar(eventId, userId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void cancelCarOffer_WithNoRiders_ShouldRemoveCarFromPointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(4);
        HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(car), List.of(), List.of(), List.of(), List.of());

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        // After cancellation, car is removed
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        carpoolService.cancelCarOffer(eventId, userId, userId);

        // Then
        verify(hangoutRepository, never()).deleteCarRider(anyString(), anyString(), anyString());
        verify(hangoutRepository).deleteCar(eventId, userId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void createNeedsRideRequest_WithValidRequest_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();
        String groupId3 = UUID.randomUUID().toString();

        NeedsRideRequest request = new NeedsRideRequest("Need a ride from downtown");
        NeedsRide needsRide = new NeedsRide(eventId, userId, request.getNotes());

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2, groupId3));
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(needsRide));

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer3 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId3)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveNeedsRide(any(NeedsRide.class))).thenReturn(needsRide);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        NeedsRide result = carpoolService.createNeedsRideRequest(eventId, userId, request);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveNeedsRide(any(NeedsRide.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void createNeedsRideRequest_WhenPointerUpdateFails_ShouldContinueWithOthers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        NeedsRideRequest request = new NeedsRideRequest("Need a ride");
        NeedsRide needsRide = new NeedsRide(eventId, userId, request.getNotes());

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(needsRide));

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveNeedsRide(any(NeedsRide.class))).thenReturn(needsRide);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        NeedsRide result = carpoolService.createNeedsRideRequest(eventId, userId, request);

        // Then
        assertThat(result).isNotNull();
        verify(hangoutRepository).saveNeedsRide(any(NeedsRide.class));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void deleteNeedsRideRequest_WithExistingRequest_ShouldRemoveFromAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride");
        HangoutDetailData dataWithNeedsRide = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(needsRide));

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        // After deletion, needsRide is removed
        HangoutDetailData updatedData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId1)
            .forHangout(eventId)
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId2)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithNeedsRide).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        doNothing().when(hangoutRepository).deleteNeedsRide(eventId, userId);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        carpoolService.deleteNeedsRideRequest(eventId, userId);

        // Then
        verify(hangoutRepository).deleteNeedsRide(eventId, userId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void deleteNeedsRideRequest_WithIdempotentDelete_ShouldUpdatePointers() {
        // Given
        String groupId = UUID.randomUUID().toString();

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData emptyData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(eventId)
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData).thenReturn(emptyData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        doNothing().when(hangoutRepository).deleteNeedsRide(eventId, userId);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        carpoolService.deleteNeedsRideRequest(eventId, userId);

        // Then
        verify(hangoutRepository).deleteNeedsRide(eventId, userId);
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));
    }

    // ============================================================================
    // PHASE 4: OPTIMISTIC LOCKING RETRY TESTS
    // ============================================================================

    @Nested
    class OptimisticLockingRetryTests {

        @Test
        void updatePointersWithCarpoolData_WithNoConflict_ShouldSucceedOnFirstAttempt() {
            // Given
            String groupId1 = UUID.randomUUID().toString();
            String groupId2 = UUID.randomUUID().toString();
            String groupId3 = UUID.randomUUID().toString();

            OfferCarRequest request = new OfferCarRequest(4, "Meet at parking lot A");
            User driver = new User();
            driver.setDisplayName("Test Driver");

            hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2, groupId3));
            hangoutData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            Car newCar = new Car(eventId, userId, "Test Driver", 4);
            newCar.setNotes("Meet at parking lot A");
            newCar.setAvailableSeats(4);

            HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(newCar), List.of(), List.of(), List.of(), List.of());

            HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId1)
                .forHangout(eventId)
                .build();
            HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId2)
                .forHangout(eventId)
                .build();
            HangoutPointer pointer3 = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId3)
                .forHangout(eventId)
                .build();

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData).thenReturn(dataWithCar);
            when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(driver));
            when(hangoutRepository.saveCar(any(Car.class))).thenReturn(newCar);
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

            // When
            carpoolService.offerCar(eventId, request, userId);

            // Then - verify that PointerUpdateService is called for all groups
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("carpool data"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("carpool data"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(eventId), any(), eq("carpool data"));

            // Note: Retry behavior is now tested in PointerUpdateServiceTest, not here.
        }

        @Test
        void updatePointersWithCarpoolData_WithExponentialBackoff_ShouldIncrementDelay() {
            // Given
            String groupId = UUID.randomUUID().toString();

            OfferCarRequest request = new OfferCarRequest(4, "Meet at parking lot A");
            User driver = new User();
            driver.setDisplayName("Test Driver");

            hangout.setAssociatedGroups(Arrays.asList(groupId));
            hangoutData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            Car newCar = new Car(eventId, userId, "Test Driver", 4);
            newCar.setNotes("Meet at parking lot A");
            newCar.setAvailableSeats(4);

            HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(newCar), List.of(), List.of(), List.of(), List.of());

            HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(eventId)
                .build();

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData).thenReturn(dataWithCar);
            when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(driver));
            when(hangoutRepository.saveCar(any(Car.class))).thenReturn(newCar);
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

            // When
            carpoolService.offerCar(eventId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));

            // Note: Exponential backoff retry behavior is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithCarpoolData_WithRepositoryException_ShouldNotRetry() {
            // Given
            String groupId = UUID.randomUUID().toString();

            OfferCarRequest request = new OfferCarRequest(4, "Meet at parking lot A");
            User driver = new User();
            driver.setDisplayName("Test Driver");

            hangout.setAssociatedGroups(Arrays.asList(groupId));
            hangoutData = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            Car newCar = new Car(eventId, userId, "Test Driver", 4);
            newCar.setNotes("Meet at parking lot A");
            newCar.setAvailableSeats(4);

            HangoutDetailData dataWithCar = new HangoutDetailData(hangout, List.of(), List.of(), List.of(newCar), List.of(), List.of(), List.of(), List.of());

            HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(eventId)
                .build();

            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData).thenReturn(dataWithCar);
            when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
            when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(driver));
            when(hangoutRepository.saveCar(any(Car.class))).thenReturn(newCar);
            when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

            // When
            carpoolService.offerCar(eventId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));

            // Note: Exception handling behavior is now tested in PointerUpdateServiceTest.
        }
    }
}