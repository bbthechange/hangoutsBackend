package com.bbthechange.inviter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new event series by converting an existing hangout
 * and adding a new member to form a multi-part series.
 */
public class CreateSeriesRequest {
    
    @NotBlank(message = "Initial hangout ID is required")
    private String initialHangoutId;
    
    @Valid
    @NotNull(message = "New member request is required")
    private CreateHangoutRequest newMemberRequest;
    
    public CreateSeriesRequest() {
    }
    
    public CreateSeriesRequest(String initialHangoutId, CreateHangoutRequest newMemberRequest) {
        this.initialHangoutId = initialHangoutId;
        this.newMemberRequest = newMemberRequest;
    }
    
    public String getInitialHangoutId() {
        return initialHangoutId;
    }
    
    public void setInitialHangoutId(String initialHangoutId) {
        this.initialHangoutId = initialHangoutId;
    }
    
    public CreateHangoutRequest getNewMemberRequest() {
        return newMemberRequest;
    }
    
    public void setNewMemberRequest(CreateHangoutRequest newMemberRequest) {
        this.newMemberRequest = newMemberRequest;
    }
}