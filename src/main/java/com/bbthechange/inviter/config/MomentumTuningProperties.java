package com.bbthechange.inviter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tunable parameters controlling momentum-driven feed behavior.
 *
 * <p>Centralizes all feed sorting and surfacing thresholds so operators can tune them
 * via configuration without a code change. Bind via {@code momentum.tuning.*} in
 * application.properties (or environment variables).
 *
 * <p>Scoring weights and dynamic-threshold parameters still live in
 * {@link com.bbthechange.inviter.service.impl.MomentumServiceImpl} — they are flagged
 * as deferred migration in MOMENTUM_CONTEXT.md section 18.
 */
@Configuration
@ConfigurationProperties(prefix = "momentum.tuning")
public class MomentumTuningProperties {

    /**
     * Age in days below which a BUILDING ("floated") hangout is considered "fresh".
     * Fresh floats bypass busy-week suppression and always surface.
     */
    private int freshFloatAgeDays = 5;

    /**
     * Number of upcoming ISO weeks we want covered with at least one suggestion.
     * Drives the forward-fill budget that surfaces stale floats and ideas.
     */
    private int forwardWeeksToFill = 8;

    /**
     * Minimum {@code interestCount} for an idea to be considered "supported" in the
     * forward-fill priority ladder.
     */
    private int ideaMinInterestCount = 3;

    /** Window (hours) used by {@code hasRecentSupportSurge} to detect a surge. */
    private int recentSupportWindowHours = 24;

    /** Minimum number of interest signals within the recent-support window to count as a surge. */
    private int recentSupportMinSignals = 2;

    /** Upper bound (hours) of the IMMINENT time horizon. */
    private int imminentHorizonHours = 48;

    /** Upper bound (days) of the NEAR_TERM time horizon. */
    private int nearTermHorizonDays = 7;

    /** Upper bound (days) of the MID_TERM time horizon. Beyond this is DISTANT. */
    private int midTermHorizonDays = 21;

    /**
     * Granularity (seconds) of the time bucket that contributes to the feed ETag.
     * Ensures ETag rolls at least once per bucket even without data writes, so
     * time-driven feed mutations (fresh→stale transitions, sliding empty weeks)
     * become visible to clients within this window.
     */
    private long etagTimeBucketSeconds = 86400L;

    public int getFreshFloatAgeDays() { return freshFloatAgeDays; }
    public void setFreshFloatAgeDays(int freshFloatAgeDays) { this.freshFloatAgeDays = freshFloatAgeDays; }

    public int getForwardWeeksToFill() { return forwardWeeksToFill; }
    public void setForwardWeeksToFill(int forwardWeeksToFill) { this.forwardWeeksToFill = forwardWeeksToFill; }

    public int getIdeaMinInterestCount() { return ideaMinInterestCount; }
    public void setIdeaMinInterestCount(int ideaMinInterestCount) { this.ideaMinInterestCount = ideaMinInterestCount; }

    public int getRecentSupportWindowHours() { return recentSupportWindowHours; }
    public void setRecentSupportWindowHours(int recentSupportWindowHours) { this.recentSupportWindowHours = recentSupportWindowHours; }

    public int getRecentSupportMinSignals() { return recentSupportMinSignals; }
    public void setRecentSupportMinSignals(int recentSupportMinSignals) { this.recentSupportMinSignals = recentSupportMinSignals; }

    public int getImminentHorizonHours() { return imminentHorizonHours; }
    public void setImminentHorizonHours(int imminentHorizonHours) { this.imminentHorizonHours = imminentHorizonHours; }

    public int getNearTermHorizonDays() { return nearTermHorizonDays; }
    public void setNearTermHorizonDays(int nearTermHorizonDays) { this.nearTermHorizonDays = nearTermHorizonDays; }

    public int getMidTermHorizonDays() { return midTermHorizonDays; }
    public void setMidTermHorizonDays(int midTermHorizonDays) { this.midTermHorizonDays = midTermHorizonDays; }

    public long getEtagTimeBucketSeconds() { return etagTimeBucketSeconds; }
    public void setEtagTimeBucketSeconds(long etagTimeBucketSeconds) { this.etagTimeBucketSeconds = etagTimeBucketSeconds; }

    public long getImminentHorizonSeconds() { return imminentHorizonHours * 3600L; }
    public long getNearTermHorizonSeconds() { return nearTermHorizonDays * 86400L; }
    public long getMidTermHorizonSeconds() { return midTermHorizonDays * 86400L; }
    public long getRecentSupportWindowSeconds() { return recentSupportWindowHours * 3600L; }
    public long getFreshFloatAgeSeconds() { return freshFloatAgeDays * 86400L; }
}
