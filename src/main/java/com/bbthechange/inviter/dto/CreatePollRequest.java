package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for creating a new poll.
 */
public class CreatePollRequest {
    
    @NotBlank(message = "Poll title is required")
    @Size(min = 1, max = 200, message = "Poll title must be between 1 and 200 characters")
    private String title;
    
    @Size(max = 1000, message = "Poll description cannot exceed 1000 characters")
    private String description;
    
    private boolean multipleChoice = false;
    
    @Size(min = 2, max = 10, message = "Poll must have between 2 and 10 options")
    private List<String> options;
    
    public CreatePollRequest() {}
    
    public CreatePollRequest(String title, String description, boolean multipleChoice, List<String> options) {
        this.title = title;
        this.description = description;
        this.multipleChoice = multipleChoice;
        this.options = options;
    }
    
    public String getTitle() {
        return title != null ? title.trim() : null;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getDescription() {
        return description != null ? description.trim() : null;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public boolean isMultipleChoice() {
        return multipleChoice;
    }
    
    public void setMultipleChoice(boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
    }
    
    public List<String> getOptions() {
        return options;
    }
    
    public void setOptions(List<String> options) {
        this.options = options;
    }
}