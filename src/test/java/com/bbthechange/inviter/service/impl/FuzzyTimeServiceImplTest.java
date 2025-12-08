package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.service.FuzzyTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00Z", "2025-08-05T21:30:00Z");

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:15:00Z = Unix timestamp 1754421300
        // 2025-08-05T21:30:00Z = Unix timestamp 1754429400
        assertEquals(1754421300L, result.startTimestamp);
        assertEquals(1754429400L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle exact time with negative timezone offset")
    void shouldHandleExactTimeWithNegativeOffset() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00-04:00", "2025-08-05T21:30:00-04:00");

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:15:00-04:00 = 2025-08-05T23:15:00Z = Unix timestamp 1754435700
        // 2025-08-05T21:30:00-04:00 = 2025-08-06T01:30:00Z = Unix timestamp 1754443800
        assertEquals(1754435700L, result.startTimestamp);
        assertEquals(1754443800L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle exact time with positive timezone offset")
    void shouldHandleExactTimeWithPositiveOffset() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00+05:30", "2025-08-05T21:30:00+05:30");

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:15:00+05:30 = 2025-08-05T13:45:00Z = Unix timestamp 1754401500
        // 2025-08-05T21:30:00+05:30 = 2025-08-05T16:00:00Z = Unix timestamp 1754409600
        assertEquals(1754401500L, result.startTimestamp);
        assertEquals(1754409600L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle morning granularity (4 hours)")
    void shouldHandleMorningGranularity() {
        TimeInfo timeInfo = new TimeInfo("morning", "2025-08-05T08:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T08:00:00Z = Unix timestamp 1754380800
        // End = start + 4 hours = 1754380800 + 14400 = 1754395200
        assertEquals(1754380800L, result.startTimestamp);
        assertEquals(1754395200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle afternoon granularity (4 hours)")
    void shouldHandleAfternoonGranularity() {
        TimeInfo timeInfo = new TimeInfo("afternoon", "2025-08-05T13:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T13:00:00Z = Unix timestamp 1754398800
        // End = start + 4 hours = 1754398800 + 14400 = 1754413200
        assertEquals(1754398800L, result.startTimestamp);
        assertEquals(1754413200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle evening granularity (4 hours)")
    void shouldHandleEveningGranularity() {
        TimeInfo timeInfo = new TimeInfo("evening", "2025-08-05T19:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:00:00Z = Unix timestamp 1754420400
        // End = start + 4 hours = 1754420400 + 14400 = 1754434800
        assertEquals(1754420400L, result.startTimestamp);
        assertEquals(1754434800L, result.endTimestamp);
        
    }

    @Test
    @DisplayName("Should handle night granularity (8 hours)")
    void shouldHandleNightGranularity() {
        TimeInfo timeInfo = new TimeInfo("night", "2025-08-05T22:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T22:00:00Z = Unix timestamp 1754431200
        // End = start + 8 hours = 1754431200 + 28800 = 1754460000
        assertEquals(1754431200L, result.startTimestamp);
        assertEquals(1754460000L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle day granularity (12 hours)")
    void shouldHandleDayGranularity() {
        TimeInfo timeInfo = new TimeInfo("day", "2025-08-05T09:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T09:00:00Z = Unix timestamp 1754384400
        // End = start + 12 hours = 1754384400 + 43200 = 1754427600
        assertEquals(1754384400L, result.startTimestamp);
        assertEquals(1754427600L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle weekend granularity (48 hours)")
    void shouldHandleWeekendGranularity() {
        TimeInfo timeInfo = new TimeInfo("weekend", "2025-08-02T10:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-02T10:00:00Z = Unix timestamp 1754128800
        // End = start + 48 hours = 1754128800 + 172800 = 1754301600
        assertEquals(1754128800L, result.startTimestamp);
        assertEquals(1754301600L, result.endTimestamp);
        
    }

    @Test
    @DisplayName("Should handle case insensitive granularity")
    void shouldHandleCaseInsensitiveGranularity() {
        TimeInfo timeInfo = new TimeInfo("MORNING", "2025-08-05T08:00:00Z", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        assertEquals(1754380800L, result.startTimestamp);
        assertEquals(1754395200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle fuzzy time with timezone offset in periodStart")
    void shouldHandleFuzzyTimeWithTimezone() {
        TimeInfo timeInfo = new TimeInfo("evening", "2025-08-05T19:00:00-04:00", null, null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:00:00-04:00 = 2025-08-05T23:00:00Z = Unix timestamp 1754434800
        // End = start + 4 hours = 1754434800 + 14400 = 1754449200
        assertEquals(1754434800L, result.startTimestamp);
        assertEquals(1754449200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should throw exception for null timeInput")
    void shouldThrowExceptionForNullTimeInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(null);
        });
        assertEquals("timeInput cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty timeInput")
    void shouldThrowExceptionForEmptyTimeInput() {
        TimeInfo timeInfo = new TimeInfo(null, null, null, null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("timeInput must contain either exact time (startTime + optional endTime) or fuzzy time (periodGranularity + periodStart)", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle exact time with only startTime - defaults end to +2 hours")
    void shouldHandleExactTimeWithOnlyStartTime() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00Z", null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:15:00Z = Unix timestamp 1754421300
        // endTimestamp defaults to startTimestamp + 2 hours (7200 seconds)
        assertEquals(1754421300L, result.startTimestamp);
        assertEquals(1754421300L + 7200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should throw exception for incomplete fuzzy time (missing periodStart)")
    void shouldThrowExceptionForIncompleteFuzzyTime() {
        TimeInfo timeInfo = new TimeInfo("morning", null, null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("periodStart cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null startTime")
    void shouldThrowExceptionForNullStartTime() {
        TimeInfo timeInfo = new TimeInfo(null, null, null, "2025-08-05T21:30:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("startTime cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty startTime")
    void shouldThrowExceptionForEmptyStartTime() {
        TimeInfo timeInfo = new TimeInfo(null, null, "", "2025-08-05T21:30:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("startTime cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle null endTime - defaults end to +2 hours")
    void shouldHandleNullEndTime() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00Z", null);

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:15:00Z = Unix timestamp 1754421300
        // endTimestamp defaults to startTimestamp + 2 hours (7200 seconds)
        assertEquals(1754421300L, result.startTimestamp);
        assertEquals(1754421300L + 7200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should handle empty endTime - defaults end to +2 hours")
    void shouldHandleEmptyEndTime() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00Z", "   ");

        FuzzyTimeService.TimeConversionResult result = fuzzyTimeService.convert(timeInfo);

        // 2025-08-05T19:15:00Z = Unix timestamp 1754421300
        // empty endTime is treated as null, so defaults to startTimestamp + 2 hours
        assertEquals(1754421300L, result.startTimestamp);
        assertEquals(1754421300L + 7200L, result.endTimestamp);
    }

    @Test
    @DisplayName("Should throw exception for null periodGranularity")
    void shouldThrowExceptionForNullPeriodGranularity() {
        TimeInfo timeInfo = new TimeInfo(null, "2025-08-05T19:00:00Z", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("periodGranularity cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty periodGranularity")
    void shouldThrowExceptionForEmptyPeriodGranularity() {
        TimeInfo timeInfo = new TimeInfo("", "2025-08-05T19:00:00Z", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("periodGranularity cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for null periodStart")
    void shouldThrowExceptionForNullPeriodStart() {
        TimeInfo timeInfo = new TimeInfo("morning", null, null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("periodStart cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for empty periodStart")
    void shouldThrowExceptionForEmptyPeriodStart() {
        TimeInfo timeInfo = new TimeInfo("morning", "   ", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("periodStart cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for endTime before startTime")
    void shouldThrowExceptionForEndTimeBeforeStartTime() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T21:30:00Z", "2025-08-05T19:15:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("endTime must be after startTime", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for equal startTime and endTime")
    void shouldThrowExceptionForEqualTimes() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05T19:15:00Z", "2025-08-05T19:15:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("endTime must be after startTime", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for invalid ISO 8601 format")
    void shouldThrowExceptionForInvalidIso8601Format() {
        TimeInfo timeInfo = new TimeInfo(null, null, "2025-08-05 19:15:00", "2025-08-05T21:30:00Z");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertTrue(exception.getMessage().contains("Invalid ISO 8601 timestamp format"));
    }

    @Test
    @DisplayName("Should throw exception for invalid fuzzy periodStart format")
    void shouldThrowExceptionForInvalidFuzzyPeriodStartFormat() {
        TimeInfo timeInfo = new TimeInfo("morning", "invalid-date", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertTrue(exception.getMessage().contains("Invalid ISO 8601 timestamp format for periodStart"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "weekly", "hourly", "daily", "monthly"})
    @DisplayName("Should throw exception for unsupported periodGranularity")
    void shouldThrowExceptionForUnsupportedPeriodGranularity(String granularity) {
        TimeInfo timeInfo = new TimeInfo(granularity, "2025-08-05T19:00:00Z", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertTrue(exception.getMessage().contains("Unsupported periodGranularity"));
        assertTrue(exception.getMessage().contains("morning, afternoon, evening, night, day, weekend"));
    }

    @Test
    @DisplayName("Should throw exception for exact granularity with fuzzy time structure")
    void shouldThrowExceptionForExactGranularityWithFuzzyStructure() {
        TimeInfo timeInfo = new TimeInfo("exact", "2025-08-05T19:00:00Z", null, null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            fuzzyTimeService.convert(timeInfo);
        });
        assertEquals("exact granularity should use startTime/endTime, not periodGranularity", exception.getMessage());
    }
}
