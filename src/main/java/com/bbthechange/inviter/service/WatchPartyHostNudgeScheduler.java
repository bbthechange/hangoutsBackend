package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import org.springframework.stereotype.Service;

/**
 * Schedules / cancels EventBridge schedules for the watch-party host nudge.
 *
 * Phase 1 stub: bodies are filled in by phase 2.
 */
@Service
public class WatchPartyHostNudgeScheduler {

    /**
     * Idempotently create-or-update the EventBridge schedule.
     * No-op (with counter) when: scheduler disabled, series is virtual,
     * hangout already has a host, hangout already nudged, or fire time has passed.
     */
    public void scheduleHostNudge(Hangout hangout, EventSeries series) {
        throw new UnsupportedOperationException("Phase 2");
    }

    /**
     * Delete the EventBridge schedule. Safe to call when no schedule exists.
     */
    public void cancelHostNudge(Hangout hangout) {
        throw new UnsupportedOperationException("Phase 2");
    }
}
