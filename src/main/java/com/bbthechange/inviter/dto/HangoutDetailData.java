package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import lombok.Data;

import java.util.List;

/**
 * Data container for all event-related information retrieved from a single item collection query.
 * Contains the event metadata plus all related items (polls, cars, votes, attendance, riders).
 */
@Data
public class HangoutDetailData {

    private Hangout hangout;
    private List<Poll> polls;
    private List<PollOption> pollOptions;
    private List<Car> cars;
    private List<Vote> votes;
    private List<InterestLevel> attendance;
    private List<CarRider> carRiders;
    private List<NeedsRide> needsRide;
    private List<Participation> participations;
    private List<ReservationOffer> reservationOffers;

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