package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.TimeSuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
class ScheduledEventListenerTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TimeSuggestionService timeSuggestionService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private ScheduledEventListener.IdeaAddBatchHandler ideaAddBatchHandler;

    private ScheduledEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        listener = new ScheduledEventListener(
                hangoutRepository, notificationService, timeSuggestionService,
                meterRegistry, objectMapper, 24, 48);
        listener.setIdeaAddBatchHandler(ideaAddBatchHandler);
    }

    // ============================================================================
    // Reminder message tests (no type field — backward compatible)
    // ============================================================================

    @Test
    void handleMessage_WithValidHangout_SendsNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 7200);
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(true);

        listener.handleMessage(messageBody);

        verify(notificationService).sendHangoutReminder(hangout);
        verify(meterRegistry).counter("hangout_reminder_total", "status", "sent");
    }

    @Test
    void handleMessage_WithMissingHangoutId_DoesNotSendNotification() {
        listener.handleMessage("{}");

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "missing_id");
    }

    @Test
    void handleMessage_WithBlankHangoutId_DoesNotSendNotification() {
        listener.handleMessage("{\"hangoutId\":\"\"}");

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "missing_id");
    }

    @Test
    void handleMessage_WithNullHangoutId_DoesNotSendNotification() {
        listener.handleMessage("{\"hangoutId\":null}");

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "missing_id");
    }

    @Test
    void handleMessage_WhenHangoutNotFound_DoesNotSendNotification() {
        String hangoutId = "nonexistent-hangout";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.empty());

        listener.handleMessage(messageBody);

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "not_found");
    }

    @Test
    void handleMessage_WhenAlreadySent_DoesNotSendAgain() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 7200);
        hangout.setReminderSentAt(System.currentTimeMillis() - 60000);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        listener.handleMessage(messageBody);

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(hangoutRepository, never()).setReminderSentAtIfNull(anyString(), anyLong());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "already_sent");
    }

    @Test
    void handleMessage_WhenTooCloseToStart_DoesNotSendNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 300);
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        listener.handleMessage(messageBody);

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "outside_window");
    }

    @Test
    void handleMessage_WhenTooFarFromStart_DoesNotSendNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 14400);
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        listener.handleMessage(messageBody);

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "outside_window");
    }

    @Test
    void handleMessage_WhenLostRace_DoesNotSendNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + 7200);
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(false);

        listener.handleMessage(messageBody);

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "lost_race");
    }

    @Test
    void handleMessage_WhenNoStartTime_DoesNotSendNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(null);
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        listener.handleMessage(messageBody);

        verify(notificationService, never()).sendHangoutReminder(any());
        verify(meterRegistry).counter("hangout_reminder_total", "status", "no_start_time");
    }

    @Test
    void handleMessage_WithInvalidJson_DoesNotCrash() {
        listener.handleMessage("not valid json");

        verify(notificationService, never()).sendHangoutReminder(any());
    }

    @Test
    void handleMessage_AtLowerBoundOfWindow_SendsNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + (90 * 60));
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(true);

        listener.handleMessage(messageBody);

        verify(notificationService).sendHangoutReminder(hangout);
        verify(meterRegistry).counter("hangout_reminder_total", "status", "sent");
    }

    @Test
    void handleMessage_AtUpperBoundOfWindow_SendsNotification() {
        String hangoutId = "test-hangout-123";
        String messageBody = "{\"hangoutId\":\"" + hangoutId + "\"}";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setStartTimestamp(Instant.now().getEpochSecond() + (150 * 60));
        hangout.setReminderSentAt(null);

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.setReminderSentAtIfNull(eq(hangoutId), anyLong())).thenReturn(true);

        listener.handleMessage(messageBody);

        verify(notificationService).sendHangoutReminder(hangout);
        verify(meterRegistry).counter("hangout_reminder_total", "status", "sent");
    }

    // ============================================================================
    // Time suggestion adoption message tests
    // ============================================================================

    @Nested
    class TimeSuggestionAdoption {

        @Test
        void handleMessage_WithAdoptionType_CallsAdoptForHangout() {
            String hangoutId = "test-hangout-456";
            String messageBody = "{\"type\":\"TIME_SUGGESTION_ADOPTION\",\"hangoutId\":\"" + hangoutId + "\",\"suggestionId\":\"sug-123\"}";

            listener.handleMessage(messageBody);

            verify(timeSuggestionService).adoptForHangout(hangoutId, 24, 48);
            verify(notificationService, never()).sendHangoutReminder(any());
        }

        @Test
        void handleMessage_WithAdoptionType_MissingHangoutId_LogsWarning() {
            String messageBody = "{\"type\":\"TIME_SUGGESTION_ADOPTION\"}";

            listener.handleMessage(messageBody);

            verify(timeSuggestionService, never()).adoptForHangout(anyString(), anyInt(), anyInt());
            verify(meterRegistry).counter("time_suggestion_adoption_total", "status", "missing_id");
        }

        @Test
        void handleMessage_WithAdoptionType_BlankHangoutId_LogsWarning() {
            String messageBody = "{\"type\":\"TIME_SUGGESTION_ADOPTION\",\"hangoutId\":\"\"}";

            listener.handleMessage(messageBody);

            verify(timeSuggestionService, never()).adoptForHangout(anyString(), anyInt(), anyInt());
            verify(meterRegistry).counter("time_suggestion_adoption_total", "status", "missing_id");
        }

        @Test
        void handleMessage_WithAdoptionType_WhenServiceThrows_CountsError() {
            String hangoutId = "test-hangout-456";
            String messageBody = "{\"type\":\"TIME_SUGGESTION_ADOPTION\",\"hangoutId\":\"" + hangoutId + "\"}";

            doThrow(new RuntimeException("DB error")).when(timeSuggestionService)
                    .adoptForHangout(anyString(), anyInt(), anyInt());

            listener.handleMessage(messageBody);

            verify(meterRegistry).counter("time_suggestion_adoption_total", "status", "error");
        }
    }

    // ============================================================================
    // Idea add batch message tests
    // ============================================================================

    @Nested
    class IdeaAddBatch {

        @Test
        void handleMessage_WithIdeaAddBatchType_CallsHandler() {
            String messageBody = "{\"type\":\"IDEA_ADD_BATCH\",\"groupId\":\"group-1\",\"listId\":\"list-1\",\"adderId\":\"user-1\"}";

            listener.handleMessage(messageBody);

            verify(ideaAddBatchHandler).handleIdeaAddBatch("group-1", "list-1", "user-1");
            verify(notificationService, never()).sendHangoutReminder(any());
        }

        @Test
        void handleMessage_WithIdeaAddBatchType_MissingGroupId_Discards() {
            String messageBody = "{\"type\":\"IDEA_ADD_BATCH\",\"listId\":\"list-1\",\"adderId\":\"user-1\"}";

            listener.handleMessage(messageBody);

            verify(ideaAddBatchHandler, never()).handleIdeaAddBatch(anyString(), anyString(), anyString());
            verify(meterRegistry).counter("idea_batch_notification_total", "status", "missing_field");
        }

        @Test
        void handleMessage_WithIdeaAddBatchType_MissingListId_Discards() {
            String messageBody = "{\"type\":\"IDEA_ADD_BATCH\",\"groupId\":\"group-1\",\"adderId\":\"user-1\"}";

            listener.handleMessage(messageBody);

            verify(ideaAddBatchHandler, never()).handleIdeaAddBatch(anyString(), anyString(), anyString());
            verify(meterRegistry).counter("idea_batch_notification_total", "status", "missing_field");
        }

        @Test
        void handleMessage_WithIdeaAddBatchType_NoHandler_Discards() {
            listener.setIdeaAddBatchHandler(null);
            String messageBody = "{\"type\":\"IDEA_ADD_BATCH\",\"groupId\":\"group-1\",\"listId\":\"list-1\",\"adderId\":\"user-1\"}";

            listener.handleMessage(messageBody);

            verify(meterRegistry).counter("idea_batch_notification_total", "status", "no_handler");
        }
    }
}
