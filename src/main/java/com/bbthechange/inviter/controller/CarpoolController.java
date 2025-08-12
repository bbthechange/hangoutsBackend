package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.CarpoolService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Car;
import com.bbthechange.inviter.model.CarRider;
import com.bbthechange.inviter.model.NeedsRide;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * REST controller for carpool management within events.
 * Handles car offers, seat reservations, and rider management.
 */
@RestController
@RequestMapping("/events/{eventId}/carpool")
@Validated
@Tag(name = "Carpool", description = "Carpool management within events")
public class CarpoolController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(CarpoolController.class);
    
    private final CarpoolService carpoolService;
    
    @Autowired
    public CarpoolController(CarpoolService carpoolService) {
        this.carpoolService = carpoolService;
    }
    
    @PostMapping("/cars")
    @Operation(summary = "Offer a car for the event")
    public ResponseEntity<Car> offerCar(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @Valid @RequestBody OfferCarRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} offering car with {} seats for event {}", userId, request.getTotalCapacity(), eventId);
        
        Car car = carpoolService.offerCar(eventId, request, userId);
        logger.info("Successfully created car offer {} for event {}", car.getDriverId(), eventId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(car);
    }
    
    @GetMapping("/cars")
    @Operation(summary = "Get all car offers for an event")
    public ResponseEntity<List<CarWithRidersDTO>> getEventCars(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        List<CarWithRidersDTO> cars = carpoolService.getEventCars(eventId, userId);
        logger.debug("Retrieved {} car offers for event {}", cars.size(), eventId);
        
        return ResponseEntity.ok(cars);
    }
    
    @PostMapping("/cars/{driverId}/reserve")
    @Operation(summary = "Reserve a seat in a car")
    public ResponseEntity<CarRider> reserveSeat(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid driver ID format") String driverId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} reserving seat with driver {} for event {}", userId, driverId, eventId);
        
        CarRider rider = carpoolService.reserveSeat(eventId, driverId, userId);
        
        return ResponseEntity.ok(rider);
    }
    
    @DeleteMapping("/cars/{driverId}/reserve")
    @Operation(summary = "Release a reserved seat")
    public ResponseEntity<Void> releaseSeat(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid driver ID format") String driverId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} releasing seat with driver {} for event {}", userId, driverId, eventId);
        
        carpoolService.releaseSeat(eventId, driverId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/cars/{driverId}")
    @Operation(summary = "Get car details with riders list")
    public ResponseEntity<CarDetailDTO> getCarDetail(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid driver ID format") String driverId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        CarDetailDTO carDetail = carpoolService.getCarDetail(eventId, driverId, userId);
        
        return ResponseEntity.ok(carDetail);
    }
    
    @PutMapping("/cars/{driverId}")
    @Operation(summary = "Update car offer details (driver only)")
    public ResponseEntity<Car> updateCarOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid driver ID format") String driverId,
            @Valid @RequestBody UpdateCarRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} updating car offer for event {}", userId, eventId);
        
        Car car = carpoolService.updateCarOffer(eventId, driverId, request, userId);
        
        return ResponseEntity.ok(car);
    }
    
    @DeleteMapping("/cars/{driverId}")
    @Operation(summary = "Cancel car offer (driver only)")
    public ResponseEntity<Void> cancelCarOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid driver ID format") String driverId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} canceling car offer for event {}", userId, eventId);
        
        carpoolService.cancelCarOffer(eventId, driverId, userId);
        
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/riderequests")
    @Operation(summary = "Get all users who need a ride")
    public ResponseEntity<List<NeedsRideDTO>> getNeedsRideRequests(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            HttpServletRequest httpRequest) {
        
        try {
            String userId = extractUserId(httpRequest);
            logger.debug("User {} getting ride requests for event {}", userId, eventId);
            
            List<NeedsRideDTO> requests = carpoolService.getNeedsRideRequests(eventId, userId);
            
            return ResponseEntity.ok(requests);
        } catch (Exception e) {
            logger.error("Unexpected error in getNeedsRideRequests for event {}: {} - {}", 
                eventId, e.getClass().getSimpleName(), e.getMessage(), e);
            throw e; // Re-throw to let global exception handler manage it
        }
    }

    @PostMapping("/riderequests")
    @Operation(summary = "Signal that you need a ride")
    public ResponseEntity<NeedsRide> createNeedsRideRequest(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @Valid @RequestBody NeedsRideRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} creating ride request for event {}", userId, eventId);
        
        NeedsRide needsRide = carpoolService.createNeedsRideRequest(eventId, userId, request);
        
        return ResponseEntity.ok(needsRide);
    }

    @DeleteMapping("/riderequests")
    @Operation(summary = "Cancel your request for a ride")
    public ResponseEntity<Void> deleteNeedsRideRequest(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("User {} deleting ride request for event {}", userId, eventId);
        
        carpoolService.deleteNeedsRideRequest(eventId, userId);
        
        return ResponseEntity.noContent().build();
    }
}