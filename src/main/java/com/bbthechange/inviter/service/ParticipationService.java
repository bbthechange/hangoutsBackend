package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CreateParticipationRequest;
import com.bbthechange.inviter.dto.ParticipationDTO;
import com.bbthechange.inviter.dto.UpdateParticipationRequest;

import java.util.List;

/**
 * Service interface for participation management within hangouts.
 * Handles ticket and reservation participation records.
 */
public interface ParticipationService {

    /**
     * Create a new participation record for a hangout.
     * Authorization: User must be a member of any group associated with the hangout.
     *
     * @param hangoutId The hangout ID
     * @param request The participation creation request
     * @param userId The authenticated user ID
     * @return The created participation DTO
     */
    ParticipationDTO createParticipation(String hangoutId, CreateParticipationRequest request, String userId);

    /**
     * Get all participations for a hangout.
     * Authorization: User must be a member of any group associated with the hangout.
     *
     * @param hangoutId The hangout ID
     * @param userId The authenticated user ID
     * @return List of participation DTOs
     */
    List<ParticipationDTO> getParticipations(String hangoutId, String userId);

    /**
     * Get a specific participation by ID.
     * Authorization: User must be a member of any group associated with the hangout.
     *
     * @param hangoutId The hangout ID
     * @param participationId The participation ID
     * @param userId The authenticated user ID
     * @return The participation DTO
     */
    ParticipationDTO getParticipation(String hangoutId, String participationId, String userId);

    /**
     * Update a participation record.
     * Authorization: Participation owner OR hangout host.
     *
     * @param hangoutId The hangout ID
     * @param participationId The participation ID
     * @param request The update request
     * @param userId The authenticated user ID
     * @return The updated participation DTO
     */
    ParticipationDTO updateParticipation(String hangoutId, String participationId,
                                         UpdateParticipationRequest request, String userId);

    /**
     * Delete a participation record.
     * Authorization: Participation owner OR hangout host.
     * Note: For CLAIMED_SPOT participations, use the dedicated unclaim endpoint instead.
     *
     * @param hangoutId The hangout ID
     * @param participationId The participation ID
     * @param userId The authenticated user ID
     */
    void deleteParticipation(String hangoutId, String participationId, String userId);
}
