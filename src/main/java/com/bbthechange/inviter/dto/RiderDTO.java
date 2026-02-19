package com.bbthechange.inviter.dto;

/**
 * DTO for rider information.
 */
public class RiderDTO {
    private String riderId;
    private String riderName;
    private String riderImagePath;
    private String notes;
    private int plusOneCount;

    public RiderDTO() {}

    public RiderDTO(String riderId, String riderName, String notes, int plusOneCount) {
        this.riderId = riderId;
        this.riderName = riderName;
        this.notes = notes;
        this.plusOneCount = plusOneCount;
    }

    public RiderDTO(String riderId, String riderName, String riderImagePath, String notes, int plusOneCount) {
        this.riderId = riderId;
        this.riderName = riderName;
        this.riderImagePath = riderImagePath;
        this.notes = notes;
        this.plusOneCount = plusOneCount;
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
