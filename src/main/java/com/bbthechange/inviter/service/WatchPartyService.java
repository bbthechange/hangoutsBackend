package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.watchparty.CreateWatchPartyRequest;
import com.bbthechange.inviter.dto.watchparty.UpdateWatchPartyRequest;
import com.bbthechange.inviter.dto.watchparty.WatchPartyDetailResponse;
import com.bbthechange.inviter.dto.watchparty.WatchPartyResponse;

/**
 * Service interface for managing TV Watch Party series.
 * Watch parties are event series that track TV seasons and auto-create hangouts for episodes.
 */
public interface WatchPartyService {

    /**
     * Create a new watch party series for a group.
     *
     * Phase 2: Episodes are provided directly in the request.
     * Phase 3: Episodes will be fetched from TVMaze automatically.
     *
     * Processing:
     * 1. Validate user is member of group
     * 2. Create or update Season record
     * 3. Apply episode combination logic (<20 hours apart)
     * 4. Calculate timestamps using defaultTime + timezone + optional dayOverride
     * 5. Create EventSeries with watch party fields
     * 6. Create Hangouts for each (combined) episode
     *
     * @param groupId The group to create the watch party in
     * @param request Watch party creation details
     * @param requestingUserId The user creating the watch party
     * @return Response with created series and hangouts
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user is not in group
     * @throws com.bbthechange.inviter.exception.ValidationException if request is invalid
     */
    WatchPartyResponse createWatchParty(String groupId, CreateWatchPartyRequest request, String requestingUserId);

    /**
     * Get detailed information about a watch party series.
     *
     * @param groupId The group the watch party belongs to
     * @param seriesId The watch party series ID
     * @param requestingUserId The user requesting the details
     * @return Detailed watch party information
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series not found
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user is not in group
     */
    WatchPartyDetailResponse getWatchParty(String groupId, String seriesId, String requestingUserId);

    /**
     * Update watch party series settings.
     *
     * Processing:
     * 1. Validate user is member of group
     * 2. Fetch and validate series (must be WATCH_PARTY type, belong to group)
     * 3. Validate timezone if provided
     * 4. Apply settings to series
     * 5. If changeExistingUpcomingHangouts=true, cascade changes to future hangouts
     * 6. Update series pointer and group timestamp for cache invalidation
     *
     * @param groupId The group the watch party belongs to
     * @param seriesId The watch party series ID to update
     * @param request Update request with new settings
     * @param requestingUserId The user requesting the update
     * @return Updated watch party details
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series not found
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user is not in group
     * @throws com.bbthechange.inviter.exception.ValidationException if request is invalid
     */
    WatchPartyDetailResponse updateWatchParty(String groupId, String seriesId,
                                               UpdateWatchPartyRequest request,
                                               String requestingUserId);

    /**
     * Delete a watch party series and all its hangouts.
     *
     * Note: The Season record is NOT deleted (other groups may use it).
     *
     * @param groupId The group the watch party belongs to
     * @param seriesId The watch party series ID to delete
     * @param requestingUserId The user requesting deletion
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series not found
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user is not in group
     */
    void deleteWatchParty(String groupId, String seriesId, String requestingUserId);

    /**
     * Set series-level interest for a watch party.
     * User can express GOING, INTERESTED, or NOT_GOING interest in the entire series.
     *
     * Processing:
     * 1. Lookup EventSeries by seriesId
     * 2. Get groupIds from the EventSeries
     * 3. Validate user is member of at least one associated group
     * 4. For each group, find and update SeriesPointer with new interest level
     * 5. Save the updated SeriesPointer(s)
     * 6. Update group timestamps for ETag invalidation
     *
     * @param seriesId The watch party series ID
     * @param level Interest level: "GOING", "INTERESTED", or "NOT_GOING"
     * @param requestingUserId The user setting the interest level
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series not found
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user is not in any associated group
     */
    void setUserInterest(String seriesId, String level, String requestingUserId);

    /**
     * Remove series-level interest for a watch party.
     * Idempotent: no error if the user has no existing interest.
     *
     * @param seriesId The watch party series ID
     * @param requestingUserId The user removing their interest
     * @throws com.bbthechange.inviter.exception.ResourceNotFoundException if series not found
     * @throws com.bbthechange.inviter.exception.UnauthorizedException if user is not in any associated group
     */
    void removeUserInterest(String seriesId, String requestingUserId);
}
