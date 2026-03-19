package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.EnrichmentResult;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for place enrichment via Google Places API (New).
 * Provides synchronous enrichment for the /places/enrich endpoint and
 * async enrichment for read-path safety nets.
 */
public interface PlaceEnrichmentService {

    /**
     * Whether the enrichment service is enabled (Google API key configured).
     */
    boolean isEnabled();

    /**
     * Synchronous enrichment. Cache lookup first, then full pipeline if miss.
     * Used by POST /places/enrich endpoint.
     * Blocks until enrichment completes or times out.
     *
     * @return enrichment result with status (CACHED, ENRICHED, FAILED)
     */
    EnrichmentResult enrichPlaceSync(String name, double latitude, double longitude,
                                     String googlePlaceId, String applePlaceId);

    /**
     * Look up enrichment cache only (no Google API calls).
     * Used by idea creation to copy cached data onto new ideas.
     *
     * @return cached entry if found
     */
    Optional<PlaceEnrichmentCacheEntry> lookupCache(String name, Double latitude, Double longitude,
                                                    String googlePlaceId);

    /**
     * Async enrichment for a specific idea. Runs the full sync pipeline in a background thread,
     * then copies the result onto the idea record.
     * Used by read-path safety net and idea creation fallback.
     */
    void enrichPlaceAsync(String groupId, String listId, String ideaId,
                          String name, Double latitude, Double longitude,
                          String googlePlaceId, String applePlaceId);

    /**
     * Read-path safety net. Scans returned ideas and triggers async enrichment for those that need it:
     * - null enrichmentStatus (legacy data)
     * - PENDING (write-time enrichment not complete)
     * - FAILED with failureCount &lt; 3 (retryable)
     * - ENRICHED but stale (&gt;30 days)
     * Max 5 triggers per call.
     */
    void triggerReadPathEnrichment(List<IdeaListMember> members, String groupId, String listId);
}
