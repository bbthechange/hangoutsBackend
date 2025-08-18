package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new idea in an idea list.
 */
@Data
@NoArgsConstructor
public class CreateIdeaRequest {
    
    @Size(min = 1, max = 200, message = "Idea name must be between 1 and 200 characters")
    private String name;
    
    @Size(max = 500, message = "URL must be less than 500 characters")
    private String url;
    
    @Size(max = 1000, message = "Note must be less than 1000 characters")
    private String note;
    
    public CreateIdeaRequest(String name, String url, String note) {
        this.name = name;
        this.url = url;
        this.note = note;
    }
    
    // Input sanitization in getters
    public String getName() { 
        return name != null ? name.trim() : null; 
    }
    
    public String getUrl() {
        return url != null ? url.trim() : null;
    }
    
    public String getNote() {
        return note != null ? note.trim() : null;
    }
}