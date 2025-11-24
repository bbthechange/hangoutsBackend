package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.HangoutDetailDTO;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for time conversion and formatting methods in HangoutServiceImpl.
 *
 * Covers:
 * - convertToUtcIsoString: handling ISO format, Unix timestamps, unknown formats
 * - formatTimeInfoForResponse: fuzzy time vs exact time formatting, null handling
 * - Integration with getHangoutDetail to verify time formatting in responses
 */
class HangoutServiceTimeConversionTest extends HangoutServiceTestBase {

    @Nested
    class ConvertToUtcIsoStringTests {
        // These tests verify the private method through getHangoutDetail

        @Test
        void getHangoutDetail_WithISOFormatTimeInfo_ConvertsToUTC() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set timeInput with ISO format timestamps
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime("2025-08-05T19:00:00-07:00"); // Pacific time
            timeInput.setEndTime("2025-08-05T21:00:00-07:00");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Should convert to UTC (Z suffix)
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).endsWith("Z");
            assertThat(resultTimeInfo.getEndTime()).endsWith("Z");
            assertThat(resultTimeInfo.getPeriodGranularity()).isNull();
            assertThat(resultTimeInfo.getPeriodStart()).isNull();
        }

        @Test
        void getHangoutDetail_WithUnixTimestampInExactTime_ConvertsToISO() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set timeInput with Unix timestamps
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime("1754557200"); // Unix timestamp
            timeInput.setEndTime("1754571600");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Should convert to ISO UTC format
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
            assertThat(resultTimeInfo.getEndTime()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
            assertThat(resultTimeInfo.getPeriodGranularity()).isNull();
            assertThat(resultTimeInfo.getPeriodStart()).isNull();
        }

        @Test
        void getHangoutDetail_WithInvalidTimeFormat_ReturnsAsIs() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set timeInput with invalid/unknown format
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime("invalid-time-format");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Should return as-is when parsing fails
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).isEqualTo("invalid-time-format");
        }

        @Test
        void getHangoutDetail_WithNullTimeString_ReturnsNull() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set timeInput with null timestamp
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime(null);
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).isNull();
        }

        @Test
        void getHangoutDetail_WithAlreadyUTCTime_RemainsUnchanged() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set timeInput with already UTC timestamp
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime("2025-08-05T19:00:00Z");
            timeInput.setEndTime("2025-08-05T21:00:00Z");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Should remain UTC
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).endsWith("Z");
            assertThat(resultTimeInfo.getEndTime()).endsWith("Z");
        }
    }

    @Nested
    class FormatTimeInfoForResponseTests {

        @Test
        void getHangoutDetail_WithFuzzyTime_OnlyReturnsPeriodFields() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set fuzzy time input
            TimeInfo timeInput = new TimeInfo();
            timeInput.setPeriodGranularity("evening");
            timeInput.setPeriodStart("2025-08-05T19:00:00Z");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Only period fields should be returned for fuzzy time
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getPeriodGranularity()).isEqualTo("evening");
            assertThat(resultTimeInfo.getPeriodStart()).isNotNull();
            assertThat(resultTimeInfo.getStartTime()).isNull();
            assertThat(resultTimeInfo.getEndTime()).isNull();
        }

        @Test
        void getHangoutDetail_WithExactTime_OnlyReturnsStartEndFields() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set exact time input
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime("2025-08-05T19:00:00Z");
            timeInput.setEndTime("2025-08-05T21:00:00Z");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Only start/end time fields should be returned for exact time
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).isNotNull();
            assertThat(resultTimeInfo.getEndTime()).isNotNull();
            assertThat(resultTimeInfo.getPeriodGranularity()).isNull();
            assertThat(resultTimeInfo.getPeriodStart()).isNull();
        }

        @Test
        void getHangoutDetail_WithNullTimeInfo_ReturnsNull() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);
            hangout.setTimeInput(null);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then
            assertThat(result.getHangout().getTimeInput()).isNull();
        }

        @Test
        void getHangoutDetail_WithFuzzyTimeAndUnixTimestamp_ConvertsToISO() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set fuzzy time with Unix timestamp
            TimeInfo timeInput = new TimeInfo();
            timeInput.setPeriodGranularity("morning");
            timeInput.setPeriodStart("1754557200"); // Unix timestamp
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then - Unix timestamp should be converted to ISO format
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getPeriodGranularity()).isEqualTo("morning");
            assertThat(resultTimeInfo.getPeriodStart()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        }

        @Test
        void getHangoutDetail_WithExactTimeNoEndTime_HandlesGracefully() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setVisibility(EventVisibility.PUBLIC);

            // Set exact time with only startTime
            TimeInfo timeInput = new TimeInfo();
            timeInput.setStartTime("2025-08-05T19:00:00Z");
            hangout.setTimeInput(timeInput);

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

            // Then
            TimeInfo resultTimeInfo = result.getHangout().getTimeInput();
            assertThat(resultTimeInfo.getStartTime()).isNotNull();
            assertThat(resultTimeInfo.getEndTime()).isNull();
        }
    }
}
