package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.FuzzyTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for FuzzyTimeServiceImpl.
 * 
 * Tests cover:
 * - Exact time parsing with various timezones
 * - Fuzzy time calculations for all granularities
 * - Error handling for invalid inputs
 * - Edge cases and boundary conditions
 */
class FuzzyTimeServiceImplTest {

    private FuzzyTimeService fuzzyTimeService;

    @BeforeEach
    void setUp() {
        fuzzyTimeService = new FuzzyTimeServiceImpl();
    }

    @Test
    @DisplayName("Should handle exact time with UTC timezone")
    void shouldHandleExactTimeWithUTC() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00Z");
        timeInput.put("endTime", "2025-08-05T21:30:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T19:15:00Z = Unix timestamp 1754421300
        // 2025-08-05T21:30:00Z = Unix timestamp 1754429400
        assertEquals(1754421300L, result.getStartTimestamp());
        assertEquals(1754429400L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle exact time with negative timezone offset")
    void shouldHandleExactTimeWithNegativeOffset() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00-04:00");
        timeInput.put("endTime", "2025-08-05T21:30:00-04:00");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T19:15:00-04:00 = 2025-08-05T23:15:00Z = Unix timestamp 1754435700
        // 2025-08-05T21:30:00-04:00 = 2025-08-06T01:30:00Z = Unix timestamp 1754443800
        assertEquals(1754435700L, result.getStartTimestamp());
        assertEquals(1754443800L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle exact time with positive timezone offset")
    void shouldHandleExactTimeWithPositiveOffset() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00+05:30");
        timeInput.put("endTime", "2025-08-05T21:30:00+05:30");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T19:15:00+05:30 = 2025-08-05T13:45:00Z = Unix timestamp 1754401500
        // 2025-08-05T21:30:00+05:30 = 2025-08-05T16:00:00Z = Unix timestamp 1754409600
        assertEquals(1754401500L, result.getStartTimestamp());
        assertEquals(1754409600L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle morning granularity (4 hours)")
    void shouldHandleMorningGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "morning");
        timeInput.put("periodStart", "2025-08-05T08:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T08:00:00Z = Unix timestamp 1754380800
        // End = start + 4 hours = 1754380800 + 14400 = 1754395200
        assertEquals(1754380800L, result.getStartTimestamp());
        assertEquals(1754395200L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle afternoon granularity (4 hours)")
    void shouldHandleAfternoonGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "afternoon");
        timeInput.put("periodStart", "2025-08-05T13:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T13:00:00Z = Unix timestamp 1754398800
        // End = start + 4 hours = 1754398800 + 14400 = 1754413200
        assertEquals(1754398800L, result.getStartTimestamp());
        assertEquals(1754413200L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle evening granularity (4 hours)")
    void shouldHandleEveningGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "evening");
        timeInput.put("periodStart", "2025-08-05T19:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T19:00:00Z = Unix timestamp 1754420400
        // End = start + 4 hours = 1754420400 + 14400 = 1754434800
        assertEquals(1754420400L, result.getStartTimestamp());
        assertEquals(1754434800L, result.getEndTimestamp());
        
    }

    @Test
    @DisplayName("Should handle night granularity (8 hours)")
    void shouldHandleNightGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "night");
        timeInput.put("periodStart", "2025-08-05T22:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T22:00:00Z = Unix timestamp 1754431200
        // End = start + 8 hours = 1754431200 + 28800 = 1754460000
        assertEquals(1754431200L, result.getStartTimestamp());
        assertEquals(1754460000L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle day granularity (12 hours)")
    void shouldHandleDayGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "day");
        timeInput.put("periodStart", "2025-08-05T09:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T09:00:00Z = Unix timestamp 1754384400
        // End = start + 12 hours = 1754384400 + 43200 = 1754427600
        assertEquals(1754384400L, result.getStartTimestamp());
        assertEquals(1754427600L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle weekend granularity (48 hours)")
    void shouldHandleWeekendGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "weekend");
        timeInput.put("periodStart", "2025-08-02T10:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-02T10:00:00Z = Unix timestamp 1754128800
        // End = start + 48 hours = 1754128800 + 172800 = 1754301600
        assertEquals(1754128800L, result.getStartTimestamp());
        assertEquals(1754301600L, result.getEndTimestamp());
        
    }

    @Test
    @DisplayName("Should handle case insensitive granularity")
    void shouldHandleCaseInsensitiveGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "MORNING");
        timeInput.put("periodStart", "2025-08-05T08:00:00Z");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        assertEquals(1754380800L, result.getStartTimestamp());
        assertEquals(1754395200L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should handle fuzzy time with timezone offset in periodStart")
    void shouldHandleFuzzyTimeWithTimezone() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "evening");
        timeInput.put("periodStart", "2025-08-05T19:00:00-04:00");

        FuzzyTimeService.FuzzyTimeResult result = fuzzyTimeService.convertTimeInput(timeInput);

        // 2025-08-05T19:00:00-04:00 = 2025-08-05T23:00:00Z = Unix timestamp 1754434800
        // End = start + 4 hours = 1754434800 + 14400 = 1754449200
        assertEquals(1754434800L, result.getStartTimestamp());
        assertEquals(1754449200L, result.getEndTimestamp());
    }

    @Test
    @DisplayName("Should throw exception for null timeInput")
    void shouldThrowExceptionForNullTimeInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(null);
        });
        assertEquals("timeInput cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty timeInput")
    void shouldThrowExceptionForEmptyTimeInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(new HashMap<>());
        });
        assertEquals("timeInput cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for incomplete exact time (missing endTime)")
    void shouldThrowExceptionForIncompleteExactTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertTrue(exception.getMessage().contains("must contain either"));
    }

    @Test
    @DisplayName("Should throw exception for incomplete fuzzy time (missing periodStart)")
    void shouldThrowExceptionForIncompleteFuzzyTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "morning");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertTrue(exception.getMessage().contains("must contain either"));
    }

    @Test
    @DisplayName("Should throw exception for null startTime")
    void shouldThrowExceptionForNullStartTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", null);
        timeInput.put("endTime", "2025-08-05T21:30:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("startTime cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty startTime")
    void shouldThrowExceptionForEmptyStartTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "");
        timeInput.put("endTime", "2025-08-05T21:30:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("startTime cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null endTime")
    void shouldThrowExceptionForNullEndTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00Z");
        timeInput.put("endTime", null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("endTime cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty endTime")
    void shouldThrowExceptionForEmptyEndTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00Z");
        timeInput.put("endTime", "   ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("endTime cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null periodGranularity")
    void shouldThrowExceptionForNullPeriodGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", null);
        timeInput.put("periodStart", "2025-08-05T19:00:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("periodGranularity cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty periodGranularity")
    void shouldThrowExceptionForEmptyPeriodGranularity() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "");
        timeInput.put("periodStart", "2025-08-05T19:00:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("periodGranularity cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null periodStart")
    void shouldThrowExceptionForNullPeriodStart() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "morning");
        timeInput.put("periodStart", null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("periodStart cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty periodStart")
    void shouldThrowExceptionForEmptyPeriodStart() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "morning");
        timeInput.put("periodStart", "   ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("periodStart cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for endTime before startTime")
    void shouldThrowExceptionForEndTimeBeforeStartTime() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T21:30:00Z");
        timeInput.put("endTime", "2025-08-05T19:15:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("endTime must be after startTime", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for equal startTime and endTime")
    void shouldThrowExceptionForEqualTimes() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05T19:15:00Z");
        timeInput.put("endTime", "2025-08-05T19:15:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("endTime must be after startTime", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for invalid ISO 8601 format")
    void shouldThrowExceptionForInvalidIso8601Format() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "2025-08-05 19:15:00");
        timeInput.put("endTime", "2025-08-05T21:30:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertTrue(exception.getMessage().contains("Invalid ISO 8601 timestamp format"));
    }

    @Test
    @DisplayName("Should throw exception for invalid fuzzy periodStart format")
    void shouldThrowExceptionForInvalidFuzzyPeriodStartFormat() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "morning");
        timeInput.put("periodStart", "invalid-date");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertTrue(exception.getMessage().contains("Invalid ISO 8601 timestamp format for periodStart"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "weekly", "hourly", "daily", "monthly"})
    @DisplayName("Should throw exception for unsupported periodGranularity")
    void shouldThrowExceptionForUnsupportedPeriodGranularity(String granularity) {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", granularity);
        timeInput.put("periodStart", "2025-08-05T19:00:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertTrue(exception.getMessage().contains("Unsupported periodGranularity"));
        assertTrue(exception.getMessage().contains("morning, afternoon, evening, night, day, weekend"));
    }

    @Test
    @DisplayName("Should throw exception for exact granularity with fuzzy time structure")
    void shouldThrowExceptionForExactGranularityWithFuzzyStructure() {
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("periodGranularity", "exact");
        timeInput.put("periodStart", "2025-08-05T19:00:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convertTimeInput(timeInput);
        });
        assertEquals("exact granularity should use startTime/endTime, not periodGranularity", exception.getMessage());
    }
}