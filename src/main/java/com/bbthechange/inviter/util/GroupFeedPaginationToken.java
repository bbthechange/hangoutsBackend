package com.bbthechange.inviter.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Base64;

/**
 * Utility class for encoding and decoding pagination tokens for the group feed API.
 * The token contains the last processed event ID, timestamp, and direction for cursor-based pagination.
 */
public class GroupFeedPaginationToken {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private String lastEventId;
    private Long lastTimestamp;
    private boolean isForward; // true for forward pagination, false for backward
    
    public GroupFeedPaginationToken() {}
    
    public GroupFeedPaginationToken(String lastEventId, Long lastTimestamp, boolean isForward) {
        this.lastEventId = lastEventId;
        this.lastTimestamp = lastTimestamp;
        this.isForward = isForward;
    }
    
    /**
     * Encode the pagination token to a base64 string.
     */
    public String encode() {
        try {
            String json = objectMapper.writeValueAsString(this);
            return Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode pagination token", e);
        }
    }
    
    /**
     * Decode a base64 pagination token string.
     */
    public static GroupFeedPaginationToken decode(String token) {
        try {
            byte[] decoded = Base64.getDecoder().decode(token);
            String json = new String(decoded);
            return objectMapper.readValue(json, GroupFeedPaginationToken.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode pagination token", e);
        }
    }
    
    public String getLastEventId() {
        return lastEventId;
    }
    
    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }
    
    public Long getLastTimestamp() {
        return lastTimestamp;
    }
    
    public void setLastTimestamp(Long lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }
    
    public boolean isForward() {
        return isForward;
    }
    
    public void setForward(boolean forward) {
        isForward = forward;
    }
}