package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hangout pointer entity for the InviterTable.
 * Represents a denormalized pointer to a hangout/event for group feed efficiency.
 * Contains key event details to avoid additional lookups when displaying group feeds.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = HANGOUT#{HangoutID}
 */
@DynamoDbBean
public class HangoutPointer extends BaseItem {
    
    // Optimistic locking - CRITICAL for preventing race conditions in concurrent updates
    private Long version;

    private String groupId;
    private String hangoutId;
    private String title;
    private String status;          // Status from the main Event record
    private Instant hangoutTime;    // When the hangout is scheduled
    private Address location;       // Denormalized location info
    private int participantCount;   // Cached count for display
    private String mainImagePath;   // Denormalized image path from Hangout
    private TimeInfo timeInfo;    // Denormalized for efficient reads
    private Long startTimestamp;    // GSI sort key for EntityTimeIndex
    private Long endTimestamp;      // Denormalized for completeness
    private String seriesId;        // Denormalized series ID for feed grouping

    // Basic hangout fields (denormalized from canonical Hangout)
    private String description;
    private EventVisibility visibility;
    private boolean carpoolEnabled;

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
    
    // Default constructor for DynamoDB
    public HangoutPointer() {
        super();
        setItemType("HANGOUT_POINTER");
        initializeCollections();
    }

    /**
     * Create a new hangout pointer record.
     */
    public HangoutPointer(String groupId, String hangoutId, String title) {
        super();
        setItemType("HANGOUT_POINTER");
        this.groupId = groupId;
        this.hangoutId = hangoutId;
        this.title = title;
        this.participantCount = 0; // Will be updated as people respond

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(groupId));
        setSk(InviterKeyFactory.getHangoutSk(hangoutId));

        initializeCollections();
    }

    /**
     * Initialize all collection fields to empty lists.
     * Prevents NullPointerExceptions when accessing denormalized data.
     */
    private void initializeCollections() {
        this.polls = new ArrayList<>();
        this.pollOptions = new ArrayList<>();
        this.votes = new ArrayList<>();
        this.cars = new ArrayList<>();
        this.carRiders = new ArrayList<>();
        this.needsRide = new ArrayList<>();
        this.attributes = new ArrayList<>();
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
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
        touch(); // Update timestamp
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        touch(); // Update timestamp
    }
    
    public Instant getHangoutTime() {
        return hangoutTime;
    }
    
    public void setHangoutTime(Instant hangoutTime) {
        this.hangoutTime = hangoutTime;
        touch(); // Update timestamp
    }
    
    public Address getLocation() {
        return location;
    }

    public void setLocation(Address location) {
        this.location = location;
        touch(); // Update timestamp
    }

    
    public int getParticipantCount() {
        return participantCount;
    }
    
    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
        touch(); // Update timestamp
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        touch(); // Update timestamp
    }

    @DynamoDbAttribute("timeInput")
    public TimeInfo getTimeInput() {
        return timeInfo;
    }
    
    public void setTimeInput(TimeInfo timeInfo) {
        this.timeInfo = timeInfo;
        touch();
    }

    @DynamoDbSecondarySortKey(indexNames = "EntityTimeIndex")
    public Long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        touch();
    }

    @Override
    @DynamoDbSecondaryPartitionKey(indexNames = {"UserGroupIndex", "EntityTimeIndex", "EndTimestampIndex"})
    public String getGsi1pk() {
        return super.getGsi1pk();
    }
    
    @DynamoDbAttribute("endTimestamp")
    @DynamoDbSecondarySortKey(indexNames = "EndTimestampIndex")
    public Long getEndTimestamp() {
        return endTimestamp;
    }
    
    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        touch();
    }
    
    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
        touch();
    }

    // ============================================================================
    // OPTIMISTIC LOCKING
    // ============================================================================

    @DynamoDbVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    // ============================================================================
    // BASIC HANGOUT FIELDS (Denormalized from canonical Hangout)
    // ============================================================================

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public EventVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(EventVisibility visibility) {
        this.visibility = visibility;
        touch();
    }

    public boolean isCarpoolEnabled() {
        return carpoolEnabled;
    }

    public void setCarpoolEnabled(boolean carpoolEnabled) {
        this.carpoolEnabled = carpoolEnabled;
        touch();
    }

    // ============================================================================
    // COMPLETE POLL DATA (Denormalized for single-query feed loading)
    // ============================================================================

    public List<Poll> getPolls() {
        return polls != null ? polls : new ArrayList<>();
    }

    public void setPolls(List<Poll> polls) {
        this.polls = polls != null ? polls : new ArrayList<>();
        touch();
    }

    public List<PollOption> getPollOptions() {
        return pollOptions != null ? pollOptions : new ArrayList<>();
    }

    public void setPollOptions(List<PollOption> pollOptions) {
        this.pollOptions = pollOptions != null ? pollOptions : new ArrayList<>();
        touch();
    }

    public List<Vote> getVotes() {
        return votes != null ? votes : new ArrayList<>();
    }

    public void setVotes(List<Vote> votes) {
        this.votes = votes != null ? votes : new ArrayList<>();
        touch();
    }

    // ============================================================================
    // COMPLETE CARPOOL DATA (Denormalized for single-query feed loading)
    // ============================================================================

    public List<Car> getCars() {
        return cars != null ? cars : new ArrayList<>();
    }

    public void setCars(List<Car> cars) {
        this.cars = cars != null ? cars : new ArrayList<>();
        touch();
    }

    public List<CarRider> getCarRiders() {
        return carRiders != null ? carRiders : new ArrayList<>();
    }

    public void setCarRiders(List<CarRider> carRiders) {
        this.carRiders = carRiders != null ? carRiders : new ArrayList<>();
        touch();
    }

    public List<NeedsRide> getNeedsRide() {
        return needsRide != null ? needsRide : new ArrayList<>();
    }

    public void setNeedsRide(List<NeedsRide> needsRide) {
        this.needsRide = needsRide != null ? needsRide : new ArrayList<>();
        touch();
    }

    // ============================================================================
    // COMPLETE ATTRIBUTE DATA (Denormalized for single-query feed loading)
    // ============================================================================

    public List<HangoutAttribute> getAttributes() {
        return attributes != null ? attributes : new ArrayList<>();
    }

    public void setAttributes(List<HangoutAttribute> attributes) {
        this.attributes = attributes != null ? attributes : new ArrayList<>();
        touch();
    }
}