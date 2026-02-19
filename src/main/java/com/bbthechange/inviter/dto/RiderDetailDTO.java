package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.CarRider;

/**
 * DTO for detailed rider information.
 */
public class RiderDetailDTO {
    private String riderId;
    private String riderName;
    private String riderImagePath;
    private String notes;
    private int plusOneCount;

    public RiderDetailDTO() {}

    public RiderDetailDTO(CarRider rider) {
        this.riderId = rider.getRiderId();
        this.riderName = rider.getRiderName();
        this.notes = rider.getNotes();
        this.plusOneCount = rider.getPlusOneCount();
    }

    public RiderDetailDTO(CarRider rider, String riderImagePath) {
        this(rider);
        this.riderImagePath = riderImagePath;
    }

    public String getRiderId() {
        return riderId;
    }

    public void setRiderId(String riderId) {
        this.riderId = riderId;
    }

    public String getRiderName() {
        return riderName;
    }

    public void setRiderName(String riderName) {
        this.riderName = riderName;
    }

    public String getRiderImagePath() {
        return riderImagePath;
    }

    public void setRiderImagePath(String riderImagePath) {
        this.riderImagePath = riderImagePath;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public int getPlusOneCount() {
        return plusOneCount;
    }

    public void setPlusOneCount(int plusOneCount) {
        this.plusOneCount = plusOneCount;
    }
}
