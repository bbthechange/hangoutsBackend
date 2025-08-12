package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Model representing a user's request for a ride to an event.
 * Uses the single-table design pattern with EVENT#{eventId} as PK
 * and NEEDS_RIDE#{userId} as SK.
 */
@DynamoDbBean
public class NeedsRide extends BaseItem {
    private String eventId;
    private String userId;
    private String notes;

    public NeedsRide() {
        super();
        setItemType("NEEDS_RIDE");
    }

    public NeedsRide(String eventId, String userId, String notes) {
        this();
        this.eventId = eventId;
        this.userId = userId;
        this.notes = notes;
        setPk(InviterKeyFactory.getEventPk(eventId));
        setSk(InviterKeyFactory.getNeedsRideSk(userId));
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}