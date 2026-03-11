package com.bbthechange.inviter.service;

/**
 * Service interface for asynchronous place enrichment via Google Places API.
 * Enriches idea records with photos, ratings, hours, etc.
 */
public interface PlaceEnrichmentService {

    /**
     * Whether the enrichment service is enabled (Google API key configured).
     */
    boolean isEnabled();

    /**
     * Asynchronously enrich an idea with Google Places data.
     * Returns immediately; enrichment happens in background.
     *
     * @param groupId       the group containing the idea list
     * @param listId        the idea list ID
     * @param ideaId        the idea to enrich
     * @param googlePlaceId the Google Places API place_id
     */
    void enrichPlaceAsync(String groupId, String listId, String ideaId, String googlePlaceId);

    /**
     * Check idea list members for stale enrichment data and trigger
     * async re-enrichment for up to 5 stale items per call.
     * Stale = lastEnrichedAt older than configured threshold (default 30 days).
     *
     * @param members the list of idea members to check
     * @param groupId the group ID
     * @param listId  the list ID
     */
    void triggerStaleReEnrichment(java.util.List<com.bbthechange.inviter.model.IdeaListMember> members,
                                  String groupId, String listId);
}
