package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.CarpoolService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of CarpoolService for carpool management within events.
 */
@Service
public class CarpoolServiceImpl implements CarpoolService {
    
    private static final Logger logger = LoggerFactory.getLogger(CarpoolServiceImpl.class);
    
    private final HangoutRepository hangoutRepository;
    private final HangoutService hangoutService;
    private final UserService userService;
    
    @Autowired
    public CarpoolServiceImpl(HangoutRepository hangoutRepository, HangoutService hangoutService, UserService userService) {
        this.hangoutRepository = hangoutRepository;
        this.hangoutService = hangoutService;
        this.userService = userService;
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
    public CarRider reserveSeat(String eventId, String driverId, String userId) {
        logger.info("User {} reserving seat with driver {} for event {}", userId, driverId, eventId);
        
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
        
        if (car.getAvailableSeats() <= 0) {
            throw new NoAvailableSeatsException("No available seats in this car");
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
        
        // Create CarRider record
        CarRider carRider = new CarRider(eventId, driverId, userId, riderName);
        CarRider savedRider = hangoutRepository.saveCarRider(carRider);
        
        // Update car available seats (decrease by 1)
        car.setAvailableSeats(car.getAvailableSeats() - 1);
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
        
        // Remove CarRider record
        hangoutRepository.deleteCarRider(eventId, driverId, userId);
        
        // Update car available seats (increase by 1)
        car.setAvailableSeats(car.getAvailableSeats() + 1);
        hangoutRepository.saveCar(car);
        
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
            // Count current riders
            long currentRiders = hangoutData.getCarRiders().stream()
                .filter(rider -> rider.getDriverId().equals(driverId))
                .count();
            
            int newTotalCapacity = request.getTotalCapacity();
            if (newTotalCapacity < currentRiders) {
                throw new ValidationException("Cannot reduce capacity below current rider count (" + currentRiders + ")");
            }
            
            car.setTotalCapacity(newTotalCapacity);
            car.setAvailableSeats((int) (newTotalCapacity - currentRiders));
        }
        if (request.getNotes() != null) {
            car.setNotes(request.getNotes());
        }
        
        return hangoutRepository.saveCar(car);
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
        
        logger.info("Successfully deleted ride request for user {} in event {}", userId, eventId);
    }
}