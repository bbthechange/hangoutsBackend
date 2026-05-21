package com.bbthechange.inviter.service.impl;

import org.springframework.stereotype.Service;

/**
 * Handles WATCH_PARTY_HOST_NUDGE SQS messages: gate checks, idempotency claim,
 * recipient assembly, and notification dispatch.
 *
 * Phase 1 stub: body is filled in by phase 2.
 */
@Service
public class WatchPartyHostNudgeService {

    /**
     * Invoked by ScheduledEventListener for WATCH_PARTY_HOST_NUDGE messages.
     */
    public void processHostNudge(String hangoutId) {
        throw new UnsupportedOperationException("Phase 2");
    }
}
