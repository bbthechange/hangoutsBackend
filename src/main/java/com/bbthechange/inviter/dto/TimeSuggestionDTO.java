package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.FuzzyTime;
import com.bbthechange.inviter.model.TimeSuggestion;
import com.bbthechange.inviter.model.TimeSuggestionStatus;

import java.util.List;

/**
 * API response DTO for a TimeSuggestion.
 */
public class TimeSuggestionDTO {

    private String suggestionId;
    private String hangoutId;
    private String groupId;
    private String suggestedBy;
    private FuzzyTime fuzzyTime;
    private Long specificTime;
    private List<String> supporterIds;
    private int supportCount;
    private TimeSuggestionStatus status;
    private long createdAtMillis;

    /** Build a DTO from a TimeSuggestion entity. */
    public static TimeSuggestionDTO from(TimeSuggestion ts) {
        TimeSuggestionDTO dto = new TimeSuggestionDTO();
        dto.suggestionId = ts.getSuggestionId();
        dto.hangoutId = ts.getHangoutId();
        dto.groupId = ts.getGroupId();
        dto.suggestedBy = ts.getSuggestedBy();
        dto.fuzzyTime = ts.getFuzzyTime();
        dto.specificTime = ts.getSpecificTime();
        dto.supporterIds = ts.getSupporterIds();
        dto.supportCount = ts.supportCount();
        dto.status = ts.getStatus();
        dto.createdAtMillis = ts.getCreatedAt() != null
                ? ts.getCreatedAt().toEpochMilli() : 0L;
        return dto;
    }

    // ============================================================================
    // GETTERS
    // ============================================================================

    public String getSuggestionId() { return suggestionId; }
    public String getHangoutId()    { return hangoutId; }
    public String getGroupId()      { return groupId; }
    public String getSuggestedBy()  { return suggestedBy; }
    public FuzzyTime getFuzzyTime() { return fuzzyTime; }
    public Long getSpecificTime()   { return specificTime; }
    public List<String> getSupporterIds() { return supporterIds; }
    public int getSupportCount()    { return supportCount; }
    public TimeSuggestionStatus getStatus() { return status; }
    public long getCreatedAtMillis() { return createdAtMillis; }
}
