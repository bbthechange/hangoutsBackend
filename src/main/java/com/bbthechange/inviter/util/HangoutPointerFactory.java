package com.bbthechange.inviter.util;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;

/**
 * Centralized factory for creating and updating HangoutPointer records.
 *
 * <p>All denormalized field copying from Hangout → HangoutPointer is defined in exactly
 * one place ({@link #applyHangoutFields}), eliminating the class of bugs where a new
 * Hangout field is added but only some pointer-construction sites are updated.
 *
 * <p>Two entry points:
 * <ul>
 *   <li>{@link #fromHangout} — for <b>creation</b> (empty collections, participantCount=0)</li>
 *   <li>{@link #applyHangoutFields} — for <b>update lambdas</b> inside
 *       {@code PointerUpdateService.updatePointerWithRetry()}, preserving existing
 *       collections, participantCount, participationSummary, and version</li>
 * </ul>
 */
public final class HangoutPointerFactory {

    private HangoutPointerFactory() {} // Utility class

    /**
     * Create a NEW HangoutPointer with all denormalized fields from a Hangout.
     * For creation only — collections are empty, participantCount is 0.
     * Callers should set collections separately if needed.
     */
    public static HangoutPointer fromHangout(Hangout hangout, String groupId) {
        HangoutPointer pointer = new HangoutPointer(
            groupId, hangout.getHangoutId(), hangout.getTitle());

        pointer.setStatus("ACTIVE");
        pointer.setParticipantCount(0);

        applyHangoutFields(pointer, hangout);

        // gsi1pk for UserGroupIndex (gsi1sk already set by applyHangoutFields)
        pointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));

        return pointer;
    }

    /**
     * Apply all Hangout-sourced fields to an EXISTING pointer.
     * Preserves collections, participantCount, participationSummary,
     * version, and other pointer-only fields.
     * Use inside PointerUpdateService.updatePointerWithRetry() lambdas.
     */
    public static void applyHangoutFields(HangoutPointer pointer, Hangout hangout) {
        pointer.setTitle(hangout.getTitle());
        pointer.setDescription(hangout.getDescription());
        pointer.setStartTimestamp(hangout.getStartTimestamp());
        pointer.setEndTimestamp(hangout.getEndTimestamp());
        pointer.setVisibility(hangout.getVisibility());
        pointer.setSeriesId(hangout.getSeriesId());
        pointer.setMainImagePath(hangout.getMainImagePath());
        pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());
        pointer.setHostAtPlaceUserId(hangout.getHostAtPlaceUserId());

        pointer.setTimeInput(hangout.getTimeInput());
        pointer.setLocation(hangout.getLocation());

        pointer.setExternalId(hangout.getExternalId());
        pointer.setExternalSource(hangout.getExternalSource());
        pointer.setIsGeneratedTitle(hangout.getIsGeneratedTitle());

        pointer.setTicketLink(hangout.getTicketLink());
        pointer.setTicketsRequired(hangout.getTicketsRequired());
        pointer.setDiscountCode(hangout.getDiscountCode());

        // Update UserGroupIndex sort key when timestamps change
        // (EntityTimeIndex uses startTimestamp directly via @DynamoDbSecondarySortKey)
        if (hangout.getStartTimestamp() != null) {
            pointer.setGsi1sk(String.valueOf(hangout.getStartTimestamp()));
        }
    }
}
