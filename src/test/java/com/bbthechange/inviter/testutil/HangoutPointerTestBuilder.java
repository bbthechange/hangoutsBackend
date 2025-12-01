package com.bbthechange.inviter.testutil;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.ParticipationSummaryDTO;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.util.InviterKeyFactory;

import java.time.Instant;
import java.util.List;

/**
 * Test builder for HangoutPointer.
 *
 * <p>Usage:
 * <pre>
 * HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
 *     .forGroup(groupId)
 *     .forHangout(hangoutId)
 *     .withTitle("My Event")
 *     .withPolls(List.of(poll))
 *     .build();
 * </pre>
 *
 * All fields have sensible defaults; only specify what you need.
 */
public class HangoutPointerTestBuilder {

    // Core fields
    private String groupId;
    private String hangoutId;
    private String title;
    private String status;
    private Instant hangoutTime;
    private Address location;
    private int participantCount;
    private TimeInfo timeInput;
    private Long startTimestamp;
    private Long endTimestamp;
    private String seriesId;

    // Basic hangout fields
    private String description;
    private EventVisibility visibility;
    private boolean carpoolEnabled;
    private String mainImagePath;

    // Denormalized collections
    private List<Poll> polls = List.of();
    private List<PollOption> pollOptions = List.of();
    private List<Vote> votes = List.of();
    private List<Car> cars = List.of();
    private List<CarRider> carRiders = List.of();
    private List<NeedsRide> needsRide = List.of();
    private List<HangoutAttribute> attributes = List.of();
    private List<InterestLevel> interestLevels = List.of();

    // Participation/ticket fields
    private ParticipationSummaryDTO participationSummary;
    private String ticketLink;
    private Boolean ticketsRequired;
    private String discountCode;

    private HangoutPointerTestBuilder() {
        // Default values
        this.groupId = "group-1";
        this.hangoutId = "hangout-1";
        this.title = "Test Hangout";
        this.status = "ACTIVE";
        this.hangoutTime = Instant.now();
        this.location = new Address();
        this.location.setName("Test Location");
        this.participantCount = 1;
        this.timeInput = new TimeInfo();
    }
    
    public static HangoutPointerTestBuilder aPointer() {
        return new HangoutPointerTestBuilder();
    }
    
    public HangoutPointerTestBuilder forGroup(String groupId) {
        this.groupId = groupId;
        return this;
    }
    
    public HangoutPointerTestBuilder forHangout(String hangoutId) {
        this.hangoutId = hangoutId;
        return this;
    }
    
    public HangoutPointerTestBuilder withTitle(String title) {
        this.title = title;
        return this;
    }
    
    public HangoutPointerTestBuilder withStatus(String status) {
        this.status = status;
        return this;
    }
    
    public HangoutPointerTestBuilder withHangoutTime(Instant hangoutTime) {
        this.hangoutTime = hangoutTime;
        return this;
    }
    
    public HangoutPointerTestBuilder withLocationName(String locationName) {
        this.location = new Address();
        this.location.setName(locationName);
        return this;
    }
    
    public HangoutPointerTestBuilder withParticipantCount(int participantCount) {
        this.participantCount = participantCount;
        return this;
    }
    
    public HangoutPointerTestBuilder withTimeInput(TimeInfo timeInput) {
        this.timeInput = timeInput;
        return this;
    }
    
    public HangoutPointerTestBuilder withStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        return this;
    }
    
    public HangoutPointerTestBuilder withEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        return this;
    }
    
    public HangoutPointerTestBuilder withSeriesId(String seriesId) {
        this.seriesId = seriesId;
        return this;
    }

    // Basic hangout field methods

    public HangoutPointerTestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public HangoutPointerTestBuilder withVisibility(EventVisibility visibility) {
        this.visibility = visibility;
        return this;
    }

    public HangoutPointerTestBuilder withCarpoolEnabled(boolean carpoolEnabled) {
        this.carpoolEnabled = carpoolEnabled;
        return this;
    }

    public HangoutPointerTestBuilder withMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        return this;
    }

    // Denormalized collection methods

    public HangoutPointerTestBuilder withPolls(List<Poll> polls) {
        this.polls = polls;
        return this;
    }

    public HangoutPointerTestBuilder withPollOptions(List<PollOption> pollOptions) {
        this.pollOptions = pollOptions;
        return this;
    }

    public HangoutPointerTestBuilder withVotes(List<Vote> votes) {
        this.votes = votes;
        return this;
    }

    public HangoutPointerTestBuilder withCars(List<Car> cars) {
        this.cars = cars;
        return this;
    }

    public HangoutPointerTestBuilder withCarRiders(List<CarRider> carRiders) {
        this.carRiders = carRiders;
        return this;
    }

    public HangoutPointerTestBuilder withNeedsRide(List<NeedsRide> needsRide) {
        this.needsRide = needsRide;
        return this;
    }

    public HangoutPointerTestBuilder withAttributes(List<HangoutAttribute> attributes) {
        this.attributes = attributes;
        return this;
    }

    public HangoutPointerTestBuilder withInterestLevels(List<InterestLevel> interestLevels) {
        this.interestLevels = interestLevels;
        return this;
    }

    // Participation/ticket field methods

    public HangoutPointerTestBuilder withParticipationSummary(ParticipationSummaryDTO participationSummary) {
        this.participationSummary = participationSummary;
        return this;
    }

    public HangoutPointerTestBuilder withTicketLink(String ticketLink) {
        this.ticketLink = ticketLink;
        return this;
    }

    public HangoutPointerTestBuilder withTicketsRequired(Boolean ticketsRequired) {
        this.ticketsRequired = ticketsRequired;
        return this;
    }

    public HangoutPointerTestBuilder withDiscountCode(String discountCode) {
        this.discountCode = discountCode;
        return this;
    }

    public HangoutPointer build() {
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, title);

        // Core fields
        pointer.setStatus(status);
        pointer.setHangoutTime(hangoutTime);
        pointer.setLocation(location);
        pointer.setParticipantCount(participantCount);
        pointer.setTimeInput(timeInput);
        pointer.setStartTimestamp(startTimestamp);
        pointer.setEndTimestamp(endTimestamp);
        pointer.setSeriesId(seriesId);

        // Basic hangout fields
        pointer.setDescription(description);
        pointer.setVisibility(visibility);
        pointer.setCarpoolEnabled(carpoolEnabled);
        pointer.setMainImagePath(mainImagePath);

        // Denormalized collections
        pointer.setPolls(polls);
        pointer.setPollOptions(pollOptions);
        pointer.setVotes(votes);
        pointer.setCars(cars);
        pointer.setCarRiders(carRiders);
        pointer.setNeedsRide(needsRide);
        pointer.setAttributes(attributes);
        pointer.setInterestLevels(interestLevels);

        // Participation/ticket fields
        pointer.setParticipationSummary(participationSummary);
        pointer.setTicketLink(ticketLink);
        pointer.setTicketsRequired(ticketsRequired);
        pointer.setDiscountCode(discountCode);

        // Set DynamoDB keys
        pointer.setPk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setSk(InviterKeyFactory.getHangoutSk(hangoutId));
        pointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));

        return pointer;
    }
}