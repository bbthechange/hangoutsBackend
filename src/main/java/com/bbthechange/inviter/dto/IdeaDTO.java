package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.IdeaListMember;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for individual ideas within idea lists.
 */
@Data
@NoArgsConstructor
public class IdeaDTO {
    
    private String id;
    private String name;
    private String url;
    private String note;
    private String addedBy;
    private Instant addedTime;
    private String imageUrl;
    private String externalId;
    private String externalSource;
    
    // Constructor from IdeaListMember entity
    public IdeaDTO(IdeaListMember member) {
        this.id = member.getIdeaId();
        this.name = member.getName();
        this.url = member.getUrl();
        this.note = member.getNote();
        this.addedBy = member.getAddedBy();
        this.addedTime = member.getAddedTime();
        this.imageUrl = member.getImageUrl();
        this.externalId = member.getExternalId();
        this.externalSource = member.getExternalSource();
    }
}