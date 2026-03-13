package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.NudgeDTO;
import com.bbthechange.inviter.dto.PollWithOptionsDTO;
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
     * Compute applicable nudges for a canonical hangout record, considering active suggestion polls.
     * When a suggestion poll exists for an attribute, the nudge is modified or suppressed.
     *
     * @param hangout        The canonical hangout
     * @param interestLevels The attendance records
     * @param polls          The polls for this hangout (used to detect suggestion polls)
     * @return List of applicable nudges
     */
    List<NudgeDTO> computeNudges(Hangout hangout, List<InterestLevel> interestLevels, List<PollWithOptionsDTO> polls);

    /**
     * Compute applicable nudges from a hangout pointer (for feed responses).
     * Uses the denormalized interest levels available on the pointer.
     *
     * @param pointer The hangout pointer record (with denormalized interestLevels)
     * @return List of applicable nudges, empty if none apply
     */
    List<NudgeDTO> computeNudgesFromPointer(HangoutPointer pointer);

    /**
     * Compute applicable nudges from a hangout pointer, considering suggestion polls.
     * Used in the feed path where poll DTOs are already computed.
     *
     * @param pointer The hangout pointer record
     * @param polls   The already-transformed poll DTOs (used to detect suggestion polls)
     * @return List of applicable nudges
     */
    List<NudgeDTO> computeNudgesFromPointer(HangoutPointer pointer, List<PollWithOptionsDTO> polls);
}
