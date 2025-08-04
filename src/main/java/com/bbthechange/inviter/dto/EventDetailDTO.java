package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;

import java.util.List;

/**
 * Data Transfer Object for complete event details.
 * Contains all event-related information retrieved via item collection pattern.
 */
public class EventDetailDTO {
    
    private Event event;
    private List<Poll> polls;
    private List<Car> cars;
    private List<Vote> votes;
    private List<InterestLevel> attendance;
    
    public EventDetailDTO(Event event, List<Poll> polls, List<Car> cars, 
                         List<Vote> votes, List<InterestLevel> attendance) {
        this.event = event;
        this.polls = polls != null ? polls : List.of();
        this.cars = cars != null ? cars : List.of();
        this.votes = votes != null ? votes : List.of();
        this.attendance = attendance != null ? attendance : List.of();
    }
    
    public Event getEvent() {
        return event;
    }
    
    public void setEvent(Event event) {
        this.event = event;
    }
    
    public List<Poll> getPolls() {
        return polls;
    }
    
    public void setPolls(List<Poll> polls) {
        this.polls = polls;
    }
    
    public List<Car> getCars() {
        return cars;
    }
    
    public void setCars(List<Car> cars) {
        this.cars = cars;
    }
    
    public List<Vote> getVotes() {
        return votes;
    }
    
    public void setVotes(List<Vote> votes) {
        this.votes = votes;
    }
    
    public List<InterestLevel> getAttendance() {
        return attendance;
    }
    
    public void setAttendance(List<InterestLevel> attendance) {
        this.attendance = attendance;
    }
}