package com.bbthechange.inviter.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Utility class for encoding and decoding pagination tokens for the group feed API.
 * The token contains the last processed event ID and timestamp for cursor-based pagination.
 */
public class GroupFeedPaginationToken {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private String lastEventId;
    private LocalDateTime lastTimestamp;
    
    public GroupFeedPaginationToken() {}
    
    public GroupFeedPaginationToken(String lastEventId, LocalDateTime lastTimestamp) {
        this.lastEventId = lastEventId;
        this.lastTimestamp = lastTimestamp;
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
    
    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }
    
    public void setLastTimestamp(LocalDateTime lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }
}