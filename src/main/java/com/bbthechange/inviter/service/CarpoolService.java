package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Car;
import com.bbthechange.inviter.model.CarRider;
import com.bbthechange.inviter.model.NeedsRide;
import java.util.List;

/**
 * Service interface for carpool management within events.
 */
public interface CarpoolService {
    
    /**
     * Offer a car for carpooling to an event.
     * Users must have access to the event to offer carpooling.
     */
    Car offerCar(String eventId, OfferCarRequest request, String userId);
    
    /**
     * Get all car offers for an event with rider information.
     * Users must have access to the event to view car offers.
     */
    List<CarWithRidersDTO> getEventCars(String eventId, String userId);
    
    /**
     * Get detailed information about a specific car offer.
     * Users must have access to the event to view car details.
     */
    CarDetailDTO getCarDetail(String eventId, String driverId, String userId);
    
    /**
     * Reserve a seat in a car.
     * Atomically creates rider record and decreases available seats.
     */
    CarRider reserveSeat(String eventId, String driverId, String userId);
    
    /**
     * Release a reserved seat in a car.
     * Atomically removes rider record and increases available seats.
     */
    void releaseSeat(String eventId, String driverId, String userId);
    
    /**
     * Update car offer details.
     * Only the driver can update their car offer.
     */
    Car updateCarOffer(String eventId, String driverId, UpdateCarRequest request, String userId);
    
    /**
     * Cancel a car offer.
     * Only the driver can cancel their car offer.
     * Removes all rider reservations and notifies affected users.
     */
    void cancelCarOffer(String eventId, String driverId, String userId);
    
    /**
     * Get all users who need a ride for an event.
     * Users must have access to the event to view ride requests.
     */
    List<NeedsRideDTO> getNeedsRideRequests(String eventId, String userId);
    
    /**
     * Create or update a ride request for the authenticated user.
     * Fails if the user already has a reserved seat in a car.
     */
    NeedsRide createNeedsRideRequest(String eventId, String userId, NeedsRideRequest request);
    
    /**
     * Delete the ride request for the authenticated user.
     */
    void deleteNeedsRideRequest(String eventId, String userId);
}