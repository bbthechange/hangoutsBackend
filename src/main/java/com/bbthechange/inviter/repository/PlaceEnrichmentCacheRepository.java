package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;

import java.util.Optional;

public interface PlaceEnrichmentCacheRepository {
    Optional<PlaceEnrichmentCacheEntry> findByCacheKey(String cacheKey);
    Optional<PlaceEnrichmentCacheEntry> findByGooglePlaceId(String googlePlaceId);
    void save(PlaceEnrichmentCacheEntry entry);
}
