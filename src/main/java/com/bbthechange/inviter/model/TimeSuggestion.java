package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TimeSuggestion entity for the InviterTable.
 * Represents a suggested time for a hangout that has no time set yet.
 *
 * Key Pattern:
 *   PK = EVENT#{hangoutId}
 *   SK = TIME_SUGGESTION#{suggestionId}
 *
 * The suggestion lives in the hangout's item collection so it is retrieved
 * as part of the existing getHangoutDetailData() item-collection query.
 */
@DynamoDbBean
public class TimeSuggestion extends BaseItem {

    public static final String TIME_SUGGESTION_PREFIX = "TIME_SUGGESTION";

    private String suggestionId;
    private String hangoutId;
    private String groupId;
    private String suggestedBy;           // User ID of the person who created the suggestion
    private FuzzyTime fuzzyTime;          // e.g. TONIGHT, THIS_WEEKEND, SATURDAY
    private Long specificTime;            // Optional Unix epoch seconds (exact time)
    private List<String> supporterIds;    // User IDs who +1'd this suggestion
    private TimeSuggestionStatus status;  // ACTIVE, ADOPTED, REJECTED

    /** Default constructor required by DynamoDB Enhanced Client. */
    public TimeSuggestion() {
        super();
        setItemType(TIME_SUGGESTION_PREFIX);
        this.supporterIds = new ArrayList<>();
        this.status = TimeSuggestionStatus.ACTIVE;
    }

    /**
     * Create a new time suggestion with a generated UUID.
     *
     * @param hangoutId  The hangout this suggestion belongs to
     * @param groupId    The group context (for authorization / momentum recompute)
     * @param suggestedBy User ID of the suggester
     * @param fuzzyTime  Fuzzy time value (e.g. TONIGHT)
     * @param specificTime Optional exact Unix timestamp in seconds (null for fuzzy-only)
     */
    public TimeSuggestion(String hangoutId, String groupId, String suggestedBy,
                          FuzzyTime fuzzyTime, Long specificTime) {
        super();
        setItemType(TIME_SUGGESTION_PREFIX);
        this.suggestionId = UUID.randomUUID().toString();
        this.hangoutId = hangoutId;
        this.groupId = groupId;
        this.suggestedBy = suggestedBy;
        this.fuzzyTime = fuzzyTime;
        this.specificTime = specificTime;
        this.supporterIds = new ArrayList<>();
        this.status = TimeSuggestionStatus.ACTIVE;

        setPk(InviterKeyFactory.getEventPk(hangoutId));
        setSk(getTimeSuggestionSk(this.suggestionId));
    }

    // ============================================================================
    // KEY HELPER
    // ============================================================================

    public static String getTimeSuggestionSk(String suggestionId) {
        return TIME_SUGGESTION_PREFIX + "#" + suggestionId;
    }

    public static boolean isTimeSuggestion(String sortKey) {
        return sortKey != null && sortKey.startsWith(TIME_SUGGESTION_PREFIX + "#");
    }

    // ============================================================================
    // ACCESSORS
    // ============================================================================

    @DynamoDbAttribute("suggestionId")
    public String getSuggestionId() {
        return suggestionId;
    }

    public void setSuggestionId(String suggestionId) {
        this.suggestionId = suggestionId;
    }

    @DynamoDbAttribute("hangoutId")
    public String getHangoutId() {
        return hangoutId;
    }

    public void setHangoutId(String hangoutId) {
        this.hangoutId = hangoutId;
    }

    @DynamoDbAttribute("groupId")
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @DynamoDbAttribute("suggestedBy")
    public String getSuggestedBy() {
        return suggestedBy;
    }

    public void setSuggestedBy(String suggestedBy) {
        this.suggestedBy = suggestedBy;
    }

    @DynamoDbAttribute("fuzzyTime")
    public FuzzyTime getFuzzyTime() {
        return fuzzyTime;
    }

    public void setFuzzyTime(FuzzyTime fuzzyTime) {
        this.fuzzyTime = fuzzyTime;
    }

    @DynamoDbAttribute("specificTime")
    public Long getSpecificTime() {
        return specificTime;
    }

    public void setSpecificTime(Long specificTime) {
        this.specificTime = specificTime;
    }

    @DynamoDbAttribute("supporterIds")
    public List<String> getSupporterIds() {
        return supporterIds;
    }

    public void setSupporterIds(List<String> supporterIds) {
        this.supporterIds = supporterIds != null ? supporterIds : new ArrayList<>();
    }

    @DynamoDbAttribute("status")
    public TimeSuggestionStatus getStatus() {
        return status;
    }

    public void setStatus(TimeSuggestionStatus status) {
        this.status = status;
        touch();
    }

    // ============================================================================
    // BUSINESS HELPERS
    // ============================================================================

    /** Add a supporter. Returns true if the user was not already a supporter. */
    public boolean addSupporter(String userId) {
        if (supporterIds == null) {
            supporterIds = new ArrayList<>();
        }
        if (!supporterIds.contains(userId)) {
            supporterIds.add(userId);
            touch();
            return true;
        }
        return false;
    }

    /** Number of supporters (not counting the suggester). */
    public int supportCount() {
        return supporterIds == null ? 0 : supporterIds.size();
    }
}
