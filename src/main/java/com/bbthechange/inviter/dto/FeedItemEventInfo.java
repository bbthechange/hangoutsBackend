package com.bbthechange.inviter.dto;

/**
 * Event information included in feed items.
 * Provides context about which event the feed item belongs to.
 */
public class FeedItemEventInfo {
    
    private String eventId;
    private String eventTitle;
    
    public FeedItemEventInfo() {}
    
    public FeedItemEventInfo(String eventId, String eventTitle) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public String getEventTitle() {
        return eventTitle;
    }
    
    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }
}