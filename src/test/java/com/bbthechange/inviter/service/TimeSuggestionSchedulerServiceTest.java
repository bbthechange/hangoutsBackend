package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeSuggestionSchedulerServiceTest {

    @Mock
    private EventBridgeSchedulerClient eventBridgeClient;

    private TimeSuggestionSchedulerService service;

    @BeforeEach
    void setUp() {
        service = new TimeSuggestionSchedulerService(eventBridgeClient, new ObjectMapper(), 24, 48);
    }

    @Test
    void scheduleAdoptionChecks_WhenEnabled_CreatesTwoSchedules() {
        when(eventBridgeClient.isEnabled()).thenReturn(true);
        Instant createdAt = Instant.now();

        service.scheduleAdoptionChecks("hangout-1", "sug-1", createdAt);

        verify(eventBridgeClient, times(2)).createOrUpdateSchedule(
                anyString(), anyString(), anyString(), eq(false));
    }

    @Test
    void scheduleAdoptionChecks_WhenEnabled_UsesCorrectScheduleNames() {
        when(eventBridgeClient.isEnabled()).thenReturn(true);
        Instant createdAt = Instant.now();

        service.scheduleAdoptionChecks("hangout-1", "sug-123", createdAt);

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventBridgeClient, times(2)).createOrUpdateSchedule(
                nameCaptor.capture(), anyString(), anyString(), eq(false));

        assertThat(nameCaptor.getAllValues()).containsExactly(
                "time-suggestion-sug-123-short",
                "time-suggestion-sug-123-long");
    }

    @Test
    void scheduleAdoptionChecks_InputJsonContainsTypeAndIds() {
        when(eventBridgeClient.isEnabled()).thenReturn(true);
        Instant createdAt = Instant.now();

        service.scheduleAdoptionChecks("hangout-abc", "sug-xyz", createdAt);

        ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventBridgeClient, times(2)).createOrUpdateSchedule(
                anyString(), anyString(), inputCaptor.capture(), eq(false));

        String input = inputCaptor.getValue();
        assertThat(input).contains("\"type\":\"TIME_SUGGESTION_ADOPTION\"");
        assertThat(input).contains("\"hangoutId\":\"hangout-abc\"");
        assertThat(input).contains("\"suggestionId\":\"sug-xyz\"");
    }

    @Test
    void scheduleAdoptionChecks_WhenDisabled_DoesNothing() {
        when(eventBridgeClient.isEnabled()).thenReturn(false);

        service.scheduleAdoptionChecks("hangout-1", "sug-1", Instant.now());

        verify(eventBridgeClient, never()).createOrUpdateSchedule(
                anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void scheduleAdoptionChecks_SkipsPastDueWindows() {
        when(eventBridgeClient.isEnabled()).thenReturn(true);
        // Created 30 hours ago — short window (24h) already passed, long window (48h) still valid
        Instant createdAt = Instant.now().minusSeconds(30 * 3600);

        service.scheduleAdoptionChecks("hangout-1", "sug-1", createdAt);

        // Only long window schedule should be created
        verify(eventBridgeClient, times(1)).createOrUpdateSchedule(
                eq("time-suggestion-sug-1-long"), anyString(), anyString(), eq(false));
    }

    @Test
    void cancelAdoptionChecks_DeletesBothSchedules() {
        when(eventBridgeClient.isEnabled()).thenReturn(true);

        service.cancelAdoptionChecks("sug-123");

        verify(eventBridgeClient).deleteSchedule("time-suggestion-sug-123-short");
        verify(eventBridgeClient).deleteSchedule("time-suggestion-sug-123-long");
    }

    @Test
    void cancelAdoptionChecks_WhenDisabled_DoesNothing() {
        when(eventBridgeClient.isEnabled()).thenReturn(false);

        service.cancelAdoptionChecks("sug-123");

        verify(eventBridgeClient, never()).deleteSchedule(anyString());
    }
}
