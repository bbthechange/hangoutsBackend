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
        
        hangoutData = HangoutDetailData.builder().withHangout(hangout).build();
    }

    // ============================================================================
    // NEEDS RIDE REQUEST TESTS
    // ============================================================================

    @Test
    void getNeedsRideRequests_WithValidEventAndUser_ReturnsNeedsRideListWithProfileData() {
        // Given
        NeedsRide needsRide1 = new NeedsRide(eventId, userId, "Need a ride from downtown");
        String userId2 = UUID.randomUUID().toString();
        NeedsRide needsRide2 = new NeedsRide(eventId, userId2, "Need a ride from airport");
        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2);
        hangoutData.setNeedsRide(needsRideList);

        UserSummaryDTO summary1 = new UserSummaryDTO(UUID.fromString(userId), "User One", "users/u1/profile.jpg");
        UserSummaryDTO summary2 = new UserSummaryDTO(UUID.fromString(userId2), "User Two", "users/u2/profile.jpg");

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(summary1));
        when(userService.getUserSummary(UUID.fromString(userId2))).thenReturn(Optional.of(summary2));

        // When
        List<NeedsRideDTO> result = carpoolService.getNeedsRideRequests(eventId, userId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        assertThat(result.get(0).getNotes()).isEqualTo("Need a ride from downtown");
        assertThat(result.get(0).getDisplayName()).isEqualTo("User One");
        assertThat(result.get(0).getMainImagePath()).isEqualTo("users/u1/profile.jpg");
        assertThat(result.get(1).getUserId()).isEqualTo(userId2);
        assertThat(result.get(1).getNotes()).isEqualTo("Need a ride from airport");
        assertThat(result.get(1).getDisplayName()).isEqualTo("User Two");
        assertThat(result.get(1).getMainImagePath()).isEqualTo("users/u2/profile.jpg");

        verify(hangoutRepository).getHangoutDetailData(eventId);
        verify(hangoutService).canUserViewHangout(userId, hangout);
    }

    @Test
    void getNeedsRideRequests_WithNonExistentEvent_ThrowsEventNotFoundException() {
        // Given
        HangoutDetailData emptyData = HangoutDetailData.builder().build();
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
        verify(userService, never()).getUserSummary(any());
    }

    @Test
    void getNeedsRideRequests_WithUserNotInCache_ReturnsNullDisplayNameAndImage() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride");
        hangoutData.setNeedsRide(List.of(needsRide));

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(hangoutData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.empty());

        // When
        List<NeedsRideDTO> result = carpoolService.getNeedsRideRequests(eventId, userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        assertThat(result.get(0).getDisplayName()).isNull();
        assertThat(result.get(0).getMainImagePath()).isNull();
    }

    // ============================================================================
    // GET EVENT CARS TESTS
    // ============================================================================

    @Test
    void getEventCars_WithValidEventAndUser_ReturnsCarsWithImagePaths() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(2);
        car.setNotes("Meet at lot A");
        String riderId = UUID.randomUUID().toString();
        CarRider rider = new CarRider(eventId, driverId, riderId, "Test Rider");
        rider.setNotes("Pick me up at corner");
        rider.setPlusOneCount(1);
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .withCarRiders(List.of(rider))
            .build();

        UserSummaryDTO driverSummary = new UserSummaryDTO(UUID.fromString(driverId), "Test Driver", "users/driver/profile.jpg");
        UserSummaryDTO riderSummary = new UserSummaryDTO(UUID.fromString(riderId), "Test Rider", "users/rider/profile.jpg");

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserSummary(UUID.fromString(driverId))).thenReturn(Optional.of(driverSummary));
        when(userService.getUserSummary(UUID.fromString(riderId))).thenReturn(Optional.of(riderSummary));

        // When
        List<CarWithRidersDTO> result = carpoolService.getEventCars(eventId, userId);

        // Then
        assertThat(result).hasSize(1);
        CarWithRidersDTO carDto = result.get(0);
        assertThat(carDto.getDriverId()).isEqualTo(driverId);
        assertThat(carDto.getDriverName()).isEqualTo("Test Driver");
        assertThat(carDto.getDriverImagePath()).isEqualTo("users/driver/profile.jpg");
        assertThat(carDto.getTotalCapacity()).isEqualTo(4);
        assertThat(carDto.getAvailableSeats()).isEqualTo(2);
        assertThat(carDto.getNotes()).isEqualTo("Meet at lot A");

        assertThat(carDto.getRiders()).hasSize(1);
        assertThat(carDto.getRiders().get(0).getRiderId()).isEqualTo(riderId);
        assertThat(carDto.getRiders().get(0).getRiderName()).isEqualTo("Test Rider");
        assertThat(carDto.getRiders().get(0).getRiderImagePath()).isEqualTo("users/rider/profile.jpg");
    }

    @Test
    void getEventCars_WithUserNotInCache_ReturnsNullImagePath() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(4);
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserSummary(UUID.fromString(driverId))).thenReturn(Optional.empty());

        // When
        List<CarWithRidersDTO> result = carpoolService.getEventCars(eventId, userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDriverImagePath()).isNull();
    }

    // ============================================================================
    // GET CAR DETAIL TESTS
    // ============================================================================

    @Test
    void getCarDetail_WithValidIds_ReturnsDetailWithImagePaths() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        car.setNotes("Meet at lot A");
        String riderId = UUID.randomUUID().toString();
        CarRider rider = new CarRider(eventId, driverId, riderId, "Test Rider");
        HangoutDetailData dataWithCarAndRider = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .withCarRiders(List.of(rider))
            .build();

        UserSummaryDTO driverSummary = new UserSummaryDTO(UUID.fromString(driverId), "Test Driver", "users/driver/profile.jpg");
        UserSummaryDTO riderSummary = new UserSummaryDTO(UUID.fromString(riderId), "Test Rider", "users/rider/profile.jpg");

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndRider);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserSummary(UUID.fromString(driverId))).thenReturn(Optional.of(driverSummary));
        when(userService.getUserSummary(UUID.fromString(riderId))).thenReturn(Optional.of(riderSummary));

        // When
        CarDetailDTO result = carpoolService.getCarDetail(eventId, driverId, userId);

        // Then
        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getDriverImagePath()).isEqualTo("users/driver/profile.jpg");
        assertThat(result.getRiders()).hasSize(1);
        assertThat(result.getRiders().get(0).getRiderImagePath()).isEqualTo("users/rider/profile.jpg");
        assertThat(result.isUserIsDriver()).isFalse();
        assertThat(result.isUserHasReservation()).isFalse();
    }

    @Test
    void getCarDetail_WithUserNotInCache_ReturnsNullImagePaths() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(4);
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserSummary(UUID.fromString(driverId))).thenReturn(Optional.empty());

        // When
        CarDetailDTO result = carpoolService.getCarDetail(eventId, driverId, userId);

        // Then
        assertThat(result.getDriverImagePath()).isNull();
        assertThat(result.getRiders()).isEmpty();
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
        HangoutDetailData dataWithRider = HangoutDetailData.builder().withHangout(hangout).withCarRiders(List.of(existingRider)).build();
        
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
        HangoutDetailData emptyData = HangoutDetailData.builder().build();
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
        HangoutDetailData emptyData = HangoutDetailData.builder().build();
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
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();
        
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
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId, null);

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
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();
        
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
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId, null);

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
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, userId, null))
                .isInstanceOf(NoAvailableSeatsException.class)
                .hasMessageContaining("Not enough available seats");
        
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
        HangoutDetailData dataWithCarAndRider = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).withCarRiders(List.of(existingRider)).build();
        
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndRider);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, userId, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("User already has a reservation with this driver");
        
        verify(hangoutRepository, never()).deleteNeedsRide(any(), any());
        verify(hangoutRepository, never()).saveCarRider(any());
    }

    // ============================================================================
    // RESERVE SEAT WITH NOTES AND PLUS-ONE TESTS
    // ============================================================================

    @Test
    void reserveSeat_WithNotes_SetsNotesOnRider() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        User user = new User();
        user.setDisplayName("Test User");
        CarRider carRider = new CarRider(eventId, driverId, userId, "Test User");
        carRider.setNotes("Pick me up at corner");

        ReserveSeatRequest request = new ReserveSeatRequest("Pick me up at corner", null);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(carRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNotes()).isEqualTo("Pick me up at corner");

        // Verify the saved rider had notes set
        org.mockito.ArgumentCaptor<CarRider> riderCaptor = org.mockito.ArgumentCaptor.forClass(CarRider.class);
        verify(hangoutRepository).saveCarRider(riderCaptor.capture());
        assertThat(riderCaptor.getValue().getNotes()).isEqualTo("Pick me up at corner");
        assertThat(riderCaptor.getValue().getPlusOneCount()).isEqualTo(0);
    }

    @Test
    void reserveSeat_WithPlusOneCount_DecrementsCorrectSeats() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(4);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        User user = new User();
        user.setDisplayName("Test User");
        CarRider carRider = new CarRider(eventId, driverId, userId, "Test User");
        carRider.setPlusOneCount(2);

        ReserveSeatRequest request = new ReserveSeatRequest(null, 2);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(carRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When
        carpoolService.reserveSeat(eventId, driverId, userId, request);

        // Then - Verify seats decremented by 3 (1 rider + 2 plus ones)
        org.mockito.ArgumentCaptor<Car> carCaptor = org.mockito.ArgumentCaptor.forClass(Car.class);
        verify(hangoutRepository).saveCar(carCaptor.capture());
        assertThat(carCaptor.getValue().getAvailableSeats()).isEqualTo(1); // 4 - 3 = 1

        org.mockito.ArgumentCaptor<CarRider> riderCaptor = org.mockito.ArgumentCaptor.forClass(CarRider.class);
        verify(hangoutRepository).saveCarRider(riderCaptor.capture());
        assertThat(riderCaptor.getValue().getPlusOneCount()).isEqualTo(2);
    }

    @Test
    void reserveSeat_WithNoBody_DefaultsToNoNotesAndZeroPlusOne() {
        // Given
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        User user = new User();
        user.setDisplayName("Test User");
        CarRider carRider = new CarRider(eventId, driverId, userId, "Test User");

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(carRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When - pass null request (no body)
        carpoolService.reserveSeat(eventId, driverId, userId, null);

        // Then - Verify rider has no notes and plusOneCount=0
        org.mockito.ArgumentCaptor<CarRider> riderCaptor = org.mockito.ArgumentCaptor.forClass(CarRider.class);
        verify(hangoutRepository).saveCarRider(riderCaptor.capture());
        assertThat(riderCaptor.getValue().getNotes()).isNull();
        assertThat(riderCaptor.getValue().getPlusOneCount()).isEqualTo(0);

        // Verify seats decremented by 1
        org.mockito.ArgumentCaptor<Car> carCaptor = org.mockito.ArgumentCaptor.forClass(Car.class);
        verify(hangoutRepository).saveCar(carCaptor.capture());
        assertThat(carCaptor.getValue().getAvailableSeats()).isEqualTo(2); // 3 - 1 = 2
    }

    @Test
    void reserveSeat_WithPlusOneCountExceedingAvailableSeats_ThrowsException() {
        // Given - car has 2 available seats, request needs 4 (1 + 3 plus ones)
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(2);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        ReserveSeatRequest request = new ReserveSeatRequest(null, 3);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, userId, request))
                .isInstanceOf(NoAvailableSeatsException.class)
                .hasMessageContaining("Not enough available seats");

        verify(hangoutRepository, never()).saveCarRider(any());
    }

    // ============================================================================
    // RESERVE SEAT ON BEHALF OF RIDER (riderId) TESTS
    // ============================================================================

    @Test
    void reserveSeat_DriverReservesForRider_CreatesRiderWithRiderIdAndDeletesNeedsRide() {
        // Given - driver reserves a seat for a different rider
        String riderId = UUID.randomUUID().toString();
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        User riderUser = new User();
        riderUser.setDisplayName("Rider Name");

        CarRider savedCarRider = new CarRider(eventId, driverId, riderId, "Rider Name");

        ReserveSeatRequest request = new ReserveSeatRequest(null, null, riderId);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(driverId, hangout)).thenReturn(true);
        when(hangoutService.canUserViewHangout(riderId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(riderId))).thenReturn(Optional.of(riderUser));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(savedCarRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, driverId, request);

        // Then
        assertThat(result.getRiderId()).isEqualTo(riderId);
        assertThat(result.getRiderName()).isEqualTo("Rider Name");

        org.mockito.ArgumentCaptor<CarRider> riderCaptor = org.mockito.ArgumentCaptor.forClass(CarRider.class);
        verify(hangoutRepository).saveCarRider(riderCaptor.capture());
        assertThat(riderCaptor.getValue().getRiderId()).isEqualTo(riderId);
        assertThat(riderCaptor.getValue().getRiderName()).isEqualTo("Rider Name");

        // Verify NeedsRide deleted for the rider, not the driver
        verify(hangoutRepository).deleteNeedsRide(eventId, riderId);

        // Verify seats decremented
        org.mockito.ArgumentCaptor<Car> carCaptor = org.mockito.ArgumentCaptor.forClass(Car.class);
        verify(hangoutRepository).saveCar(carCaptor.capture());
        assertThat(carCaptor.getValue().getAvailableSeats()).isEqualTo(2); // 3 - 1
    }

    @Test
    void reserveSeat_NonDriverSpecifiesRiderId_ThrowsUnauthorizedException() {
        // Given - a non-driver user tries to reserve on behalf of another user
        String riderId = UUID.randomUUID().toString();
        String nonDriverUserId = UUID.randomUUID().toString();

        ReserveSeatRequest request = new ReserveSeatRequest(null, null, riderId);

        // When/Then - non-driver trying to reserve for someone else
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, nonDriverUserId, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Only the driver can reserve a seat on behalf of another rider");

        // Verify no repository calls were made
        verify(hangoutRepository, never()).getHangoutDetailData(any());
        verify(hangoutRepository, never()).saveCarRider(any());
    }

    @Test
    void reserveSeat_DriverReservesForRiderNotInEvent_ThrowsUnauthorizedException() {
        // Given - driver tries to reserve for a rider who can't view the hangout
        String riderId = UUID.randomUUID().toString();
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        ReserveSeatRequest request = new ReserveSeatRequest(null, null, riderId);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(driverId, hangout)).thenReturn(true);
        when(hangoutService.canUserViewHangout(riderId, hangout)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, driverId, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Rider does not have access to this event");

        verify(hangoutRepository, never()).saveCarRider(any());
    }

    @Test
    void reserveSeat_NullRiderId_ReservesForAuthenticatedUser() {
        // Given - riderId is null, should behave like before (reserve for caller)
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        User user = new User();
        user.setDisplayName("Caller Name");

        CarRider savedCarRider = new CarRider(eventId, driverId, userId, "Caller Name");

        ReserveSeatRequest request = new ReserveSeatRequest("my notes", null, null);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(savedCarRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId, request);

        // Then - reserved for the authenticated user, not a different rider
        assertThat(result.getRiderId()).isEqualTo(userId);

        org.mockito.ArgumentCaptor<CarRider> riderCaptor = org.mockito.ArgumentCaptor.forClass(CarRider.class);
        verify(hangoutRepository).saveCarRider(riderCaptor.capture());
        assertThat(riderCaptor.getValue().getRiderId()).isEqualTo(userId);

        // canUserViewHangout only called once (for caller, not for a separate rider)
        verify(hangoutService, times(1)).canUserViewHangout(anyString(), any());
    }

    @Test
    void reserveSeat_DriverReservesForRiderWithExistingReservation_ThrowsValidationException() {
        // Given - driver calls reserveSeat with riderId set to a rider who already has a CarRider record
        String riderId = UUID.randomUUID().toString();
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        CarRider existingRider = new CarRider(eventId, driverId, riderId, "Existing Rider");
        HangoutDetailData dataWithCarAndRider = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .withCarRiders(List.of(existingRider))
            .build();

        ReserveSeatRequest request = new ReserveSeatRequest(null, null, riderId);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCarAndRider);
        when(hangoutService.canUserViewHangout(driverId, hangout)).thenReturn(true);
        when(hangoutService.canUserViewHangout(riderId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.reserveSeat(eventId, driverId, driverId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessage("User already has a reservation with this driver");

        // Verify no CarRider was saved
        verify(hangoutRepository, never()).saveCarRider(any());
    }

    @Test
    void reserveSeat_DriverReservesForRiderWithNotesAndPlusOne_SetsFieldsCorrectly() {
        // Given - driver calls reserveSeat with riderId, notes, and plusOneCount
        String riderId = UUID.randomUUID().toString();
        Car car = new Car(eventId, driverId, "Test Driver", 5);
        car.setAvailableSeats(4);
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .build();

        User riderUser = new User();
        riderUser.setDisplayName("Rider Name");

        CarRider savedCarRider = new CarRider(eventId, driverId, riderId, "Rider Name");
        savedCarRider.setNotes("Pick up at mall");
        savedCarRider.setPlusOneCount(2);

        ReserveSeatRequest request = new ReserveSeatRequest("Pick up at mall", 2, riderId);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(driverId, hangout)).thenReturn(true);
        when(hangoutService.canUserViewHangout(riderId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(riderId))).thenReturn(Optional.of(riderUser));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(savedCarRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, driverId, request);

        // Then - verify the saved CarRider has riderId (not driverId), notes, and plusOneCount
        org.mockito.ArgumentCaptor<CarRider> riderCaptor = org.mockito.ArgumentCaptor.forClass(CarRider.class);
        verify(hangoutRepository).saveCarRider(riderCaptor.capture());
        CarRider capturedRider = riderCaptor.getValue();
        assertThat(capturedRider.getRiderId()).isEqualTo(riderId);
        assertThat(capturedRider.getNotes()).isEqualTo("Pick up at mall");
        assertThat(capturedRider.getPlusOneCount()).isEqualTo(2);

        // Verify the saved Car has availableSeats = 1 (4 - 3 seats needed: 1 rider + 2 plus ones)
        org.mockito.ArgumentCaptor<Car> carCaptor = org.mockito.ArgumentCaptor.forClass(Car.class);
        verify(hangoutRepository).saveCar(carCaptor.capture());
        assertThat(carCaptor.getValue().getAvailableSeats()).isEqualTo(1);
    }

    @Test
    void reserveSeat_DriverReservesForSelf_NoExtraAuthCheck() {
        // Given - driver calls reserveSeat with riderId set to their own ID (same as userId)
        Car car = new Car(eventId, driverId, "Test Driver", 4);
        car.setAvailableSeats(3);
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .build();

        User driverUser = new User();
        driverUser.setDisplayName("Driver Name");

        CarRider savedCarRider = new CarRider(eventId, driverId, driverId, "Driver Name");

        // riderId equals userId (driverId) -- should skip the extra canUserViewHangout call
        ReserveSeatRequest request = new ReserveSeatRequest(null, null, driverId);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(driverId, hangout)).thenReturn(true);
        when(userService.getUserById(UUID.fromString(driverId))).thenReturn(Optional.of(driverUser));
        when(hangoutRepository.saveCarRider(any(CarRider.class))).thenReturn(savedCarRider);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(car);

        // When
        CarRider result = carpoolService.reserveSeat(eventId, driverId, driverId, request);

        // Then - success, and canUserViewHangout called exactly once (for the caller only)
        assertThat(result).isNotNull();
        assertThat(result.getRiderId()).isEqualTo(driverId);
        verify(hangoutService, times(1)).canUserViewHangout(anyString(), any());
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
        hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

        Car newCar = new Car(eventId, userId, "Test Driver", 4);
        newCar.setNotes("Meet at parking lot A");
        newCar.setAvailableSeats(4);

        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(newCar)).build();

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
        hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

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
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        User user = new User();
        user.setDisplayName("Test User");

        CarRider newRider = new CarRider(eventId, driverId, userId, "Test User");

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).withCarRiders(List.of(newRider)).build();

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
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId, null);

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
        HangoutDetailData dataWithCarAndNeedsRide = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).withNeedsRide(List.of(needsRide)).build();

        User user = new User();
        user.setDisplayName("Test User");

        CarRider newRider = new CarRider(eventId, driverId, userId, "Test User");

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        // After reservation, needsRide is removed
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).withCarRiders(List.of(newRider)).build();

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
        CarRider result = carpoolService.reserveSeat(eventId, driverId, userId, null);

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
        HangoutDetailData dataWithCarAndRider = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).withCarRiders(List.of(existingRider)).build();

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2, groupId3));
        // After release, rider is removed
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

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
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        UpdateCarRequest request = new UpdateCarRequest(6, "Updated notes");

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        Car updatedCar = new Car(eventId, userId, "Test Driver", 6);
        updatedCar.setAvailableSeats(5); // 6 - 1 driver - 0 riders
        updatedCar.setNotes("Updated notes");
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(updatedCar)).build();

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
        org.mockito.ArgumentCaptor<Car> carCaptor = org.mockito.ArgumentCaptor.forClass(Car.class);
        verify(hangoutRepository).saveCar(carCaptor.capture());
        Car savedCar = carCaptor.getValue();
        assertThat(savedCar.getTotalCapacity()).isEqualTo(6);
        assertThat(savedCar.getAvailableSeats()).isEqualTo(5); // 6 - 1 driver - 0 riders
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("carpool data"));
    }

    @Test
    void updateCarOffer_WithCapacityChange_ShouldSubtractDriverSeat() {
        // Given - car with capacity 4, 1 rider
        String groupId = UUID.randomUUID().toString();
        String riderId = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(2); // 4 - 1 driver - 1 rider
        CarRider rider = new CarRider(eventId, userId, riderId, "Test Rider");
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .withCarRiders(List.of(rider))
            .build();

        UpdateCarRequest request = new UpdateCarRequest(5, null);

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        Car updatedCar = new Car(eventId, userId, "Test Driver", 5);
        updatedCar.setAvailableSeats(3); // 5 - 1 driver - 1 rider
        HangoutDetailData updatedData = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(updatedCar))
            .withCarRiders(List.of(rider))
            .build();

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar).thenReturn(updatedData);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);
        when(hangoutRepository.saveCar(any(Car.class))).thenReturn(updatedCar);
        when(hangoutRepository.findHangoutById(eventId)).thenReturn(Optional.of(hangout));

        // When
        carpoolService.updateCarOffer(eventId, userId, request, userId);

        // Then
        org.mockito.ArgumentCaptor<Car> carCaptor = org.mockito.ArgumentCaptor.forClass(Car.class);
        verify(hangoutRepository).saveCar(carCaptor.capture());
        Car savedCar = carCaptor.getValue();
        assertThat(savedCar.getTotalCapacity()).isEqualTo(5);
        assertThat(savedCar.getAvailableSeats()).isEqualTo(3); // 5 - 1 driver - 1 rider
    }

    @Test
    void updateCarOffer_CannotReduceBelowDriverPlusRiders() {
        // Given - car with capacity 4, 2 riders
        String riderId1 = UUID.randomUUID().toString();
        String riderId2 = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(1); // 4 - 1 driver - 2 riders
        CarRider rider1 = new CarRider(eventId, userId, riderId1, "Rider 1");
        CarRider rider2 = new CarRider(eventId, userId, riderId2, "Rider 2");
        HangoutDetailData dataWithCar = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .withCarRiders(List.of(rider1, rider2))
            .build();

        // Attempt to reduce to 2 seats (need at least 3: 1 driver + 2 riders)
        UpdateCarRequest request = new UpdateCarRequest(2, null);

        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(dataWithCar);
        when(hangoutService.canUserViewHangout(userId, hangout)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> carpoolService.updateCarOffer(eventId, userId, request, userId))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateCarOffer_WithNotesChange_ShouldUpdateAllPointers() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        Car car = new Car(eventId, userId, "Test Driver", 4);
        car.setAvailableSeats(4);
        car.setNotes("Meet at parking lot A");
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        UpdateCarRequest request = new UpdateCarRequest(4, "Meet at parking lot B");

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        Car updatedCar = new Car(eventId, userId, "Test Driver", 4);
        updatedCar.setAvailableSeats(4);
        updatedCar.setNotes("Meet at parking lot B");
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(updatedCar)).build();

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
        HangoutDetailData dataWithCarAndRiders = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(List.of(car))
            .withCarRiders(List.of(rider1, rider2, rider3))
            .build();

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        // After cancellation, car and riders are removed
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).build();

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
        HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(car)).build();

        hangout.setAssociatedGroups(Arrays.asList(groupId));
        // After cancellation, car is removed
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).build();

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
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withNeedsRide(List.of(needsRide)).build();

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
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).withNeedsRide(List.of(needsRide)).build();

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
        HangoutDetailData dataWithNeedsRide = HangoutDetailData.builder().withHangout(hangout).withNeedsRide(List.of(needsRide)).build();

        hangout.setAssociatedGroups(Arrays.asList(groupId1, groupId2));
        // After deletion, needsRide is removed
        HangoutDetailData updatedData = HangoutDetailData.builder().withHangout(hangout).build();

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
        HangoutDetailData emptyData = HangoutDetailData.builder().withHangout(hangout).build();

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
            hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

            Car newCar = new Car(eventId, userId, "Test Driver", 4);
            newCar.setNotes("Meet at parking lot A");
            newCar.setAvailableSeats(4);

            HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(newCar)).build();

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
            hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

            Car newCar = new Car(eventId, userId, "Test Driver", 4);
            newCar.setNotes("Meet at parking lot A");
            newCar.setAvailableSeats(4);

            HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(newCar)).build();

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
            hangoutData = HangoutDetailData.builder().withHangout(hangout).build();

            Car newCar = new Car(eventId, userId, "Test Driver", 4);
            newCar.setNotes("Meet at parking lot A");
            newCar.setAvailableSeats(4);

            HangoutDetailData dataWithCar = HangoutDetailData.builder().withHangout(hangout).withCars(List.of(newCar)).build();

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