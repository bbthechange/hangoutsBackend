package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;

import java.util.List;

/**
 * DO NOT USE. Use HangoutDetail instead
 * Data container for all event-related information retrieved from a single item collection query.
 * Contains the event metadata plus all related items (polls, cars, votes, attendance, riders).
 */
@Deprecated
public class EventDetailData {
    
    private final Event event;
    private final List<Poll> polls;
    private final List<Car> cars;
    private final List<Vote> votes;
    private final List<InterestLevel> attendance;
    private final List<CarRider> carRiders;
    
    public EventDetailData(Event event, List<Poll> polls, List<Car> cars, 
                          List<Vote> votes, List<InterestLevel> attendance, List<CarRider> carRiders) {
        this.event = event;
        this.polls = polls != null ? polls : List.of();
        this.cars = cars != null ? cars : List.of();
        this.votes = votes != null ? votes : List.of();
        this.attendance = attendance != null ? attendance : List.of();
        this.carRiders = carRiders != null ? carRiders : List.of();
    }
    
    public Event getEvent() {
        return event;
    }
    
    public List<Poll> getPolls() {
        return polls;
    }
    
    public List<Car> getCars() {
        return cars;
    }
    
    public List<Vote> getVotes() {
        return votes;
    }
    
    public List<InterestLevel> getAttendance() {
        return attendance;
    }
    
    public List<CarRider> getCarRiders() {
        return carRiders;
    }
}