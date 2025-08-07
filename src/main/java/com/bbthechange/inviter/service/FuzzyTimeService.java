package com.bbthechange.inviter.service;

import java.util.Map;

/**
 * Service for converting fuzzy time inputs to canonical timestamps.
 * Handles both exact time inputs (ISO 8601 with timezone) and fuzzy time inputs 
 * (periodGranularity + periodStart) according to the Fuzzy Time API specification.
 */
public interface FuzzyTimeService {
    
    /**
     * Convert timeInput map to canonical timestamps.
     * 
     * @param timeInput Map containing either:
     *                  - Exact time: "startTime" and "endTime" (ISO 8601 strings with timezone)
     *                  - Fuzzy time: "periodGranularity" and "periodStart" (ISO 8601 with timezone)
     * @return FuzzyTimeResult with startTimestamp and endTimestamp (Unix seconds since epoch)
     * @throws IllegalArgumentException if timeInput is invalid or incomplete
     */
    FuzzyTimeResult convertTimeInput(Map<String, String> timeInput);
    
    /**
     * Result object containing canonical timestamps.
     */
    class FuzzyTimeResult {
        private final Long startTimestamp;
        private final Long endTimestamp;
        
        public FuzzyTimeResult(Long startTimestamp, Long endTimestamp) {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
        }
        
        public Long getStartTimestamp() {
            return startTimestamp;
        }
        
        public Long getEndTimestamp() {
            return endTimestamp;
        }
    }
}