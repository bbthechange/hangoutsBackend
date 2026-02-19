package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for reserving a seat in a car.
 * All fields are optional for backward compatibility.
 */
public class ReserveSeatRequest {

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Min(value = 0, message = "Plus-one count cannot be negative")
    @Max(value = 7, message = "Plus-one count cannot exceed 7")
    private Integer plusOneCount;

    public ReserveSeatRequest() {}

    public ReserveSeatRequest(String notes, Integer plusOneCount) {
        this.notes = notes;
        this.plusOneCount = plusOneCount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getPlusOneCount() {
        return plusOneCount;
    }

    public void setPlusOneCount(Integer plusOneCount) {
        this.plusOneCount = plusOneCount;
    }
}
