package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.TimeSuggestion;
import com.bbthechange.inviter.model.TimeSuggestionStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Lean projection of {@link TimeSuggestion} denormalized onto {@code HangoutPointer}
 * for the group feed. Carries only the fields the feed needs to render — no DynamoDB
 * key data from {@code BaseItem}, since these are nested inside the pointer and have
 * no identity of their own.
 */
@DynamoDbBean
public class TimeSuggestionPointerView {

    private String suggestionId;
    private String suggestedBy;
    private TimeInfo timeInput;
    private List<String> supporterIds;
    private TimeSuggestionStatus status;
    private Long createdAtMillis;

    public TimeSuggestionPointerView() {
        this.supporterIds = new ArrayList<>();
    }

    public static TimeSuggestionPointerView from(TimeSuggestion ts) {
        TimeSuggestionPointerView view = new TimeSuggestionPointerView();
        view.suggestionId = ts.getSuggestionId();
        view.suggestedBy = ts.getSuggestedBy();
        view.timeInput = ts.getTimeInput();
        view.supporterIds = ts.getSupporterIds() != null
                ? new ArrayList<>(ts.getSupporterIds())
                : new ArrayList<>();
        view.status = ts.getStatus();
        view.createdAtMillis = ts.getCreatedAt() != null ? ts.getCreatedAt().toEpochMilli() : null;
        return view;
    }

    @DynamoDbAttribute("suggestionId")
    public String getSuggestionId() { return suggestionId; }
    public void setSuggestionId(String suggestionId) { this.suggestionId = suggestionId; }

    @DynamoDbAttribute("suggestedBy")
    public String getSuggestedBy() { return suggestedBy; }
    public void setSuggestedBy(String suggestedBy) { this.suggestedBy = suggestedBy; }

    @DynamoDbAttribute("timeInput")
    public TimeInfo getTimeInput() { return timeInput; }
    public void setTimeInput(TimeInfo timeInput) { this.timeInput = timeInput; }

    @DynamoDbAttribute("supporterIds")
    public List<String> getSupporterIds() {
        return supporterIds != null ? supporterIds : new ArrayList<>();
    }
    public void setSupporterIds(List<String> supporterIds) {
        this.supporterIds = supporterIds != null ? supporterIds : new ArrayList<>();
    }

    @DynamoDbAttribute("status")
    public TimeSuggestionStatus getStatus() { return status; }
    public void setStatus(TimeSuggestionStatus status) { this.status = status; }

    @DynamoDbAttribute("createdAtMillis")
    public Long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(Long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public int supportCount() {
        return supporterIds == null ? 0 : supporterIds.size();
    }
}
