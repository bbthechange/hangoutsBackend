package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

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
}