package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

/**
 * DTO for hangout detail response with all related data.
 *
 * <p>Use the builder to create instances - callers only need to provide the fields they have:
 * <pre>
 * HangoutDetailDTO.builder()
 *     .withHangout(hangout)
 *     .withPolls(polls)
 *     .build();
 * </pre>
 * All list fields default to empty lists if not specified.
 */
@Data
@Builder(setterPrefix = "with", toBuilder = true)
@AllArgsConstructor
public class HangoutDetailDTO {
    private Hangout hangout;
    @Builder.Default
    private List<HangoutAttributeDTO> attributes = List.of();
    @Builder.Default
    private List<PollWithOptionsDTO> polls = List.of();
    @Builder.Default
    private List<Car> cars = List.of();
    @Builder.Default
    private List<Vote> votes = List.of();
    @Builder.Default
    private List<InterestLevel> attendance = List.of();
    @Builder.Default
    private List<CarRider> carRiders = List.of();
    @Builder.Default
    private List<NeedsRideDTO> needsRide = List.of();
    @Builder.Default
    private List<ParticipationDTO> participations = List.of();
    @Builder.Default
    private List<ReservationOfferDTO> reservationOffers = List.of();

    // Host at place resolved fields (fetched from user cache)
    private String hostAtPlaceDisplayName;
    private String hostAtPlaceImagePath;

    // Momentum data (populated by MomentumService at read time)
    private MomentumDTO momentum;

    // Time suggestions (active suggestions for timeless hangouts)
    @Builder.Default
    private List<TimeSuggestionDTO> timeSuggestions = List.of();

    // Action-oriented nudges (computed by NudgeService at read time — never stored)
    @Builder.Default
    private List<NudgeDTO> nudges = List.of();

    public List<NudgeDTO> getNudges() {
        return nudges != null ? nudges : new ArrayList<>();
    }

    public void setNudges(List<NudgeDTO> nudges) {
        this.nudges = nudges;
    }
}