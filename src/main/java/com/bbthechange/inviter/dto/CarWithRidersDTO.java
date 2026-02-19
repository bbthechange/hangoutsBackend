package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Car;
import com.bbthechange.inviter.model.CarRider;
import java.util.List;

/**
 * DTO for car with its riders list.
 */
public class CarWithRidersDTO {
    
    private String driverId;
    private String driverName;
    private int totalCapacity;
    private int availableSeats;
    private String driverImagePath;
    private String notes;
    private List<RiderDTO> riders;

    public CarWithRidersDTO() {}

    public CarWithRidersDTO(Car car, List<CarRider> riders) {
        this.driverId = car.getDriverId();
        this.driverName = car.getDriverName();
        this.totalCapacity = car.getTotalCapacity();
        this.availableSeats = car.getAvailableSeats();
        this.notes = car.getNotes();
        this.riders = riders.stream()
            .map(rider -> new RiderDTO(rider.getRiderId(), rider.getRiderName(), rider.getNotes(), rider.getPlusOneCount()))
            .toList();
    }

    public String getDriverImagePath() {
        return driverImagePath;
    }

    public void setDriverImagePath(String driverImagePath) {
        this.driverImagePath = driverImagePath;
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
    
    public List<RiderDTO> getRiders() {
        return riders;
    }
    
    public void setRiders(List<RiderDTO> riders) {
        this.riders = riders;
    }
}