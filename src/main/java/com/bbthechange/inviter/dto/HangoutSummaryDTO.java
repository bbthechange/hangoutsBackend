package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.util.HangoutDataTransformer;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data Transfer Object for Hangout summary information in group feeds.
 * Represents a standalone hangout event in the feed.
 * Contains transformed, nested data from HangoutPointer for easy client consumption.
 *
 * <p>Primary usage is via the transformation constructor:
 * <pre>
 * HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);
 * </pre>
 *
 * <p>A builder is also available for direct construction:
 * <pre>
 * HangoutSummaryDTO.builder()
 *     .withHangoutId(id)
 *     .withTitle(title)
 *     .build();
 * </pre>
 * All list fields default to empty lists if not specified.
 */
@Builder(setterPrefix = "with", toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HangoutSummaryDTO implements FeedItem {

    private String hangoutId;
    private String title;
    private String status;
    private TimeInfo timeInfo; // Fuzzy time information for display
    private Address location;
    private int participantCount;
    private String mainImagePath; // Denormalized image path from HangoutPointer
    @Builder.Default
    private String type = "hangout"; // Type discriminator for client-side handling

    // Additional basic fields
    private String description;
    private EventVisibility visibility;
    private boolean carpoolEnabled;
    private Long startTimestamp;
    private Long endTimestamp;
    private String seriesId;

    // Transformed poll data (nested with vote counts and user voting status)
    @Builder.Default
    private List<PollWithOptionsDTO> polls = List.of();

    // Transformed carpool data (nested with riders)
    @Builder.Default
    private List<CarWithRidersDTO> cars = List.of();
    @Builder.Default
    private List<NeedsRideDTO> needsRide = List.of();

    // Attribute data
    @Builder.Default
    private List<HangoutAttributeDTO> attributes = List.of();

    // Interest level / attendance data
    @Builder.Default
    private List<InterestLevel> interestLevels = List.of();

    // Ticket/participation coordination fields
    private ParticipationSummaryDTO participationSummary;
    private String ticketLink;
    private Boolean ticketsRequired;
    private String discountCode;

    // External source fields (denormalized from HangoutPointer)
    private String externalId;
    private String externalSource;
    @Builder.Default
    private Boolean isGeneratedTitle = false;

    /**
     * Create HangoutSummaryDTO from HangoutPointer with transformed nested data.
     *
     * @param pointer The hangout pointer with denormalized data
     * @param requestingUserId User ID for calculating poll voting status
     */
    public HangoutSummaryDTO(HangoutPointer pointer, String requestingUserId) {
        // Type discriminator (must be set explicitly since @Builder.Default only applies to builder)
        this.type = "hangout";

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

        // Attributes
        List<HangoutAttribute> hangoutAttributeDTOS = pointer.getAttributes();
        if (hangoutAttributeDTOS != null) {
            this.attributes = hangoutAttributeDTOS.stream()
                    .map(HangoutAttributeDTO::fromEntity)
                    .collect(Collectors.toList());
        }

        // Interest levels
        this.interestLevels = pointer.getInterestLevels();

        // Ticket/participation coordination fields (denormalized from pointer)
        this.participationSummary = pointer.getParticipationSummary();
        this.ticketLink = pointer.getTicketLink();
        this.ticketsRequired = pointer.getTicketsRequired();
        this.discountCode = pointer.getDiscountCode();

        // External source fields (denormalized from pointer)
        this.externalId = pointer.getExternalId();
        this.externalSource = pointer.getExternalSource();
        this.isGeneratedTitle = pointer.getIsGeneratedTitle() != null ? pointer.getIsGeneratedTitle() : false;
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

    public List<HangoutAttributeDTO> getAttributes() {
        return attributes != null ? attributes : new ArrayList<>();
    }

    public void setAttributes(List<HangoutAttributeDTO> attributes) {
        this.attributes = attributes;
    }

    // Interest level / attendance data getters/setters

    public List<InterestLevel> getInterestLevels() {
        return interestLevels != null ? interestLevels : new ArrayList<>();
    }

    public void setInterestLevels(List<InterestLevel> interestLevels) {
        this.interestLevels = interestLevels;
    }

    // Ticket/participation coordination getters/setters

    public ParticipationSummaryDTO getParticipationSummary() {
        return participationSummary;
    }

    public void setParticipationSummary(ParticipationSummaryDTO participationSummary) {
        this.participationSummary = participationSummary;
    }

    public String getTicketLink() {
        return ticketLink;
    }

    public void setTicketLink(String ticketLink) {
        this.ticketLink = ticketLink;
    }

    public Boolean getTicketsRequired() {
        return ticketsRequired;
    }

    public void setTicketsRequired(Boolean ticketsRequired) {
        this.ticketsRequired = ticketsRequired;
    }

    public String getDiscountCode() {
        return discountCode;
    }

    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
    }

    // External source field getters/setters

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
    }

    public Boolean getIsGeneratedTitle() {
        return isGeneratedTitle;
    }

    public void setIsGeneratedTitle(Boolean isGeneratedTitle) {
        this.isGeneratedTitle = isGeneratedTitle;
    }
}