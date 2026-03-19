package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;

public class EnrichmentData {

    private String cachedPhotoUrl;
    private Double cachedRating;
    private Integer cachedPriceLevel;
    private String cachedHoursJson;
    private String phoneNumber;
    private String websiteUrl;
    private String menuUrl;
    private String googlePlaceId;

    public EnrichmentData() {}

    public static EnrichmentData fromCacheEntry(PlaceEnrichmentCacheEntry entry) {
        EnrichmentData data = new EnrichmentData();
        data.cachedPhotoUrl = entry.getCachedPhotoUrl();
        data.cachedRating = entry.getCachedRating();
        data.cachedPriceLevel = entry.getCachedPriceLevel();
        data.cachedHoursJson = entry.getCachedHoursJson();
        data.phoneNumber = entry.getPhoneNumber();
        data.websiteUrl = entry.getWebsiteUrl();
        data.menuUrl = entry.getMenuUrl();
        data.googlePlaceId = entry.getGooglePlaceId();
        return data;
    }

    public String getCachedPhotoUrl() { return cachedPhotoUrl; }
    public void setCachedPhotoUrl(String cachedPhotoUrl) { this.cachedPhotoUrl = cachedPhotoUrl; }

    public Double getCachedRating() { return cachedRating; }
    public void setCachedRating(Double cachedRating) { this.cachedRating = cachedRating; }

    public Integer getCachedPriceLevel() { return cachedPriceLevel; }
    public void setCachedPriceLevel(Integer cachedPriceLevel) { this.cachedPriceLevel = cachedPriceLevel; }

    public String getCachedHoursJson() { return cachedHoursJson; }
    public void setCachedHoursJson(String cachedHoursJson) { this.cachedHoursJson = cachedHoursJson; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public String getMenuUrl() { return menuUrl; }
    public void setMenuUrl(String menuUrl) { this.menuUrl = menuUrl; }

    public String getGooglePlaceId() { return googlePlaceId; }
    public void setGooglePlaceId(String googlePlaceId) { this.googlePlaceId = googlePlaceId; }
}
