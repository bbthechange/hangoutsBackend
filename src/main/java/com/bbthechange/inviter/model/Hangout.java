package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hangout entity for the InviterTable.
 * Represents the canonical record for a hangout/event.
 * 
 * Key Pattern: PK = EVENT#{HangoutID}, SK = METADATA
 */
@DynamoDbBean
public class Hangout extends BaseItem {
    
    private String hangoutId;
    private String title;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private Long version;
    private List<String> associatedGroups; // Groups this hangout is associated with
    private boolean carpoolEnabled; // Whether carpooling features are enabled
    private TimeInfo timeInfo; // Original fuzzy time input from client
    private Long startTimestamp; // Canonical UTC Unix timestamp (seconds since epoch) for start time
    private Long endTimestamp; // Canonical UTC Unix timestamp (seconds since epoch) for end time
    private String seriesId; // Link to EventSeries if this hangout is part of a multi-part event

    // Ticket-related fields
    private String ticketLink;          // URL to ticket purchase page
    private Boolean ticketsRequired;    // Are tickets mandatory?
    private String discountCode;        // Optional discount code

    // Reminder-related fields
    private String reminderScheduleName;  // EventBridge schedule name (e.g., "hangout-{id}") for updates/deletion
    private Long reminderSentAt;          // Epoch millis when reminder was sent (idempotency flag)

    // External source fields (for integration with Ticketmaster, Yelp, etc.)
    private String externalId;            // ID from external source
    private String externalSource;        // Source system name (e.g., "TICKETMASTER", "YELP")
    private Boolean isGeneratedTitle;     // Whether title was auto-generated (defaults to false)

    // Host at place field (records who is hosting when "my place" is selected)
    private String hostAtPlaceUserId;

    // Watch Party fields
    private Boolean titleNotificationSent;  // Track if title notification has been sent
    private List<String> combinedExternalIds; // External IDs of episodes combined into this hangout

    // Default constructor for DynamoDB
    public Hangout() {
        super();
        setItemType(InviterKeyFactory.HANGOUT_PREFIX);
        this.associatedGroups = new ArrayList<>();
        this.combinedExternalIds = new ArrayList<>();
        this.carpoolEnabled = false;
        this.version = 1L;
    }

    /**
     * Create a new hangout with generated UUID.
     */
    public Hangout(String title, String description, LocalDateTime startTime, LocalDateTime endTime,
                  Address location, EventVisibility visibility, String mainImagePath) {
        super();
        setItemType(InviterKeyFactory.HANGOUT_PREFIX);
        this.hangoutId = UUID.randomUUID().toString();
        this.title = title;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.visibility = visibility;
        this.mainImagePath = mainImagePath;
        this.version = 1L;
        this.associatedGroups = new ArrayList<>();
        this.combinedExternalIds = new ArrayList<>();
        this.carpoolEnabled = false;

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(this.hangoutId));
        setSk(InviterKeyFactory.getMetadataSk());
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
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
        touch(); // Update timestamp
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
        touch(); // Update timestamp
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
        touch(); // Update timestamp
    }
    
    public Address getLocation() {
        return location;
    }
    
    public void setLocation(Address location) {
        this.location = location;
        touch(); // Update timestamp
    }
    
    public EventVisibility getVisibility() {
        return visibility;
    }
    
    public void setVisibility(EventVisibility visibility) {
        this.visibility = visibility;
        touch(); // Update timestamp
    }
    
    public String getMainImagePath() {
        return mainImagePath;
    }
    
    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        touch(); // Update timestamp
    }
    
    public Long getVersion() {
        return version;
    }
    
    public void setVersion(Long version) {
        this.version = version;
    }
    
    public List<String> getAssociatedGroups() {
        return associatedGroups;
    }
    
    public void setAssociatedGroups(List<String> associatedGroups) {
        this.associatedGroups = associatedGroups != null ? associatedGroups : new ArrayList<>();
        touch(); // Update timestamp
    }
    
    public boolean isCarpoolEnabled() {
        return carpoolEnabled;
    }
    
    public void setCarpoolEnabled(boolean carpoolEnabled) {
        this.carpoolEnabled = carpoolEnabled;
        touch(); // Update timestamp
    }
    
    /**
     * Increment version for optimistic locking.
     */
    public void incrementVersion() {
        this.version = (this.version != null) ? this.version + 1 : 1L;
        touch();
    }
    
    /**
     * Add a group to the associated groups list.
     */
    public void addAssociatedGroup(String groupId) {
        if (this.associatedGroups == null) {
            this.associatedGroups = new ArrayList<>();
        }
        if (!this.associatedGroups.contains(groupId)) {
            this.associatedGroups.add(groupId);
            touch();
        }
    }
    
    /**
     * Remove a group from the associated groups list.
     */
    public void removeAssociatedGroup(String groupId) {
        if (this.associatedGroups != null) {
            this.associatedGroups.remove(groupId);
            touch();
        }
    }
    
    @DynamoDbAttribute("timeInput")
    public TimeInfo getTimeInput() {
        return timeInfo;
    }
    
    public void setTimeInput(TimeInfo timeInfo) {
        this.timeInfo = timeInfo;
        touch(); // Update timestamp
    }
    
    @DynamoDbSecondarySortKey(indexNames = "SeriesIndex")
    public Long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        touch(); // Update timestamp
    }
    
    public Long getEndTimestamp() {
        return endTimestamp;
    }
    
    public void setEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        touch(); // Update timestamp
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "SeriesIndex")
    public String getSeriesId() {
        return seriesId;
    }
    
    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
        touch(); // Update timestamp
    }

    public String getTicketLink() {
        return ticketLink;
    }

    public void setTicketLink(String ticketLink) {
        this.ticketLink = ticketLink;
        touch(); // Update timestamp
    }

    public Boolean getTicketsRequired() {
        return ticketsRequired;
    }

    public void setTicketsRequired(Boolean ticketsRequired) {
        this.ticketsRequired = ticketsRequired;
        touch(); // Update timestamp
    }

    public String getDiscountCode() {
        return discountCode;
    }

    public void setDiscountCode(String discountCode) {
        this.discountCode = discountCode;
        touch(); // Update timestamp
    }

    public String getReminderScheduleName() {
        return reminderScheduleName;
    }

    public void setReminderScheduleName(String reminderScheduleName) {
        this.reminderScheduleName = reminderScheduleName;
        touch(); // Update timestamp
    }

    public Long getReminderSentAt() {
        return reminderSentAt;
    }

    public void setReminderSentAt(Long reminderSentAt) {
        this.reminderSentAt = reminderSentAt;
        touch(); // Update timestamp
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "ExternalIdIndex")
    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch(); // Update timestamp
    }

    @DynamoDbSecondarySortKey(indexNames = "ExternalIdIndex")
    public String getExternalSource() {
        return externalSource;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
        touch(); // Update timestamp
    }

    public Boolean getIsGeneratedTitle() {
        return isGeneratedTitle;
    }

    public void setIsGeneratedTitle(Boolean isGeneratedTitle) {
        this.isGeneratedTitle = isGeneratedTitle;
        touch(); // Update timestamp
    }

    public String getHostAtPlaceUserId() {
        return hostAtPlaceUserId;
    }

    public void setHostAtPlaceUserId(String hostAtPlaceUserId) {
        this.hostAtPlaceUserId = hostAtPlaceUserId;
        touch(); // Update timestamp
    }

    // ============================================================================
    // WATCH PARTY FIELDS
    // ============================================================================

    public Boolean getTitleNotificationSent() {
        return titleNotificationSent;
    }

    public void setTitleNotificationSent(Boolean titleNotificationSent) {
        this.titleNotificationSent = titleNotificationSent;
        touch();
    }

    public List<String> getCombinedExternalIds() {
        return combinedExternalIds;
    }

    public void setCombinedExternalIds(List<String> combinedExternalIds) {
        this.combinedExternalIds = combinedExternalIds != null ? combinedExternalIds : new ArrayList<>();
        touch();
    }

    // ============================================================================
    // WATCH PARTY HELPER METHODS
    // ============================================================================

    /**
     * Check if this hangout has combined episodes (multi-episode watch party).
     *
     * @return true if there are combined external IDs
     */
    public boolean hasCombinedEpisodes() {
        return this.combinedExternalIds != null && !this.combinedExternalIds.isEmpty();
    }

    /**
     * Add an external ID to the combined episodes list.
     *
     * @param externalId The external ID to add
     */
    public void addCombinedExternalId(String externalId) {
        if (this.combinedExternalIds == null) {
            this.combinedExternalIds = new ArrayList<>();
        }
        if (!this.combinedExternalIds.contains(externalId)) {
            this.combinedExternalIds.add(externalId);
            touch();
        }
    }

    /**
     * Remove an external ID from the combined episodes list.
     *
     * @param externalId The external ID to remove
     */
    public void removeCombinedExternalId(String externalId) {
        if (this.combinedExternalIds != null && this.combinedExternalIds.remove(externalId)) {
            touch();
        }
    }

    /**
     * Get the count of combined episodes.
     *
     * @return The number of combined external IDs
     */
    public int getCombinedEpisodesCount() {
        return this.combinedExternalIds != null ? this.combinedExternalIds.size() : 0;
    }
}