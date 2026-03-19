package com.bbthechange.inviter.service;

/**
 * Holds extracted details from Google Places API (New) Place Details response.
 */
public class PlaceDetailsResult {

    private Double rating;
    private Integer priceLevel;
    private String phoneNumber;       // nationalPhoneNumber
    private String websiteUrl;        // websiteUri
    private String cachedHoursJson;   // JSON array from currentOpeningHours.weekdayDescriptions
    private String photoName;         // first photo resource name for subsequent photo fetch
    private String googleMapsUri;

    public PlaceDetailsResult() {}

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getPriceLevel() { return priceLevel; }
    public void setPriceLevel(Integer priceLevel) { this.priceLevel = priceLevel; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }

    public String getCachedHoursJson() { return cachedHoursJson; }
    public void setCachedHoursJson(String cachedHoursJson) { this.cachedHoursJson = cachedHoursJson; }

    public String getPhotoName() { return photoName; }
    public void setPhotoName(String photoName) { this.photoName = photoName; }

    public String getGoogleMapsUri() { return googleMapsUri; }
    public void setGoogleMapsUri(String googleMapsUri) { this.googleMapsUri = googleMapsUri; }
}
