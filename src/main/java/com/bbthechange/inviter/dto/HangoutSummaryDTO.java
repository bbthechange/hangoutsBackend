package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.util.HangoutDataTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Hangout summary information in group feeds.
 * Represents a standalone hangout event in the feed.
 * Contains transformed, nested data from HangoutPointer for easy client consumption.
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

    // Transformed poll data (nested with vote counts and user voting status)
    private List<PollWithOptionsDTO> polls;

    // Transformed carpool data (nested with riders)
    private List<CarWithRidersDTO> cars;
    private List<NeedsRideDTO> needsRide;

    // Attribute data (kept as-is, simple structure)
    private List<HangoutAttribute> attributes;

    /**
     * Create HangoutSummaryDTO from HangoutPointer with transformed nested data.
     *
     * @param pointer The hangout pointer with denormalized data
     * @param requestingUserId User ID for calculating poll voting status
     */
    public HangoutSummaryDTO(HangoutPointer pointer, String requestingUserId) {
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

        // Transform poll data into nested structure with vote counts
        this.polls = HangoutDataTransformer.transformPollData(
                pointer.getPolls(),
                pointer.getPollOptions(),
                pointer.getVotes(),
                requestingUserId
        );

        // Transform carpool data into nested structure
        this.cars = HangoutDataTransformer.transformCarpoolData(
                pointer.getCars(),
                pointer.getCarRiders()
        );

        this.needsRide = HangoutDataTransformer.transformNeedsRideData(
                pointer.getNeedsRide()
        );

        // Attributes are already simple, keep as-is
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

    // Poll data getters/setters (now nested DTOs)

    public List<PollWithOptionsDTO> getPolls() {
        return polls != null ? polls : new ArrayList<>();
    }

    public void setPolls(List<PollWithOptionsDTO> polls) {
        this.polls = polls;
    }

    // Carpool data getters/setters (now nested DTOs)

    public List<CarWithRidersDTO> getCars() {
        return cars != null ? cars : new ArrayList<>();
    }

    public void setCars(List<CarWithRidersDTO> cars) {
        this.cars = cars;
    }

    public List<NeedsRideDTO> getNeedsRide() {
        return needsRide != null ? needsRide : new ArrayList<>();
    }

    public void setNeedsRide(List<NeedsRideDTO> needsRide) {
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