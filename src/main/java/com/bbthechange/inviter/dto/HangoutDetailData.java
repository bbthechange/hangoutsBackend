package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Data container for all event-related information retrieved from a single item collection query.
 * Contains the event metadata plus all related items (polls, cars, votes, attendance, riders).
 *
 * <p>Use the builder to create instances - callers only need to provide the fields they have:
 * <pre>
 * HangoutDetailData.builder()
 *     .withHangout(hangout)
 *     .withPolls(polls)
 *     .withCars(cars)
 *     .build();
 * </pre>
 * All list fields default to empty lists if not specified.
 */
@Data
@Builder(setterPrefix = "with", toBuilder = true)
public class HangoutDetailData {

    private Hangout hangout;
    @Builder.Default
    private List<Poll> polls = List.of();
    @Builder.Default
    private List<PollOption> pollOptions = List.of();
    @Builder.Default
    private List<Car> cars = List.of();
    @Builder.Default
    private List<Vote> votes = List.of();
    @Builder.Default
    private List<InterestLevel> attendance = List.of();
    @Builder.Default
    private List<CarRider> carRiders = List.of();
    @Builder.Default
    private List<NeedsRide> needsRide = List.of();
    @Builder.Default
    private List<Participation> participations = List.of();
    @Builder.Default
    private List<ReservationOffer> reservationOffers = List.of();

    /**
     * Full constructor for backward compatibility with existing production code.
     * Normalizes null lists to empty lists.
     *
     * @deprecated Prefer using the builder: {@code HangoutDetailData.builder().withHangout(h).build()}
     */
    @Deprecated
    public HangoutDetailData(Hangout hangout, List<Poll> polls, List<PollOption> pollOptions,
                             List<Car> cars, List<Vote> votes, List<InterestLevel> attendance,
                             List<CarRider> carRiders, List<NeedsRide> needsRide,
                             List<Participation> participations, List<ReservationOffer> reservationOffers) {
        this.hangout = hangout;
        this.polls = polls != null ? polls : List.of();
        this.pollOptions = pollOptions != null ? pollOptions : List.of();
        this.cars = cars != null ? cars : List.of();
        this.votes = votes != null ? votes : List.of();
        this.attendance = attendance != null ? attendance : List.of();
        this.carRiders = carRiders != null ? carRiders : List.of();
        this.needsRide = needsRide != null ? needsRide : List.of();
        this.participations = participations != null ? participations : List.of();
        this.reservationOffers = reservationOffers != null ? reservationOffers : List.of();
    }
}