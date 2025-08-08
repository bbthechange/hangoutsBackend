package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.dto.TimeInput;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Implementation of FuzzyTimeService for converting timeInput to canonical timestamps.
 * 
 * Handles:
 * - Exact time: Parses ISO 8601 strings with timezone offsets to UTC Unix timestamps
 * - Fuzzy time: Calculates endTimestamp based on periodGranularity and periodStart
 * 
 * Based on FUZZY_TIME_API_DESIGN.md and FUZZY_TIME_IMPLEMENTATION_PLAN.md specifications.
 */
@Service
public class FuzzyTimeServiceImpl implements FuzzyTimeService {
    
    private static final Logger logger = LoggerFactory.getLogger(FuzzyTimeServiceImpl.class);
    
    @Override
    public TimeConversionResult convert(TimeInput timeInput) {
        if (timeInput == null) {
            throw new IllegalArgumentException("timeInput cannot be null");
        }
        
        // Check for exact time structure first and validate both fields
        if (timeInput.getStartTime() != null || timeInput.getEndTime() != null) {
            return handleExactTime(timeInput); // This will throw specific validation errors
        }
        
        // Check for fuzzy time structure and validate both fields
        if (timeInput.getPeriodGranularity() != null || timeInput.getPeriodStart() != null) {
            return handleFuzzyTime(timeInput); // This will throw specific validation errors
        }
        
        throw new IllegalArgumentException(
            "timeInput must contain either exact time (startTime + endTime) or fuzzy time (periodGranularity + periodStart)"
        );
    }
    
    /**
     * Handle exact time input with startTime and endTime ISO 8601 strings.
     */
    private TimeConversionResult handleExactTime(TimeInput timeInput) {
        String startTimeStr = timeInput.getStartTime();
        String endTimeStr = timeInput.getEndTime();
        
        if (startTimeStr == null || startTimeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("startTime cannot be null or empty");
        }
        if (endTimeStr == null || endTimeStr.trim().isEmpty()) {
            throw new IllegalArgumentException("endTime cannot be null or empty");
        }
        
        try {
            // Parse ISO 8601 with timezone to UTC Unix timestamp
            Long startTimestamp = parseIso8601ToUnixTimestamp(startTimeStr);
            Long endTimestamp = parseIso8601ToUnixTimestamp(endTimeStr);
            
            if (endTimestamp <= startTimestamp) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
            
            logger.debug("Converted exact time: {} -> {}, {} -> {}", 
                startTimeStr, startTimestamp, endTimeStr, endTimestamp);
            
            return new TimeConversionResult(startTimestamp, endTimestamp);
            
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO 8601 timestamp format: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle fuzzy time input with periodGranularity and periodStart.
     */
    private TimeConversionResult handleFuzzyTime(TimeInput timeInput) {
        String periodGranularity = timeInput.getPeriodGranularity();
        String periodStartStr = timeInput.getPeriodStart();
        
        if (periodGranularity == null || periodGranularity.trim().isEmpty()) {
            throw new IllegalArgumentException("periodGranularity cannot be null or empty");
        }
        if (periodStartStr == null || periodStartStr.trim().isEmpty()) {
            throw new IllegalArgumentException("periodStart cannot be null or empty");
        }
        
        try {
            // Parse periodStart ISO 8601 to get start timestamp
            Long startTimestamp = parseIso8601ToUnixTimestamp(periodStartStr);
            
            // Calculate end timestamp based on granularity
            Long endTimestamp = calculateEndTimestamp(startTimestamp, periodGranularity);
            
            logger.debug("Converted fuzzy time: {} {} -> {} to {}", 
                periodGranularity, periodStartStr, startTimestamp, endTimestamp);
            
            return new TimeConversionResult(startTimestamp, endTimestamp);
            
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO 8601 timestamp format for periodStart: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse ISO 8601 string with timezone offset to UTC Unix timestamp (seconds since epoch).
     */
    private Long parseIso8601ToUnixTimestamp(String iso8601String) {
        ZonedDateTime dateTime = ZonedDateTime.parse(iso8601String, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        return dateTime.toInstant().getEpochSecond();
    }
    
    /**
     * Calculate end timestamp based on period granularity and start timestamp.
     * 
     * Business rules from FUZZY_TIME_IMPLEMENTATION_PLAN.md:
     * - morning: 4 hours
     * - afternoon: 4 hours  
     * - evening: 4 hours
     * - night: 8 hours
     * - day: 12 hours
     * - weekend: 2 days (48 hours)
     */
    private Long calculateEndTimestamp(Long startTimestamp, String periodGranularity) {
        switch (periodGranularity.toLowerCase()) {
            case "exact":
                throw new IllegalArgumentException("exact granularity should use startTime/endTime, not periodGranularity");
            
            case "morning":
            case "afternoon":
            case "evening":
                return startTimestamp + (4 * 60 * 60); // 4 hours in seconds
            
            case "night":
                return startTimestamp + (8 * 60 * 60); // 8 hours in seconds
            
            case "day":
                return startTimestamp + (12 * 60 * 60); // 12 hours in seconds
            
            case "weekend":
                return startTimestamp + (2 * 24 * 60 * 60); // 2 days (48 hours) in seconds
            
            default:
                throw new IllegalArgumentException("Unsupported periodGranularity: " + periodGranularity + 
                    ". Supported values: morning, afternoon, evening, night, day, weekend");
        }
    }
}