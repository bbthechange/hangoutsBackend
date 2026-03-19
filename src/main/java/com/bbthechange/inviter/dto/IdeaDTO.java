package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.IdeaListMember;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for individual ideas within idea lists.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdeaDTO {
    
    private String id;
    private String name;
    private String url;
    private String note;
    private String addedBy;
    private String addedByName;
    private String addedByImagePath;
    private Instant addedTime;
    private String imageUrl;
    private String externalId;
    private String externalSource;
    private List<InterestedUserDTO> interestedUsers;
    private int interestCount;

    // Place fields
    private String googlePlaceId;
    private String applePlaceId;
    private String address;
    private Double latitude;
    private Double longitude;
    private String cachedPhotoUrl;
    private Double cachedRating;
    private Integer cachedPriceLevel;
    private String phoneNumber;
    private String websiteUrl;
    private String menuUrl;
    private String cachedHoursJson;
    private String placeCategory;
    private String lastEnrichedAt;
    private String enrichmentStatus;

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
        this.interestedUsers = new ArrayList<>();
        this.interestCount = 1; // minimum 1 for the creator
        // Place fields
        this.googlePlaceId = member.getGooglePlaceId();
        this.applePlaceId = member.getApplePlaceId();
        this.address = member.getAddress();
        this.latitude = member.getLatitude();
        this.longitude = member.getLongitude();
        this.cachedPhotoUrl = member.getCachedPhotoUrl();
        this.cachedRating = member.getCachedRating();
        this.cachedPriceLevel = member.getCachedPriceLevel();
        this.phoneNumber = member.getPhoneNumber();
        this.websiteUrl = member.getWebsiteUrl();
        this.menuUrl = member.getMenuUrl();
        this.cachedHoursJson = member.getCachedHoursJson();
        this.placeCategory = member.getPlaceCategory();
        this.lastEnrichedAt = member.getLastEnrichedAt() != null ? member.getLastEnrichedAt().toString() : null;
        this.enrichmentStatus = member.getEnrichmentStatus();
    }
}