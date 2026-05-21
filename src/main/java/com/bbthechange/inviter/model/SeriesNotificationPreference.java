package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-user, per-series notification preferences.
 *
 * Key Pattern: PK = USER#{userId}, SK = SERIESPREF#{seriesId}
 *
 * Sparse — the row exists only when at least one nudge type is muted for this
 * series/user combination. Absence of the row OR absence of a key in the map
 * both mean "not muted." See contract §2 / §8 for the rationale behind using a
 * map instead of per-type boolean columns.
 */
@DynamoDbBean
public class SeriesNotificationPreference extends BaseItem {

    private String userId;
    private String seriesId;
    private Map<String, Boolean> mutedNudgeTypes;

    public SeriesNotificationPreference() {
        super();
        setItemType("SERIES_NOTIFICATION_PREFERENCE");
        this.mutedNudgeTypes = new HashMap<>();
    }

    public SeriesNotificationPreference(String userId, String seriesId) {
        this();
        this.userId = userId;
        this.seriesId = seriesId;
        setPk(InviterKeyFactory.getUserPk(userId));
        setSk(InviterKeyFactory.getSeriesPrefSk(seriesId));
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(String seriesId) {
        this.seriesId = seriesId;
    }

    public Map<String, Boolean> getMutedNudgeTypes() {
        return mutedNudgeTypes;
    }

    public void setMutedNudgeTypes(Map<String, Boolean> mutedNudgeTypes) {
        this.mutedNudgeTypes = mutedNudgeTypes != null ? mutedNudgeTypes : new HashMap<>();
    }
}
