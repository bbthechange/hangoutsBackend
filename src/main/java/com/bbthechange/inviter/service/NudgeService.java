package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.NudgeDTO;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;

import java.util.List;

/**
 * Service for computing action-oriented nudges for hangouts.
 *
 * <p>Nudges are computed fresh on each request — never stored in the database.
 * They guide group members toward the next concrete action for a hangout.
 */
public interface NudgeService {

    /**
     * Compute applicable nudges for a canonical hangout record.
     * Used when building the hangout detail response.
     *
     * @param hangout       The canonical hangout (must have momentum fields populated)
     * @param interestLevels The attendance records for the hangout
     * @return List of applicable nudges, empty if none apply
     */
    List<NudgeDTO> computeNudges(Hangout hangout, List<InterestLevel> interestLevels);

    /**
     * Compute applicable nudges from a hangout pointer (for feed responses).
     * Uses the denormalized interest levels available on the pointer.
     *
     * @param pointer The hangout pointer record (with denormalized interestLevels)
     * @return List of applicable nudges, empty if none apply
     */
    List<NudgeDTO> computeNudgesFromPointer(HangoutPointer pointer);
}
