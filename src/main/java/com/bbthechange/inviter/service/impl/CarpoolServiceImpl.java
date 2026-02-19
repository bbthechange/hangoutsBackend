package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.CarpoolService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of CarpoolService for carpool management within events.
 */
@Service
public class CarpoolServiceImpl implements CarpoolService {

    private static final Logger logger = LoggerFactory.getLogger(CarpoolServiceImpl.class);

    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final HangoutService hangoutService;
    private final UserService userService;
    private final PointerUpdateService pointerUpdateService;
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public CarpoolServiceImpl(HangoutRepository hangoutRepository, GroupRepository groupRepository,
                             HangoutService hangoutService, UserService userService,
                             PointerUpdateService pointerUpdateService,
                             GroupTimestampService groupTimestampService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.hangoutService = hangoutService;
        this.userService = userService;
        this.pointerUpdateService = pointerUpdateService;
        this.groupTimestampService = groupTimestampService;
    }
    
    @Override
    public Car offerCar(String eventId, OfferCarRequest request, String userId) {
        logger.info("User {} offering car with {} seats for event {}", userId, request.getTotalCapacity(), eventId);

        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot offer carpooling for this event");
        }
        
        // Get user display name for denormalization
        User user = userService.getUserById(UUID.fromString(userId))
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        String driverName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
        
        // Create car offer
        Car car = new Car(eventId, userId, driverName, request.getTotalCapacity());
        car.setNotes(request.getNotes());

        Car savedCar = hangoutRepository.saveCar(car);

        // Update pointer records with new car data
        updatePointersWithCarpoolData(eventId);

        logger.info("Successfully created car offer {} for event {}", savedCar.getDriverId(), eventId);

        return savedCar;
    }
    
    @Override
    public List<CarWithRidersDTO> getEventCars(String eventId, String userId) {
        logger.debug("Getting car offers for event {} for user {}", eventId, userId);
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view carpooling for this event");
        }
        
        // Get cars and their riders from hangout detail data
        List<Car> cars = hangoutData.getCars();
        List<CarRider> allRiders = hangoutData.getCarRiders();
        
        // Build CarWithRidersDTO by grouping riders by driver
        return cars.stream()
            .map(car -> {
                List<CarRider> carRiders = allRiders.stream()
                    .filter(rider -> rider.getDriverId().equals(car.getDriverId()))
                    .toList();
                return new CarWithRidersDTO(car, carRiders);
            })
            .toList();
    }
    
    @Override
    public CarDetailDTO getCarDetail(String eventId, String driverId, String userId) {
        logger.debug("Getting car detail for driver {} in event {} for user {}", driverId, eventId, userId);
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view carpooling for this event");
        }
        
        // Find the specific car
        Car car = hangoutData.getCars().stream()
            .filter(c -> c.getDriverId().equals(driverId))
            .findFirst()
            .orElseThrow(() -> new CarNotFoundException("Car offer not found for driver: " + driverId));
        
        boolean userIsDriver = userId.equals(driverId);
        
        // Get riders for this car and check if user has reservation
        List<CarRider> riders = hangoutData.getCarRiders().stream()
            .filter(rider -> rider.getDriverId().equals(driverId))
            .toList();
        boolean userHasReservation = riders.stream()
            .anyMatch(rider -> rider.getRiderId().equals(userId));
        
        return new CarDetailDTO(car, riders, userIsDriver, userHasReservation);
    }
    
    @Override
    public CarRider reserveSeat(String eventId, String driverId, String userId, ReserveSeatRequest request) {
        logger.info("User {} reserving seat with driver {} for event {}", userId, driverId, eventId);

        // Extract notes and plusOneCount from request (null-safe)
        String notes = request != null ? request.getNotes() : null;
        int effectivePlusOneCount = (request != null && request.getPlusOneCount() != null) ? request.getPlusOneCount() : 0;
        int seatsNeeded = 1 + effectivePlusOneCount;

        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot reserve seats for this event");
        }

        // Find the car
        Car car = hangoutData.getCars().stream()
            .filter(c -> c.getDriverId().equals(driverId))
            .findFirst()
            .orElseThrow(() -> new CarNotFoundException("Car offer not found for driver: " + driverId));

        if (car.getAvailableSeats() < seatsNeeded) {
            throw new NoAvailableSeatsException(
                "Not enough available seats: need " + seatsNeeded + " but only " + car.getAvailableSeats() + " available");
        }

        // Check if user already has a reservation
        List<CarRider> existingRiders = hangoutData.getCarRiders().stream()
            .filter(rider -> rider.getDriverId().equals(driverId) && rider.getRiderId().equals(userId))
            .toList();

        if (!existingRiders.isEmpty()) {
            throw new ValidationException("User already has a reservation with this driver");
        }

        // Get user display name for denormalization
        User user = userService.getUserById(UUID.fromString(userId))
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        String riderName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();

        // Create CarRider record with notes and plusOneCount
        CarRider carRider = new CarRider(eventId, driverId, userId, riderName);
        carRider.setNotes(notes);
        carRider.setPlusOneCount(effectivePlusOneCount);
        CarRider savedRider = hangoutRepository.saveCarRider(carRider);

        // Update car available seats (decrease by seatsNeeded)
        car.setAvailableSeats(car.getAvailableSeats() - seatsNeeded);
        hangoutRepository.saveCar(car);
        
        // Auto-delete any existing ride request for this user
        try {
            hangoutRepository.deleteNeedsRide(eventId, userId);
            logger.info("Automatically deleted 'needs ride' request for user {} after seat reservation.", userId);
        } catch (Exception e) {
            // Log this, but do not fail the transaction. The user has a seat,
            // which is the most important state. A dangling request is a minor issue.
            logger.warn("Failed to auto-delete 'needs ride' request for user {}.", userId, e);
        }

        // Update pointer records with new rider data
        updatePointersWithCarpoolData(eventId);

        logger.info("Successfully reserved seat for user {} with driver {} in event {}", userId, driverId, eventId);
        return savedRider;
    }
    
    @Override
    public void releaseSeat(String eventId, String driverId, String userId) {
        logger.info("User {} releasing seat with driver {} for event {}", userId, driverId, eventId);
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot modify reservations for this event");
        }
        
        // Find the car
        Car car = hangoutData.getCars().stream()
            .filter(c -> c.getDriverId().equals(driverId))
            .findFirst()
            .orElseThrow(() -> new CarNotFoundException("Car offer not found for driver: " + driverId));
        
        // Check if user has a reservation
        List<CarRider> userRiders = hangoutData.getCarRiders().stream()
            .filter(rider -> rider.getDriverId().equals(driverId) && rider.getRiderId().equals(userId))
            .toList();
        
        if (userRiders.isEmpty()) {
            throw new ValidationException("User does not have a reservation with this driver");
        }

        CarRider riderToRelease = userRiders.get(0);

        // Remove CarRider record
        hangoutRepository.deleteCarRider(eventId, driverId, userId);

        // Update car available seats (restore rider + plus ones)
        car.setAvailableSeats(car.getAvailableSeats() + riderToRelease.getTotalSeatsNeeded());
        hangoutRepository.saveCar(car);

        // Update pointer records with updated rider data
        updatePointersWithCarpoolData(eventId);

        logger.info("Successfully released seat for user {} from driver {} in event {}", userId, driverId, eventId);
    }
    
    @Override
    public Car updateCarOffer(String eventId, String driverId, UpdateCarRequest request, String userId) {
        logger.info("User {} updating car offer for event {}", userId, eventId);
        
        if (!userId.equals(driverId)) {
            throw new UnauthorizedException("Only the driver can update their car offer");
        }
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot modify car offers for this event");
        }
        
        // Find the car
        Car car = hangoutData.getCars().stream()
            .filter(c -> c.getDriverId().equals(driverId))
            .findFirst()
            .orElseThrow(() -> new CarNotFoundException("Car offer not found for driver: " + driverId));
        
        // Update car details
        if (request.getTotalCapacity() != null) {
            // Count total occupied seats (riders + their plus ones)
            int occupiedSeats = hangoutData.getCarRiders().stream()
                .filter(rider -> rider.getDriverId().equals(driverId))
                .mapToInt(CarRider::getTotalSeatsNeeded)
                .sum();

            int newTotalCapacity = request.getTotalCapacity();
            if (newTotalCapacity < occupiedSeats) {
                throw new ValidationException("Cannot reduce capacity below occupied seats (" + occupiedSeats + ")");
            }

            car.setTotalCapacity(newTotalCapacity);
            car.setAvailableSeats(newTotalCapacity - occupiedSeats);
        }
        if (request.getNotes() != null) {
            car.setNotes(request.getNotes());
        }

        Car savedCar = hangoutRepository.saveCar(car);

        // Update pointer records with updated car data
        updatePointersWithCarpoolData(eventId);

        return savedCar;
    }
    
    @Override
    public void cancelCarOffer(String eventId, String driverId, String userId) {
        logger.info("User {} canceling car offer for event {}", userId, eventId);
        
        if (!userId.equals(driverId)) {
            throw new UnauthorizedException("Only the driver can cancel their car offer");
        }
        
        // Get hangout and verify user can view it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot cancel car offers for this event");
        }
        
        // Remove all rider records for this car
        List<CarRider> ridersToRemove = hangoutData.getCarRiders().stream()
            .filter(rider -> rider.getDriverId().equals(driverId))
            .toList();
        
        for (CarRider rider : ridersToRemove) {
            hangoutRepository.deleteCarRider(eventId, driverId, rider.getRiderId());
            logger.debug("Removed rider {} from canceled car offer by driver {}", rider.getRiderId(), driverId);
        }
        
        // Remove car offer
        hangoutRepository.deleteCar(eventId, driverId);

        // Update pointer records with updated car data (car now removed)
        updatePointersWithCarpoolData(eventId);

        logger.info("Successfully canceled car offer for driver {} in event {} (removed {} riders)",
                   driverId, eventId, ridersToRemove.size());
    }

    @Override
    public List<NeedsRideDTO> getNeedsRideRequests(String eventId, String userId) {
        logger.debug("Getting ride requests for event {} by user {}", eventId, userId);

        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }

        Hangout hangout = hangoutData.getHangout();

        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot view ride requests for this event");
        }
        List<NeedsRide> needsRideList = hangoutData.getNeedsRide();

        return needsRideList.stream()
                .map(NeedsRideDTO::new)
                .toList();
    }

    @Override
    public NeedsRide createNeedsRideRequest(String eventId, String userId, NeedsRideRequest request) {
        logger.info("User {} creating ride request for event {}", userId, eventId);

        // Get hangout and verify user can access it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot request ride for this event");
        }

        // Check if user already has a reserved seat in any car
        boolean hasReservedSeat = hangoutData.getCarRiders().stream()
                .anyMatch(rider -> rider.getRiderId().equals(userId));
        
        if (hasReservedSeat) {
            throw new ValidationException("Cannot request a ride when you already have a reserved seat");
        }

        // Create or update the needs ride request
        NeedsRide needsRide = new NeedsRide(eventId, userId, request.getNotes());
        NeedsRide savedNeedsRide = hangoutRepository.saveNeedsRide(needsRide);

        // Update pointer records with new ride request data
        updatePointersWithCarpoolData(eventId);

        logger.info("Successfully created/updated ride request for user {} in event {}", userId, eventId);
        return savedNeedsRide;
    }

    @Override
    public void deleteNeedsRideRequest(String eventId, String userId) {
        logger.info("User {} deleting ride request for event {}", userId, eventId);

        // Get hangout and verify user can access it
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(eventId);
        if (hangoutData.getHangout() == null) {
            throw new EventNotFoundException("Event not found: " + eventId);
        }
        
        Hangout hangout = hangoutData.getHangout();
        if (!hangoutService.canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("Cannot delete ride request for this event");
        }

        // Delete the needs ride request (idempotent operation)
        hangoutRepository.deleteNeedsRide(eventId, userId);

        // Update pointer records with updated ride request data
        updatePointersWithCarpoolData(eventId);

        logger.info("Successfully deleted ride request for user {} in event {}", userId, eventId);
    }

    // ============================================================================
    // POINTER SYNCHRONIZATION
    // ============================================================================

    /**
     * Update all pointer records with the current carpool data from the canonical hangout.
     * This method should be called after any car/rider/needsRide create/update/delete operation.
     *
     * Uses optimistic locking with retry to handle concurrent pointer updates.
     */
    private void updatePointersWithCarpoolData(String hangoutId) {
        // Get hangout to find associated groups
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            logger.warn("Cannot update pointers for non-existent hangout: {}", hangoutId);
            return;
        }

        Hangout hangout = hangoutOpt.get();
        List<String> associatedGroups = hangout.getAssociatedGroups();

        if (associatedGroups == null || associatedGroups.isEmpty()) {
            logger.debug("No associated groups for hangout {}, skipping pointer update", hangoutId);
            return;
        }

        // Get current carpool data from canonical record
        HangoutDetailData hangoutData = hangoutRepository.getHangoutDetailData(hangoutId);
        List<Car> cars = hangoutData.getCars();
        List<CarRider> carRiders = hangoutData.getCarRiders();
        List<NeedsRide> needsRide = hangoutData.getNeedsRide();

        // Update each group's pointer with optimistic locking retry
        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                pointer.setCars(new ArrayList<>(cars));
                pointer.setCarRiders(new ArrayList<>(carRiders));
                pointer.setNeedsRide(new ArrayList<>(needsRide));
            }, "carpool data");
        }

        // Update group timestamps for ETag invalidation
        groupTimestampService.updateGroupTimestamps(associatedGroups);
    }
}