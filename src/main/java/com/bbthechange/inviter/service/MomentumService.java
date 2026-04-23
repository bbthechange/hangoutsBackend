package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.MomentumDTO;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;

/**
 * Service responsible for computing and managing hangout momentum state.
 *
 * Momentum progresses: BUILDING -> GAINING_MOMENTUM -> CONFIRMED
 * Once CONFIRMED, a hangout is never demoted.
 */
public interface MomentumService {

    /**
     * Initialize momentum for a newly created hangout.
     * Sets CONFIRMED if locked in, BUILDING if floated.
     * The hangout is saved with momentum fields set before pointers are created.
     *
     * @param hangout         The newly created hangout (not yet saved)
     * @param confirmed       true = "Lock it in", false/null = "Float it"
     * @param creatorUserId   User ID of the creator
     */
    void initializeMomentum(Hangout hangout, boolean confirmed, String creatorUserId);

    /**
     * Recompute momentum score for a hangout from current signals.
     * Idempotent — safe to call multiple times. Updates canonical record and all pointers.
     * Called after any signal change (RSVP, attribute add, time/location add, etc.)
     *
     * @param hangoutId The hangout ID to recompute
     */
    void recomputeMomentum(String hangoutId);

    /**
     * Explicitly confirm a hangout (manual "It's on!" action by a group member/host).
     *
     * @param hangoutId           The hangout ID to confirm
     * @param confirmedByUserId   User ID who confirmed
     */
    void confirmHangout(String hangoutId, String confirmedByUserId);

    /**
     * Send the manual "It's on!" notification for a hangout that was already confirmed
     * by a caller writing state directly (e.g. {@code HangoutServiceImpl.updateHangout}
     * sets the CONFIRMED fields on its local hangout copy). State is assumed to be
     * persisted already — this method only fires the push notification.
     *
     * @param hangoutId           The hangout ID that was just confirmed
     * @param confirmedByUserId   User ID who confirmed
     */
    void notifyManualConfirmation(String hangoutId, String confirmedByUserId);

    /**
     * Build a MomentumDTO for API response from a Hangout's stored fields.
     * Handles score normalization using the group's dynamic threshold.
     *
     * @param hangout  The canonical hangout record
     * @param groupId  The group ID (used to compute dynamic threshold for normalization)
     * @return MomentumDTO with normalized score
     */
    MomentumDTO buildMomentumDTO(Hangout hangout, String groupId);

    /**
     * Build a MomentumDTO from pointer data (for feed responses).
     * Uses raw score as-is (threshold unavailable at feed time).
     *
     * @param pointer The hangout pointer record
     * @return MomentumDTO from pointer fields
     */
    MomentumDTO buildMomentumDTOFromPointer(HangoutPointer pointer);
}
