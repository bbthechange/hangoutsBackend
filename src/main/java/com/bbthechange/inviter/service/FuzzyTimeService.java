package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.TimeInfo;

/**
 * Service for converting fuzzy time inputs to canonical timestamps.
 * Handles both exact time inputs (ISO 8601 with timezone) and fuzzy time inputs 
 * (periodGranularity + periodStart) according to the Fuzzy Time API specification.
 */
public interface FuzzyTimeService {
    
    /**
     * Convert TimeInput to canonical timestamps.
     * 
     * @param timeInfo TimeInput containing either:
     *                  - Exact time: startTime and endTime (ISO 8601 strings with timezone)
     *                  - Fuzzy time: periodGranularity and periodStart (ISO 8601 with timezone)
     * @return TimeConversionResult with startTimestamp and endTimestamp (Unix seconds since epoch)
     * @throws IllegalArgumentException if timeInput is invalid or incomplete
     */
    TimeConversionResult convert(TimeInfo timeInfo);
    
    /**
     * Result object containing canonical timestamps.
     */
    class TimeConversionResult {
        public final Long startTimestamp;
        public final Long endTimestamp;
        
        public TimeConversionResult(Long startTimestamp, Long endTimestamp) {
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
        }
    }
}