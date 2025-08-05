package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating car offer details.
 */
public class UpdateCarRequest {
    
    @Min(value = 2, message = "Car must have at least 2 total seats (including driver)")
    @Max(value = 8, message = "Car cannot have more than 8 total seats")
    private Integer totalCapacity;
    
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;
    
    public UpdateCarRequest() {}
    
    public UpdateCarRequest(Integer totalCapacity, String notes) {
        this.totalCapacity = totalCapacity;
        this.notes = notes;
    }
    
    public Integer getTotalCapacity() {
        return totalCapacity;
    }
    
    public void setTotalCapacity(Integer totalCapacity) {
        this.totalCapacity = totalCapacity;
    }
    
    public String getNotes() {
        return notes != null ? notes.trim() : null;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
}