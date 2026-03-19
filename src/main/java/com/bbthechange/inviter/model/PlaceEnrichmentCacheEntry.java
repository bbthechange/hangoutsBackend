package com.bbthechange.inviter.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

/**
 * DynamoDB entity for the PlaceEnrichmentCache table.
 *
 * <p>Stores Google Places enrichment data keyed by a normalized cache key
 * (lowercase name + rounded coordinates). Entries expire via DynamoDB TTL
 * after 90 days and are re-created on next use.</p>
 *
 * <p>Standalone table — NOT part of InviterTable single-table design.</p>
 */
@DynamoDbBean
public class PlaceEnrichmentCacheEntry {

    private String cacheKey;          // PK: "lowercase(name)_round(lat,4)_round(lng,4)"
    private String googlePlaceId;     // GSI1-PK (GooglePlaceIdIndex)
    private String applePlaceId;
    private String status;            // ENRICHED | FAILED | PERMANENTLY_FAILED
    private String cachedPhotoUrl;    // S3 path: "places/{cacheKey}/photo.jpg"
    private Double cachedRating;
    private Integer cachedPriceLevel;
    private String cachedHoursJson;   // JSON array of 7 day strings (Google weekday_text format)
    private String phoneNumber;
    private String websiteUrl;
    private String menuUrl;
    private String lastEnrichedAt;    // ISO 8601 string
    private Integer failureCount;     // 0-3, default 0
    private String createdAt;         // ISO 8601 string
    private Long ttl;                 // epoch seconds, 90 days from creation

    public PlaceEnrichmentCacheEntry() {
        this.failureCount = 0;
    }

    @DynamoDbPartitionKey
    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GooglePlaceIdIndex")
    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getApplePlaceId() {
        return applePlaceId;
    }

    public void setApplePlaceId(String applePlaceId) {
        this.applePlaceId = applePlaceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCachedPhotoUrl() {
        return cachedPhotoUrl;
    }

    public void setCachedPhotoUrl(String cachedPhotoUrl) {
        this.cachedPhotoUrl = cachedPhotoUrl;
    }

    public Double getCachedRating() {
        return cachedRating;
    }

    public void setCachedRating(Double cachedRating) {
        this.cachedRating = cachedRating;
    }

    public Integer getCachedPriceLevel() {
        return cachedPriceLevel;
    }

    public void setCachedPriceLevel(Integer cachedPriceLevel) {
        this.cachedPriceLevel = cachedPriceLevel;
    }

    public String getCachedHoursJson() {
        return cachedHoursJson;
    }

    public void setCachedHoursJson(String cachedHoursJson) {
        this.cachedHoursJson = cachedHoursJson;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public String getMenuUrl() {
        return menuUrl;
    }

    public void setMenuUrl(String menuUrl) {
        this.menuUrl = menuUrl;
    }

    public String getLastEnrichedAt() {
        return lastEnrichedAt;
    }

    public void setLastEnrichedAt(String lastEnrichedAt) {
        this.lastEnrichedAt = lastEnrichedAt;
    }

    public Integer getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Integer failureCount) {
        this.failureCount = failureCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }
}
