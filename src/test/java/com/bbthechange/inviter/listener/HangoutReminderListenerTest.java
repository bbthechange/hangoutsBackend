package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HangoutReminderListenerTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private HangoutReminderListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        listener = new HangoutReminderListener(hangoutRepository, notificationService, meterRegistry, objectMapper);
    }

    @Test
    void handleReminder_WithValidHangout_SendsNotification() {
        // Given
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 7200); // 2 hours from now
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(true);

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService).sendHangoutReminder(hangout);
        verify(meterRegistry).counter("hangout_reminder_total", "status", "sent");
    }

    @Test
    void handleReminder_WithMissingHangoutId_DoesNotSendNotification() {
        // Given
        String messageBody = "{}";

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "missing_id");
    }

    @Test
    void handleReminder_WithBlankHangoutId_DoesNotSendNotification() {
        // Given
        String messageBody = "{\"hangoutId\":\"\"}";

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "missing_id");
    }

    @Test
    void handleReminder_WithNullHangoutId_DoesNotSendNotification() {
        // Given
        String messageBody = "{\"hangoutId\":null}";

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "missing_id");
    }

    @Test
    void handleReminder_WhenHangoutNotFound_DoesNotSendNotification() {
        // Given
        String hangoutId = "nonexistent-hangout";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.empty());

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "not_found");
    }

    @Test
    void handleReminder_WhenAlreadySent_DoesNotSendAgain() {
        // Given
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 7200);
        hangout.setReminderSentAt(System.currentTimeMillis() - 60000); // Already sent

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(hangoutRepository, never()).setReminderSentAtIfNull(anyString(), anyLong());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "already_sent");
    }

    @Test
    void handleReminder_WhenTooCloseToStart_DoesNotSendNotification() {
        // Given
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 300); // Only 5 minutes away
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "outside_window");
    }

    @Test
    void handleReminder_WhenTooFarFromStart_DoesNotSendNotification() {
        // Given
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 14400); // 4 hours away
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "outside_window");
    }

    @Test
    void handleReminder_WhenLostRace_DoesNotSendNotification() {
        // Given
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 7200);
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(false);

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "lost_race");
    }

    @Test
    void handleReminder_WhenNoStartTime_DoesNotSendNotification() {
        // Given
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(null); // No start time
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "no_start_time");
    }

    @Test
    void handleReminder_WithInvalidJson_DoesNotCrash() {
        // Given
        String messageBody = "not valid json";

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "error");
    }

    @Test
    void handleReminder_AtLowerBoundOfWindow_SendsNotification() {
        // Given - exactly 90 minutes before start (lower bound)
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + (90 * 60)); // 90 minutes
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(true);

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService).sendHangoutReminder(hangout);
        verify(meterRegistry).counter("hangout_reminder_total", "status", "sent");
    }

    @Test
    void handleReminder_AtUpperBoundOfWindow_SendsNotification() {
        // Given - exactly 150 minutes before start (upper bound)
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + (150 * 60)); // 150 minutes
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(true);

        // When
        listener.handleReminder(messageBody);

        // Then
        verify(notificationService).sendHangoutReminder(hangout);
        verify(meterRegistry).counter("hangout_reminder_total", "status", "sent");
    }
}
