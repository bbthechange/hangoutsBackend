package com.bbthechange.inviter.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks per-group notification state for adaptive threshold computation.
 *
 * Stored in the group's partition:
 *   PK = GROUP#{groupId}
 *   SK = NOTIFICATION_TRACKER
 *   itemType = NOTIFICATION_TRACKER
 *
 * Weekly counters reset at the start of each ISO week (Monday UTC).
 * An 8-week rolling average is maintained to smooth out variance.
 */
@DynamoDbBean
public class GroupNotificationTracker extends BaseItem {

    private String groupId;

    /** Total notifications sent during the current week. */
    private int notificationsSentThisWeek;

    /**
     * ISO week-year string for the current week window, e.g. "2026-W10".
     * Used to detect rollover when a new week begins.
     */
    private String weekKey;

    /**
     * Rolling 8-week average of notifications sent per week.
     * Updated on each weekly rollover.
     */
    private double rollingWeeklyAverage;

    /**
     * Count of each signal type received this week.
     * Key: signal type (e.g. "BUILDING_TO_GAINING", "CONFIRMED", "CONCRETE_ACTION")
     * Value: count
     */
    private Map<String, Integer> signalCounts;

    public GroupNotificationTracker() {
        super();
        setItemType("NOTIFICATION_TRACKER");
        this.signalCounts = new HashMap<>();
        this.notificationsSentThisWeek = 0;
        this.rollingWeeklyAverage = 0.0;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public int getNotificationsSentThisWeek() {
        return notificationsSentThisWeek;
    }

    public void setNotificationsSentThisWeek(int notificationsSentThisWeek) {
        this.notificationsSentThisWeek = notificationsSentThisWeek;
    }

    public String getWeekKey() {
        return weekKey;
    }

    public void setWeekKey(String weekKey) {
        this.weekKey = weekKey;
    }

    public double getRollingWeeklyAverage() {
        return rollingWeeklyAverage;
    }

    public void setRollingWeeklyAverage(double rollingWeeklyAverage) {
        this.rollingWeeklyAverage = rollingWeeklyAverage;
    }

    public Map<String, Integer> getSignalCounts() {
        return signalCounts;
    }

    public void setSignalCounts(Map<String, Integer> signalCounts) {
        this.signalCounts = signalCounts != null ? signalCounts : new HashMap<>();
    }
}
