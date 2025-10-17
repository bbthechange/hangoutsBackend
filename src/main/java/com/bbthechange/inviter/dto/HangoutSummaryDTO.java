package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Hangout summary information in group feeds.
 * Represents a standalone hangout event in the feed.
 * Contains all denormalized data from HangoutPointer for single-query feed loading.
 */
// TODO @Data?
public class HangoutSummaryDTO implements FeedItem {

    private String hangoutId;
    private String title;
    private String status;
    private TimeInfo timeInfo; // Fuzzy time information for display
    private Address location;
    private int participantCount;
    private String mainImagePath; // Denormalized image path from HangoutPointer
    private String type = "hangout"; // Type discriminator for client-side handling

    // Additional basic fields
    private String description;
    private EventVisibility visibility;
    private boolean carpoolEnabled;
    private Long startTimestamp;
    private Long endTimestamp;
    private String seriesId;

    // Complete poll data (denormalized for single-query feed loading)
    private List<Poll> polls;
    private List<PollOption> pollOptions;
    private List<Vote> votes;

    // Complete carpool data (denormalized for single-query feed loading)
    private List<Car> cars;
    private List<CarRider> carRiders;
    private List<NeedsRide> needsRide;

    // Complete attribute data (denormalized for single-query feed loading)
    private List<HangoutAttribute> attributes;

    public HangoutSummaryDTO(HangoutPointer pointer) {
        // Basic fields
        this.hangoutId = pointer.getHangoutId();
        this.title = pointer.getTitle();
        this.status = pointer.getStatus();
        this.timeInfo = pointer.getTimeInput(); // Set timeInfo from pointer's timeInput
        this.location = pointer.getLocation();
        this.participantCount = pointer.getParticipantCount();
        this.mainImagePath = pointer.getMainImagePath(); // Get denormalized image path

        // Additional basic fields
        this.description = pointer.getDescription();
        this.visibility = pointer.getVisibility();
        this.carpoolEnabled = pointer.isCarpoolEnabled();
        this.startTimestamp = pointer.getStartTimestamp();
        this.endTimestamp = pointer.getEndTimestamp();
        this.seriesId = pointer.getSeriesId();

        // Complete poll data (defensive copies to prevent null issues)
        this.polls = pointer.getPolls() != null ? new ArrayList<>(pointer.getPolls()) : new ArrayList<>();
        this.pollOptions = pointer.getPollOptions() != null ? new ArrayList<>(pointer.getPollOptions()) : new ArrayList<>();
        this.votes = pointer.getVotes() != null ? new ArrayList<>(pointer.getVotes()) : new ArrayList<>();

        // Complete carpool data
        this.cars = pointer.getCars() != null ? new ArrayList<>(pointer.getCars()) : new ArrayList<>();
        this.carRiders = pointer.getCarRiders() != null ? new ArrayList<>(pointer.getCarRiders()) : new ArrayList<>();
        this.needsRide = pointer.getNeedsRide() != null ? new ArrayList<>(pointer.getNeedsRide()) : new ArrayList<>();

        // Complete attribute data
        this.attributes = pointer.getAttributes() != null ? new ArrayList<>(pointer.getAttributes()) : new ArrayList<>();
    }
    
    public String getHangoutId() {
        return hangoutId;
    }
    
    public void setHangoutId(String hangoutId) {
        this.hangoutId = hangoutId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public TimeInfo getTimeInfo() {
        return timeInfo;
    }
    
    public void setTimeInfo(TimeInfo timeInfo) {
        this.timeInfo = timeInfo;
    }
    
    public Address getLocation() {
        return location;
    }

    public void setLocation(Address location) {
        this.location = location;
    }
    
    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    // Additional basic field getters/setters

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public EventVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(EventVisibility visibility) {
        this.visibility = visibility;
    }

    public boolean isCarpoolEnabled() {
        return carpoolEnabled;
    }

    public void setCarpoolEnabled(boolean carpoolEnabled) {
        this.carpoolEnabled = carpoolEnabled;
    }

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Long getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    // Poll data getters/setters

    public List<Poll> getPolls() {
        return polls != null ? polls : new ArrayList<>();
    }

    public void setPolls(List<Poll> polls) {
        this.polls = polls;
    }

    public List<PollOption> getPollOptions() {
        return pollOptions != null ? pollOptions : new ArrayList<>();
    }

    public void setPollOptions(List<PollOption> pollOptions) {
        this.pollOptions = pollOptions;
    }

    public List<Vote> getVotes() {
        return votes != null ? votes : new ArrayList<>();
    }

    public void setVotes(List<Vote> votes) {
        this.votes = votes;
    }

    // Carpool data getters/setters

    public List<Car> getCars() {
        return cars != null ? cars : new ArrayList<>();
    }

    public void setCars(List<Car> cars) {
        this.cars = cars;
    }

    public List<CarRider> getCarRiders() {
        return carRiders != null ? carRiders : new ArrayList<>();
    }

    public void setCarRiders(List<CarRider> carRiders) {
        this.carRiders = carRiders;
    }

    public List<NeedsRide> getNeedsRide() {
        return needsRide != null ? needsRide : new ArrayList<>();
    }

    public void setNeedsRide(List<NeedsRide> needsRide) {
        this.needsRide = needsRide;
    }

    // Attribute data getters/setters

    public List<HangoutAttribute> getAttributes() {
        return attributes != null ? attributes : new ArrayList<>();
    }

    public void setAttributes(List<HangoutAttribute> attributes) {
        this.attributes = attributes;
    }
}