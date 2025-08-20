package com.bbthechange.inviter.dto;

import java.util.Map;

/**
 * Polymorphic feed item for the group feed API.
 * Contains common fields and polymorphic data based on item type.
 */
public class FeedItemDTO {
    
    private String itemType;
    private FeedItemEventInfo eventInfo;
    private Map<String, Object> data;
    
    public FeedItemDTO() {}
    
    public FeedItemDTO(String itemType, FeedItemEventInfo eventInfo, Map<String, Object> data) {
        this.itemType = itemType;
        this.eventInfo = eventInfo;
        this.data = data;
    }
    
    public String getItemType() {
        return itemType;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    
    public FeedItemEventInfo getEventInfo() {
        return eventInfo;
    }
    
    public void setEventInfo(FeedItemEventInfo eventInfo) {
        this.eventInfo = eventInfo;
    }
    
    public Map<String, Object> getData() {
        return data;
    }
    
    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}