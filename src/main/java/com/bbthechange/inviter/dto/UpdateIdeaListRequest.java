package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.IdeaListCategory;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an idea list (name, category, note).
 */
@Data
@NoArgsConstructor
public class UpdateIdeaListRequest {
    
    @Size(min = 1, max = 100, message = "Idea list name must be between 1 and 100 characters")
    private String name;
    
    private IdeaListCategory category;

    @Size(max = 500, message = "Note must be less than 500 characters")
    private String note;

    private Boolean isLocation;

    public UpdateIdeaListRequest(String name, IdeaListCategory category, String note) {
        this.name = name;
        this.category = category;
        this.note = note;
    }

    public UpdateIdeaListRequest(String name, IdeaListCategory category, String note, Boolean isLocation) {
        this.name = name;
        this.category = category;
        this.note = note;
        this.isLocation = isLocation;
    }
    
    // Input sanitization in getters
    public String getName() { 
        return name != null ? name.trim() : null; 
    }
    
    public String getNote() {
        return note != null ? note.trim() : null;
    }
}