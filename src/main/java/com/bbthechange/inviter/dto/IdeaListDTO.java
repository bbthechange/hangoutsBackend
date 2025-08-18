package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListCategory;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for IdeaList information.
 * Can be populated with ideas for efficient list display.
 */
@Data
@NoArgsConstructor
public class IdeaListDTO {
    
    private String id;
    private String name;
    private IdeaListCategory category;
    private String note;
    private String createdBy;
    private Instant createdAt;
    private List<IdeaDTO> ideas = new ArrayList<>();
    
    // Constructor from IdeaList entity
    public IdeaListDTO(IdeaList ideaList) {
        this.id = ideaList.getListId();
        this.name = ideaList.getName();
        this.category = ideaList.getCategory();
        this.note = ideaList.getNote();
        this.createdBy = ideaList.getCreatedBy();
        this.createdAt = ideaList.getCreatedAt();
        this.ideas = new ArrayList<>();
    }
    
    // Helper method to add an idea
    public void addIdea(IdeaDTO idea) {
        if (this.ideas == null) {
            this.ideas = new ArrayList<>();
        }
        this.ideas.add(idea);
    }
}