package com.bbthechange.inviter.service;

import com.bbthechange.inviter.config.SchedulerConfig;
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
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HangoutSchedulerService.
 *
 * Tests EventBridge Scheduler integration for hangout reminder scheduling.
 * Covers schedule creation, updates, cancellation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class HangoutSchedulerServiceTest {

    @Mock
    private SchedulerClient schedulerClient;

    @Mock
    private SchedulerConfig schedulerConfig;

    @Mock
    private HangoutRepository hangoutRepository;

    private MeterRegistry meterRegistry;
    private HangoutSchedulerService service;

    private static final String TEST_HANGOUT_ID = "test-hangout-123";
    private static final String TEST_TARGET_ARN = "arn:aws:events:us-west-2:123456789:api-destination/test";
    private static final String TEST_ROLE_ARN = "arn:aws:iam::123456789:role/scheduler-role";
    private static final String TEST_DLQ_ARN = "arn:aws:sqs:us-west-2:123456789:dlq";
    private static final String TEST_GROUP_NAME = "hangout-reminders";
    private static final int TEST_FLEXIBLE_WINDOW = 5;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        setupDefaultSchedulerConfig();
        service = new HangoutSchedulerService(schedulerClient, schedulerConfig, hangoutRepository, meterRegistry);
    }

    private void setupDefaultSchedulerConfig() {
        lenient().when(schedulerConfig.isSchedulerEnabled()).thenReturn(true);
        lenient().when(schedulerConfig.getTargetArn()).thenReturn(TEST_TARGET_ARN);
        lenient().when(schedulerConfig.getRoleArn()).thenReturn(TEST_ROLE_ARN);
        lenient().when(schedulerConfig.getDlqArn()).thenReturn(TEST_DLQ_ARN);
        lenient().when(schedulerConfig.getGroupName()).thenReturn(TEST_GROUP_NAME);
        lenient().when(schedulerConfig.getFlexibleWindowMinutes()).thenReturn(TEST_FLEXIBLE_WINDOW);
    }

    // ==================== scheduleReminder() Tests ====================

    @Nested
    class ScheduleReminderTests {

        @Test
        void scheduleReminder_WhenSchedulerDisabled_DoesNothing() {
            // Given: Scheduler is disabled
            when(schedulerConfig.isSchedulerEnabled()).thenReturn(false);
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));

            // When
            service.scheduleReminder(hangout);

            // Then: No scheduler interactions
            verifyNoInteractions(schedulerClient);
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenSchedulerClientNull_DoesNothing() {
            // Given: SchedulerClient is null (mimics disabled state)
            when(schedulerConfig.isSchedulerEnabled()).thenReturn(true);
            HangoutSchedulerService serviceWithNullClient =
                    new HangoutSchedulerService(null, schedulerConfig, hangoutRepository, meterRegistry);
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));

            // When
            serviceWithNullClient.scheduleReminder(hangout);

            // Then: No exceptions, no repository calls
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
            verifyNoInteractions(schedulerClient);
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenReminderTimeAlreadyPassed_DoesNothing() {
            // Given: Hangout starts in 1 hour (reminder would be 1 hour ago)
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(1));

            // When
            service.scheduleReminder(hangout);

            // Then: No schedule created (reminder time in the past)
            verifyNoInteractions(schedulerClient);
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenReminderTimeJustPassed_DoesNothing() {
            // Given: Hangout starts in 2 hours + 30 seconds
            // Reminder would be at now + 30 seconds, which is < MIN_SCHEDULE_ADVANCE_SECONDS (60s)
            long startTime = Instant.now().getEpochSecond() + (2 * 60 * 60) + 30;
            Hangout hangout = createHangoutWithStartTime(startTime);

            // When
            service.scheduleReminder(hangout);

            // Then: No schedule created (too close to now)
            verifyNoInteractions(schedulerClient);
            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void scheduleReminder_WhenScheduleDoesNotExist_CreatesNewSchedule() {
            // Given: Schedule doesn't exist
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().scheduleArn("arn:schedule").build());

            // When
            service.scheduleReminder(hangout);

            // Then: Create schedule called with correct parameters
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());

            CreateScheduleRequest request = captor.getValue();
            assertThat(request.name()).isEqualTo("hangout-" + TEST_HANGOUT_ID);
            assertThat(request.groupName()).isEqualTo(TEST_GROUP_NAME);
            assertThat(request.scheduleExpression()).startsWith("at(");
            assertThat(request.target().arn()).isEqualTo(TEST_TARGET_ARN);
            assertThat(request.target().roleArn()).isEqualTo(TEST_ROLE_ARN);
            assertThat(request.actionAfterCompletion()).isEqualTo(ActionAfterCompletion.DELETE);

            // Verify repository updated
            verify(hangoutRepository).updateReminderScheduleName(TEST_HANGOUT_ID, "hangout-" + TEST_HANGOUT_ID);

            // Verify success metric
            double count = meterRegistry.counter("hangout_schedule_created", "status", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void scheduleReminder_WhenScheduleExists_UpdatesSchedule() {
            // Given: Schedule already exists
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                    .thenReturn(GetScheduleResponse.builder().name("hangout-" + TEST_HANGOUT_ID).build());
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenReturn(UpdateScheduleResponse.builder().scheduleArn("arn:schedule").build());

            // When
            service.scheduleReminder(hangout);

            // Then: Update schedule called (not create)
            verify(schedulerClient, never()).createSchedule(any(CreateScheduleRequest.class));

            ArgumentCaptor<UpdateScheduleRequest> captor = ArgumentCaptor.forClass(UpdateScheduleRequest.class);
            verify(schedulerClient).updateSchedule(captor.capture());

            UpdateScheduleRequest request = captor.getValue();
            assertThat(request.name()).isEqualTo("hangout-" + TEST_HANGOUT_ID);
            assertThat(request.groupName()).isEqualTo(TEST_GROUP_NAME);

            // Verify repository updated
            verify(hangoutRepository).updateReminderScheduleName(TEST_HANGOUT_ID, "hangout-" + TEST_HANGOUT_ID);
        }

        @Test
        void scheduleReminder_WhenCreateThrowsConflict_RetriesWithUpdate() {
            // Given: getSchedule returns not found, but createSchedule throws ConflictException (race condition)
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenThrow(ConflictException.builder().message("Already exists").build());
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenReturn(UpdateScheduleResponse.builder().scheduleArn("arn:schedule").build());

            // When
            service.scheduleReminder(hangout);

            // Then: Create attempted first, then update as fallback
            verify(schedulerClient).createSchedule(any(CreateScheduleRequest.class));
            verify(schedulerClient).updateSchedule(any(UpdateScheduleRequest.class));

            // Verify repository updated
            verify(hangoutRepository).updateReminderScheduleName(TEST_HANGOUT_ID, "hangout-" + TEST_HANGOUT_ID);

            // Verify conflict_resolved metric
            double count = meterRegistry.counter("hangout_schedule_created", "status", "conflict_resolved").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void scheduleReminder_VerifiesScheduleExpressionFormat() {
            // Given: Hangout starting 5 hours from now
            // Verify the schedule expression follows the at(yyyy-MM-dd'T'HH:mm:ss) format
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().scheduleArn("arn:schedule").build());

            // When
            service.scheduleReminder(hangout);

            // Then: Verify schedule expression format matches at(yyyy-MM-dd'T'HH:mm:ss)
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());

            String expression = captor.getValue().scheduleExpression();
            // Verify format: at(yyyy-MM-dd'T'HH:mm:ss)
            assertThat(expression).matches("at\\(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\)");
        }

        @Test
        void scheduleReminder_VerifiesInputContainsHangoutId() {
            // Given
            Hangout hangout = createHangoutWithStartTime(hoursFromNow(5));
            when(schedulerClient.getSchedule(any(GetScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().scheduleArn("arn:schedule").build());

            // When
            service.scheduleReminder(hangout);

            // Then: Verify target input contains hangoutId as JSON
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());

            String expectedInput = "{\"hangoutId\":\"" + TEST_HANGOUT_ID + "\"}";
            assertThat(captor.getValue().target().input()).isEqualTo(expectedInput);
        }
    }

    // ==================== cancelReminder() Tests ====================

    @Nested
    class CancelReminderTests {

        @Test
        void cancelReminder_WhenSchedulerDisabled_DoesNothing() {
            // Given: Scheduler is disabled
            when(schedulerConfig.isSchedulerEnabled()).thenReturn(false);
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-abc123");

            // When
            service.cancelReminder(hangout);

            // Then: No scheduler interactions
            verifyNoInteractions(schedulerClient);
        }

        @Test
        void cancelReminder_WhenHangoutHasScheduleName_DeletesByStoredName() {
            // Given: Hangout has a stored schedule name
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-abc123");
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenReturn(DeleteScheduleResponse.builder().build());

            // When
            service.cancelReminder(hangout);

            // Then: Delete called with stored name
            ArgumentCaptor<DeleteScheduleRequest> captor = ArgumentCaptor.forClass(DeleteScheduleRequest.class);
            verify(schedulerClient).deleteSchedule(captor.capture());

            assertThat(captor.getValue().name()).isEqualTo("hangout-abc123");
            assertThat(captor.getValue().groupName()).isEqualTo(TEST_GROUP_NAME);

            // Verify success metric
            double count = meterRegistry.counter("hangout_schedule_deleted", "status", "success").count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void cancelReminder_WhenHangoutHasNoScheduleName_DeletesByDefaultName() {
            // Given: Hangout has no stored schedule name
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName(null);
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenReturn(DeleteScheduleResponse.builder().build());

            // When
            service.cancelReminder(hangout);

            // Then: Delete called with default name pattern
            ArgumentCaptor<DeleteScheduleRequest> captor = ArgumentCaptor.forClass(DeleteScheduleRequest.class);
            verify(schedulerClient).deleteSchedule(captor.capture());

            assertThat(captor.getValue().name()).isEqualTo("hangout-" + TEST_HANGOUT_ID);
        }

        @Test
        void cancelReminder_WhenScheduleNotFound_DoesNotThrow() {
            // Given: Schedule doesn't exist
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-xyz789");
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Not found").build());

            // When/Then: No exception propagated (idempotent behavior)
            service.cancelReminder(hangout);

            // Verify delete was attempted
            verify(schedulerClient).deleteSchedule(any(DeleteScheduleRequest.class));

            // No error metric (this is expected behavior)
            double count = meterRegistry.counter("hangout_schedule_deleted", "status", "error").count();
            assertThat(count).isEqualTo(0.0);
        }

        @Test
        void cancelReminder_WhenOtherErrorOccurs_LogsAndContinues() {
            // Given: Scheduler throws a different exception
            Hangout hangout = createHangout();
            hangout.setReminderScheduleName("hangout-error");
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenThrow(SchedulerException.builder().message("Internal error").build());

            // When/Then: No exception propagated
            service.cancelReminder(hangout);

            // Verify error metric incremented
            double count = meterRegistry.counter("hangout_schedule_deleted", "status", "error").count();
            assertThat(count).isEqualTo(1.0);
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
