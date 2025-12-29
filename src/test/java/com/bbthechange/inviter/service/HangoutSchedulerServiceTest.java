package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HangoutSchedulerService.
 *
 * Tests business logic for hangout reminder scheduling.
 * AWS SDK interactions are mocked via EventBridgeSchedulerClient.
 */
@ExtendWith(MockitoExtension.class)
class HangoutSchedulerServiceTest {

    @Mock
    private EventBridgeSchedulerClient eventBridgeClient;

    @Mock
    private HangoutRepository hangoutRepository;

    private MeterRegistry meterRegistry;
    private HangoutSchedulerService service;

    private static final String TEST_HANGOUT_ID = "test-hangout-123";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(eventBridgeClient.isEnabled()).thenReturn(true);
        service = new HangoutSchedulerService(eventBridgeClient, hangoutRepository, meterRegistry);
    }

    // ==================== scheduleReminder() Tests ====================

    @Nested
    class ScheduleReminderTests {

        @Test
        void scheduleReminder_WhenSchedulerDisabled_DoesNothing() {
            // Given: Scheduler is disabled
            when(eventBridgeClient.isEnabled()).thenReturn(false);
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));

            // When
            service.scheduleReminder(hangout);

            // Then: No scheduler or repository interactions
            verify(eventBridgeClient, never()).createOrUpdateSchedule(any(), any(), any(), anyBoolean());
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenHangoutHasNoStartTime_DoesNothing() {
            // Given: Hangout with null startTimestamp
            Hangout hangout = createHangout();
            hangout.setStartTimestamp(null);

            // When
            service.scheduleReminder(hangout);

            // Then: No schedule created
            verify(eventBridgeClient, never()).createOrUpdateSchedule(any(), any(), any(), anyBoolean());
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenReminderTimeAlreadyPassed_DoesNotSchedule() {
            // Given: Hangout starts in 1 hour (reminder would be 1 hour ago)
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(1));

            // When
            service.scheduleReminder(hangout);

            // Then: No schedule created (reminder time in the past)
            verify(eventBridgeClient, never()).createOrUpdateSchedule(any(), any(), any(), anyBoolean());
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenReminderTimeAlreadyPassed_DeletesExistingSchedule() {
            // Given: Hangout starts in 1 hour AND has an existing schedule
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(1));
            hangout.setReminderScheduleName("hangout-existing");

            // When
            service.scheduleReminder(hangout);

            // Then: Existing schedule is deleted
            verify(eventBridgeClient).deleteSchedule("hangout-existing");
            verify(eventBridgeClient, never()).createOrUpdateSchedule(any(), any(), any(), anyBoolean());
        }

        @Test
        void scheduleReminder_WhenReminderTimeJustPassed_DoesNotSchedule() {
            // Given: Hangout starts in 2 hours + 30 seconds
            // Reminder would be at now + 30 seconds, which is < MIN_SCHEDULE_ADVANCE_SECONDS (60s)
            long startTime = Instant.now().getEpochSecond() + (2 * 60 * 60) + 30;
            Hangout hangout = createHangoutWithStartTime(startTime);

            // When
            service.scheduleReminder(hangout);

            // Then: No schedule created (too close to now)
            verify(eventBridgeClient, never()).createOrUpdateSchedule(any(), any(), any(), anyBoolean());
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenNoExistingSchedule_CreatesWithExpectedToExistFalse() {
            // Given: Hangout without existing schedule name
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            hangout.setReminderScheduleName(null);

            // When
            service.scheduleReminder(hangout);

            // Then: Create/update called with expectedToExist=false
            verify(eventBridgeClient).createOrUpdateSchedule(
                    eq("hangout-" + TEST_HANGOUT_ID),
                    argThat(expr -> expr.startsWith("at(")),
                    eq("{\"hangoutId\":\"" + TEST_HANGOUT_ID + "\"}"),
                    eq(false)  // expectedToExist = false
            );

            // Verify repository updated
            verify(hangoutRepository).updateReminderScheduleName(TEST_HANGOUT_ID, "hangout-" + TEST_HANGOUT_ID);

            // Verify success metric
            assertThat(meterRegistry.counter("hangout_schedule_created", "status", "success").count())
                    .isEqualTo(1.0);
        }

        @Test
        void scheduleReminder_WhenExistingSchedule_UpdatesWithExpectedToExistTrue() {
            // Given: Hangout with existing schedule name
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            hangout.setReminderScheduleName("hangout-existing-abc");

            // When
            service.scheduleReminder(hangout);

            // Then: Create/update called with expectedToExist=true and existing name
            verify(eventBridgeClient).createOrUpdateSchedule(
                    eq("hangout-existing-abc"),  // Uses stored name
                    argThat(expr -> expr.startsWith("at(")),
                    eq("{\"hangoutId\":\"" + TEST_HANGOUT_ID + "\"}"),
                    eq(true)  // expectedToExist = true
            );

            // Verify repository updated with same name
            verify(hangoutRepository).updateReminderScheduleName(TEST_HANGOUT_ID, "hangout-existing-abc");
        }

        @Test
        void scheduleReminder_VerifiesScheduleExpressionFormat() {
            // Given: Hangout starting 5 hours from now
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));

            // When
            service.scheduleReminder(hangout);

            // Then: Verify schedule expression format matches at(yyyy-MM-dd'T'HH:mm:ss)
            ArgumentCaptor<String> expressionCaptor = ArgumentCaptor.forClass(String.class);
            verify(eventBridgeClient).createOrUpdateSchedule(any(), expressionCaptor.capture(), any(), anyBoolean());

            String expression = expressionCaptor.getValue();
            assertThat(expression).matches("at\\(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\)");
        }

        @Test
        void scheduleReminder_WhenClientThrowsException_IncrementsErrorMetric() {
            // Given: Client throws exception
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            doThrow(new RuntimeException("AWS Error"))
                    .when(eventBridgeClient).createOrUpdateSchedule(any(), any(), any(), anyBoolean());

            // When
            service.scheduleReminder(hangout);

            // Then: Error metric incremented, no exception propagated
            assertThat(meterRegistry.counter("hangout_schedule_created", "status", "error").count())
                    .isEqualTo(1.0);
        }
    }

    // ==================== cancelReminder() Tests ====================

    @Nested
    class CancelReminderTests {

        @Test
        void cancelReminder_WhenSchedulerDisabled_DoesNothing() {
            // Given: Scheduler is disabled
            when(eventBridgeClient.isEnabled()).thenReturn(false);
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-abc123");

            // When
            service.cancelReminder(hangout);

            // Then: No scheduler interactions
            verify(eventBridgeClient, never()).deleteSchedule(any());
        }

        @Test
        void cancelReminder_WhenHangoutHasScheduleName_DeletesByStoredName() {
            // Given: Hangout has a stored schedule name
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-abc123");

            // When
            service.cancelReminder(hangout);

            // Then: Delete called with stored name
            verify(eventBridgeClient).deleteSchedule("hangout-abc123");

            // Verify success metric
            assertThat(meterRegistry.counter("hangout_schedule_deleted", "status", "success").count())
                    .isEqualTo(1.0);
        }

        @Test
        void cancelReminder_WhenHangoutHasNoScheduleName_DeletesByDefaultName() {
            // Given: Hangout has no stored schedule name
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName(null);

            // When
            service.cancelReminder(hangout);

            // Then: Delete called with default name pattern
            verify(eventBridgeClient).deleteSchedule("hangout-" + TEST_HANGOUT_ID);
        }

        @Test
        void cancelReminder_WhenClientThrowsException_IncrementsErrorMetric() {
            // Given: Client throws exception
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-error");
            doThrow(new RuntimeException("Delete failed"))
                    .when(eventBridgeClient).deleteSchedule(any());

            // When
            service.cancelReminder(hangout);

            // Then: Error metric incremented, no exception propagated
            assertThat(meterRegistry.counter("hangout_schedule_deleted", "status", "error").count())
                    .isEqualTo(1.0);
        }
    }

    // ==================== Helper Methods ====================

    private Hangout createHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(TEST_HANGOUT_ID);
        hangout.setTitle("Test Hangout");
        return hangout;
    }

    private Hangout createHangoutWithStartTime(long startTimestamp) {
        Hangout hangout = createHangout();
        hangout.setStartTimestamp(startTimestamp);
        return hangout;
    }

    private long hoursFromNow(int hours) {
        return Instant.now().getEpochSecond() + (hours * 60 * 60);
    }
}
