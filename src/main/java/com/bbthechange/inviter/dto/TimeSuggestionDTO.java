package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.TimeSuggestion;
import com.bbthechange.inviter.model.TimeSuggestionStatus;
import com.bbthechange.inviter.util.TimeInfoFormatter;

import java.util.List;

/**
 * API response DTO for a TimeSuggestion.
 * timeInput is normalized to UTC via TimeInfoFormatter so the wire shape
 * matches Hangout.timeInput exactly.
 */
public class TimeSuggestionDTO {

    private String suggestionId;
    private String hangoutId;
    private String groupId;
    private String suggestedBy;
    private TimeInfo timeInput;
    private List<String> supporterIds;
    private int supportCount;
    private TimeSuggestionStatus status;
    private long createdAtMillis;

    /**
     * Build a DTO from the lean pointer view + context supplied by the pointer record.
     * Used in feed responses where only the denormalized view is available.
     */
    public static TimeSuggestionDTO fromPointerView(TimeSuggestionPointerView view,
                                                    String hangoutId, String groupId) {
        TimeSuggestionDTO dto = new TimeSuggestionDTO();
        dto.suggestionId = view.getSuggestionId();
        dto.hangoutId = hangoutId;
        dto.groupId = groupId;
        dto.suggestedBy = view.getSuggestedBy();
        dto.timeInput = TimeInfoFormatter.forResponse(view.getTimeInput());
        dto.supporterIds = view.getSupporterIds();
        dto.supportCount = view.supportCount();
        dto.status = view.getStatus();
        dto.createdAtMillis = view.getCreatedAtMillis() != null ? view.getCreatedAtMillis() : 0L;
        return dto;
    }

    /** Build a DTO from a TimeSuggestion entity. */
    public static TimeSuggestionDTO from(TimeSuggestion ts) {
        TimeSuggestionDTO dto = new TimeSuggestionDTO();
        dto.suggestionId = ts.getSuggestionId();
        dto.hangoutId = ts.getHangoutId();
        dto.groupId = ts.getGroupId();
        dto.suggestedBy = ts.getSuggestedBy();
        dto.timeInput = TimeInfoFormatter.forResponse(ts.getTimeInput());
        dto.supporterIds = ts.getSupporterIds();
        dto.supportCount = ts.supportCount();
        dto.status = ts.getStatus();
        dto.createdAtMillis = ts.getCreatedAt() != null
                ? ts.getCreatedAt().toEpochMilli() : 0L;
        return dto;
    }

    public String getSuggestionId() { return suggestionId; }
    public String getHangoutId()    { return hangoutId; }
    public String getGroupId()      { return groupId; }
    public String getSuggestedBy()  { return suggestedBy; }
    public TimeInfo getTimeInput()  { return timeInput; }
    public List<String> getSupporterIds() { return supporterIds; }
    public int getSupportCount()    { return supportCount; }
    public TimeSuggestionStatus getStatus() { return status; }
    public long getCreatedAtMillis() { return createdAtMillis; }
}
