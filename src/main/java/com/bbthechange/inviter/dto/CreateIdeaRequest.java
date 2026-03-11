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

    @Size(max = 2000, message = "Image URL must be less than 2000 characters")
    private String imageUrl;

    @Size(max = 200, message = "External ID must be less than 200 characters")
    private String externalId;

    @Size(max = 50, message = "External source must be less than 50 characters")
    private String externalSource;

    // Place fields
    @Size(max = 200, message = "Google Place ID must be less than 200 characters")
    private String googlePlaceId;

    @Size(max = 200, message = "Apple Place ID must be less than 200 characters")
    private String applePlaceId;

    @Size(max = 500, message = "Address must be less than 500 characters")
    private String address;

    private Double latitude;
    private Double longitude;

    @Size(max = 50, message = "Place category must be less than 50 characters")
    private String placeCategory;

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

    public String getImageUrl() {
        return imageUrl != null ? imageUrl.trim() : null;
    }

    public String getExternalId() {
        return externalId != null ? externalId.trim() : null;
    }

    public String getExternalSource() {
        return externalSource != null ? externalSource.trim() : null;
    }

    public String getGooglePlaceId() {
        return googlePlaceId != null ? googlePlaceId.trim() : null;
    }

    public String getApplePlaceId() {
        return applePlaceId != null ? applePlaceId.trim() : null;
    }

    public String getAddress() {
        return address != null ? address.trim() : null;
    }

    public String getPlaceCategory() {
        return placeCategory != null ? placeCategory.trim() : null;
    }
}