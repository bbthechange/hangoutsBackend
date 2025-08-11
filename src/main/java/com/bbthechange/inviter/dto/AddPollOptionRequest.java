package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for adding a new option to an existing poll.
 */
public class AddPollOptionRequest {
    
    @NotBlank(message = "Option text is required")
    @Size(min = 1, max = 100, message = "Option text must be between 1 and 100 characters")
    private String text;
    
    public AddPollOptionRequest() {}
    
    public AddPollOptionRequest(String text) {
        this.text = text;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
}