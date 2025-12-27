package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReminderControllerTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private NotificationService notificationService;

    private MeterRegistry meterRegistry;
    private ReminderController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TEST_HANGOUT_ID = "hangout-12345";
    private static final long MIN_MINUTES_BEFORE_START = 90;
    private static final long MAX_MINUTES_BEFORE_START = 150;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        controller = new ReminderController(hangoutRepository, notificationService, meterRegistry);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    // ==================== Input Validation Tests ====================

    @Nested
    class InputValidationTests {

        @Test
        void sendHangoutReminder_MissingHangoutId_Returns400() throws Exception {
            // Given: Request body without hangoutId
            Map<String, String> body = Map.of();

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "missing_id").count();
            assertThat(count).isEqualTo(1.0);

            // No repository calls
            verifyNoInteractions(hangoutRepository);
            verifyNoInteractions(notificationService);
        }

        @Test
        void sendHangoutReminder_BlankHangoutId_Returns400() throws Exception {
            // Given: Request body with blank hangoutId
            Map<String, String> body = Map.of("hangoutId", "   ");

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "missing_id").count();
            assertThat(count).isEqualTo(1.0);

            // No repository calls
            verifyNoInteractions(hangoutRepository);
            verifyNoInteractions(notificationService);
        }
    }

    // ==================== Hangout Lookup Tests ====================

    @Nested
    class HangoutLookupTests {

        @Test
        void sendHangoutReminder_HangoutNotFound_Returns200() throws Exception {
            // Given: Hangout does not exist
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.empty());

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "not_found").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }
    }

    // ==================== Idempotency Tests ====================

    @Nested
    class IdempotencyTests {

        @Test
        void sendHangoutReminder_AlreadySent_Returns200AndSkips() throws Exception {
            // Given: Hangout with reminderSentAt already set
            Hangout hangout = createValidHangout();
            hangout.setReminderSentAt(System.currentTimeMillis());
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "already_sent").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }

        @Test
        void sendHangoutReminder_LostRace_Returns200AndSkips() throws Exception {
            // Given: Hangout is valid but atomic update fails (lost race)
            Hangout hangout = createValidHangout();
            hangout.setStartTimestamp(getStartTimestampInWindow());
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));
            when(hangoutRepository.setReminderSentAtIfNull(eq(TEST_HANGOUT_ID), anyLong()))
                .thenReturn(false);

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "lost_race").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }
    }

    // ==================== Start Time Validation Tests ====================

    @Nested
    class StartTimeValidationTests {

        @Test
        void sendHangoutReminder_NoStartTime_Returns200AndSkips() throws Exception {
            // Given: Hangout with null startTimestamp
            Hangout hangout = createValidHangout();
            hangout.setStartTimestamp(null);
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "no_start_time").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }

        @Test
        void sendHangoutReminder_TooEarlyWindow_Returns200AndSkips() throws Exception {
            // Given: Hangout starts in more than 150 minutes (outside window)
            Hangout hangout = createValidHangout();
            long nowSeconds = Instant.now().getEpochSecond();
            hangout.setStartTimestamp(nowSeconds + (160 * 60)); // 160 minutes from now
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "outside_window").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }

        @Test
        void sendHangoutReminder_TooLateWindow_Returns200AndSkips() throws Exception {
            // Given: Hangout starts in less than 90 minutes (outside window)
            Hangout hangout = createValidHangout();
            long nowSeconds = Instant.now().getEpochSecond();
            hangout.setStartTimestamp(nowSeconds + (80 * 60)); // 80 minutes from now
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "outside_window").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }
    }

    // ==================== Success Path Tests ====================

    @Nested
    class SuccessPathTests {

        @Test
        void sendHangoutReminder_ValidRequest_SendsNotification() throws Exception {
            // Given: Valid hangout within time window
            Hangout hangout = createValidHangout();
            hangout.setStartTimestamp(getStartTimestampInWindow());
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));
            when(hangoutRepository.setReminderSentAtIfNull(eq(TEST_HANGOUT_ID), anyLong()))
                .thenReturn(true);

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "sent").count();
            assertThat(count).isEqualTo(1.0);

            // Notification sent
            verify(notificationService).sendHangoutReminder(hangout);
        }

        @Test
        void sendHangoutReminder_AtMinBoundary_SendsNotification() throws Exception {
            // Given: Hangout exactly 90 minutes from now (min boundary)
            Hangout hangout = createValidHangout();
            long nowSeconds = Instant.now().getEpochSecond();
            hangout.setStartTimestamp(nowSeconds + (90 * 60)); // Exactly 90 minutes
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));
            when(hangoutRepository.setReminderSentAtIfNull(eq(TEST_HANGOUT_ID), anyLong()))
                .thenReturn(true);

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Notification sent
            verify(notificationService).sendHangoutReminder(hangout);
        }

        @Test
        void sendHangoutReminder_AtMaxBoundary_SendsNotification() throws Exception {
            // Given: Hangout exactly 150 minutes from now (max boundary)
            Hangout hangout = createValidHangout();
            long nowSeconds = Instant.now().getEpochSecond();
            hangout.setStartTimestamp(nowSeconds + (150 * 60)); // Exactly 150 minutes
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));
            when(hangoutRepository.setReminderSentAtIfNull(eq(TEST_HANGOUT_ID), anyLong()))
                .thenReturn(true);

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Notification sent
            verify(notificationService).sendHangoutReminder(hangout);
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    class ErrorHandlingTests {

        @Test
        void sendHangoutReminder_Exception_Returns200AndLogsError() throws Exception {
            // Given: Repository throws exception
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenThrow(new RuntimeException("Database error"));

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then: Returns 200 to prevent scheduler retries
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "error").count();
            assertThat(count).isEqualTo(1.0);

            // No notification sent
            verifyNoInteractions(notificationService);
        }

        @Test
        void sendHangoutReminder_NotificationException_Returns200() throws Exception {
            // Given: Notification service throws exception
            Hangout hangout = createValidHangout();
            hangout.setStartTimestamp(getStartTimestampInWindow());
            when(hangoutRepository.findHangoutById(TEST_HANGOUT_ID))
                .thenReturn(Optional.of(hangout));
            when(hangoutRepository.setReminderSentAtIfNull(eq(TEST_HANGOUT_ID), anyLong()))
                .thenReturn(true);
            doThrow(new RuntimeException("Notification error"))
                .when(notificationService).sendHangoutReminder(any());

            Map<String, String> body = Map.of("hangoutId", TEST_HANGOUT_ID);

            // When/Then: Returns 200 to prevent scheduler retries
            mockMvc.perform(post("/internal/reminders/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk());

            // Verify error metric
            double count = meterRegistry.counter("hangout_reminder_total", "status", "error").count();
            assertThat(count).isEqualTo(1.0);
        }
    }

    // ==================== Helper Methods ====================

    private Hangout createValidHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(TEST_HANGOUT_ID);
        hangout.setTitle("Test Hangout");
        hangout.setReminderSentAt(null);
        return hangout;
    }

    private long getStartTimestampInWindow() {
        // Return a timestamp that's 120 minutes from now (middle of the window)
        return Instant.now().getEpochSecond() + (120 * 60);
    }
}
