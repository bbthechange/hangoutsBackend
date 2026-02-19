package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Car;
import com.bbthechange.inviter.model.CarRider;
import java.util.List;

/**
 * DTO for detailed car information with riders and driver details.
 */
public class CarDetailDTO {
    
    private String eventId;
    private String driverId;
    private String driverName;
    private int totalCapacity;
    private int availableSeats;
    private String driverImagePath;
    private String notes;
    private List<RiderDetailDTO> riders;
    private boolean userIsDriver;
    private boolean userHasReservation;

    public CarDetailDTO() {}

    public CarDetailDTO(Car car, List<CarRider> riders, boolean userIsDriver, boolean userHasReservation) {
        this.eventId = car.getEventId();
        this.driverId = car.getDriverId();
        this.driverName = car.getDriverName();
        this.totalCapacity = car.getTotalCapacity();
        this.availableSeats = car.getAvailableSeats();
        this.notes = car.getNotes();
        this.riders = riders.stream()
            .map(RiderDetailDTO::new)
            .toList();
        this.userIsDriver = userIsDriver;
        this.userHasReservation = userHasReservation;
    }

    public String getDriverImagePath() {
        return driverImagePath;
    }

    public void setDriverImagePath(String driverImagePath) {
        this.driverImagePath = driverImagePath;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getDriverId() {
        return driverId;
    }
    
    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }
    
    public String getDriverName() {
        return driverName;
    }
    
    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }
    
    public int getTotalCapacity() {
        return totalCapacity;
    }
    
    public void setTotalCapacity(int totalCapacity) {
        this.totalCapacity = totalCapacity;
    }
    
    public int getAvailableSeats() {
        return availableSeats;
    }
    
    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public List<RiderDetailDTO> getRiders() {
        return riders;
    }
    
    public void setRiders(List<RiderDetailDTO> riders) {
        this.riders = riders;
    }
    
    public boolean isUserIsDriver() {
        return userIsDriver;
    }
    
    public void setUserIsDriver(boolean userIsDriver) {
        this.userIsDriver = userIsDriver;
    }
    
    public boolean isUserHasReservation() {
        return userHasReservation;
    }
    
    public void setUserHasReservation(boolean userHasReservation) {
        this.userHasReservation = userHasReservation;
    }
}