package com.bbthechange.inviter.client;

import com.bbthechange.inviter.config.SchedulerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.ActionAfterCompletion;
import software.amazon.awssdk.services.scheduler.model.ConflictException;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleResponse;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.DeleteScheduleResponse;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindowMode;
import software.amazon.awssdk.services.scheduler.model.InternalServerException;
import software.amazon.awssdk.services.scheduler.model.ResourceNotFoundException;
import software.amazon.awssdk.services.scheduler.model.Target;
import software.amazon.awssdk.services.scheduler.model.UpdateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.UpdateScheduleResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventBridgeSchedulerClientTest {

    private static final String SCHEDULE_NAME = "test-schedule";
    private static final String SCHEDULE_EXPRESSION = "at(2025-01-01T10:00:00)";
    private static final String INPUT_JSON = "{\"hangoutId\":\"123\",\"type\":\"REMINDER\"}";
    private static final String QUEUE_ARN = "arn:aws:sqs:us-west-2:123456789:reminder-queue";
    private static final String ROLE_ARN = "arn:aws:iam::123456789:role/scheduler-role";
    private static final String DLQ_ARN = "arn:aws:sqs:us-west-2:123456789:reminder-dlq";
    private static final String GROUP_NAME = "hangout-reminders";

    @Mock
    private SchedulerClient schedulerClient;

    @Mock
    private SchedulerConfig config;

    private EventBridgeSchedulerClient client;

    @BeforeEach
    void setUp() {
        lenient().when(config.isSchedulerEnabled()).thenReturn(true);
        lenient().when(config.getQueueArn()).thenReturn(QUEUE_ARN);
        lenient().when(config.getRoleArn()).thenReturn(ROLE_ARN);
        lenient().when(config.getDlqArn()).thenReturn(DLQ_ARN);
        lenient().when(config.getGroupName()).thenReturn(GROUP_NAME);

        client = new EventBridgeSchedulerClient(schedulerClient, config);
    }

    @Nested
    class IsEnabled {

        @Test
        void isEnabled_WhenConfigEnabledAndClientExists_ReturnsTrue() {
            // Given
            when(config.isSchedulerEnabled()).thenReturn(true);

            // When
            boolean result = client.isEnabled();

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void isEnabled_WhenConfigDisabled_ReturnsFalse() {
            // Given
            when(config.isSchedulerEnabled()).thenReturn(false);

            // When
            boolean result = client.isEnabled();

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void isEnabled_WhenClientNull_ReturnsFalse() {
            // Given
            EventBridgeSchedulerClient clientWithNullScheduler =
                    new EventBridgeSchedulerClient(null, config);
            when(config.isSchedulerEnabled()).thenReturn(true);

            // When
            boolean result = clientWithNullScheduler.isEnabled();

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class CreateOrUpdateScheduleCreateFirstStrategy {

        @Test
        void createOrUpdateSchedule_WhenDisabled_DoesNothing() {
            // Given
            when(config.isSchedulerEnabled()).thenReturn(false);

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then
            verify(schedulerClient, never()).createSchedule(any(CreateScheduleRequest.class));
            verify(schedulerClient, never()).updateSchedule(any(UpdateScheduleRequest.class));
        }

        @Test
        void createOrUpdateSchedule_CreateFirst_WhenScheduleDoesNotExist_CreatesSuccessfully() {
            // Given
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());
            verify(schedulerClient, never()).updateSchedule(any(UpdateScheduleRequest.class));

            CreateScheduleRequest request = captor.getValue();
            assertThat(request.name()).isEqualTo(SCHEDULE_NAME);
            assertThat(request.scheduleExpression()).isEqualTo(SCHEDULE_EXPRESSION);
            assertThat(request.groupName()).isEqualTo(GROUP_NAME);
        }

        @Test
        void createOrUpdateSchedule_CreateFirst_WhenConflict_FallsBackToUpdate() {
            // Given
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenThrow(ConflictException.builder().message("Schedule already exists").build());
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenReturn(UpdateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then
            ArgumentCaptor<CreateScheduleRequest> createCaptor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            ArgumentCaptor<UpdateScheduleRequest> updateCaptor = ArgumentCaptor.forClass(UpdateScheduleRequest.class);

            verify(schedulerClient).createSchedule(createCaptor.capture());
            verify(schedulerClient).updateSchedule(updateCaptor.capture());

            assertThat(createCaptor.getValue().name()).isEqualTo(SCHEDULE_NAME);
            assertThat(updateCaptor.getValue().name()).isEqualTo(SCHEDULE_NAME);
            assertThat(updateCaptor.getValue().scheduleExpression()).isEqualTo(SCHEDULE_EXPRESSION);
        }

        @Test
        void createOrUpdateSchedule_CreateFirst_WhenConflictThenNotFound_RetriesCreate() {
            // Given - create throws conflict, update throws not found, second create succeeds
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenThrow(ConflictException.builder().message("Schedule already exists").build())
                    .thenReturn(CreateScheduleResponse.builder().build());
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Schedule not found").build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then - sequence: create (fails) → update (fails) → create again (succeeds)
            verify(schedulerClient, times(2)).createSchedule(any(CreateScheduleRequest.class));
            verify(schedulerClient).updateSchedule(any(UpdateScheduleRequest.class));
        }
    }

    @Nested
    class CreateOrUpdateScheduleUpdateFirstStrategy {

        @Test
        void createOrUpdateSchedule_UpdateFirst_WhenScheduleExists_UpdatesSuccessfully() {
            // Given
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenReturn(UpdateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, true);

            // Then
            verify(schedulerClient).updateSchedule(any(UpdateScheduleRequest.class));
            verify(schedulerClient, never()).createSchedule(any(CreateScheduleRequest.class));
        }

        @Test
        void createOrUpdateSchedule_UpdateFirst_WhenNotFound_FallsBackToCreate() {
            // Given
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Schedule not found").build());
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, true);

            // Then
            verify(schedulerClient).updateSchedule(any(UpdateScheduleRequest.class));
            verify(schedulerClient).createSchedule(any(CreateScheduleRequest.class));
        }

        @Test
        void createOrUpdateSchedule_UpdateFirst_WhenNotFoundThenConflict_RetriesUpdate() {
            // Given - update throws not found, create throws conflict, second update succeeds
            when(schedulerClient.updateSchedule(any(UpdateScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Schedule not found").build())
                    .thenReturn(UpdateScheduleResponse.builder().build());
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenThrow(ConflictException.builder().message("Schedule already exists").build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, true);

            // Then - sequence: update (fails) → create (fails) → update again (succeeds)
            verify(schedulerClient, times(2)).updateSchedule(any(UpdateScheduleRequest.class));
            verify(schedulerClient).createSchedule(any(CreateScheduleRequest.class));
        }
    }

    @Nested
    class CreateOrUpdateScheduleTargetBuilding {

        @Test
        void createOrUpdateSchedule_BuildsTargetWithCorrectConfig() {
            // Given
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());

            Target target = captor.getValue().target();
            assertThat(target.arn()).isEqualTo(QUEUE_ARN);
            assertThat(target.roleArn()).isEqualTo(ROLE_ARN);
            assertThat(target.input()).isEqualTo(INPUT_JSON);
            assertThat(target.deadLetterConfig().arn()).isEqualTo(DLQ_ARN);
            assertThat(target.retryPolicy().maximumRetryAttempts()).isEqualTo(2);
            assertThat(target.retryPolicy().maximumEventAgeInSeconds()).isEqualTo(3600);
        }

        @Test
        void createOrUpdateSchedule_SetsActionAfterCompletionToDelete() {
            // Given
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());

            assertThat(captor.getValue().actionAfterCompletion()).isEqualTo(ActionAfterCompletion.DELETE);
        }

        @Test
        void createOrUpdateSchedule_SetsFlexibleTimeWindowOff() {
            // Given
            when(schedulerClient.createSchedule(any(CreateScheduleRequest.class)))
                    .thenReturn(CreateScheduleResponse.builder().build());

            // When
            client.createOrUpdateSchedule(SCHEDULE_NAME, SCHEDULE_EXPRESSION, INPUT_JSON, false);

            // Then
            ArgumentCaptor<CreateScheduleRequest> captor = ArgumentCaptor.forClass(CreateScheduleRequest.class);
            verify(schedulerClient).createSchedule(captor.capture());

            assertThat(captor.getValue().flexibleTimeWindow().mode()).isEqualTo(FlexibleTimeWindowMode.OFF);
        }
    }

    @Nested
    class DeleteSchedule {

        @Test
        void deleteSchedule_WhenDisabled_DoesNothing() {
            // Given
            when(config.isSchedulerEnabled()).thenReturn(false);

            // When
            client.deleteSchedule(SCHEDULE_NAME);

            // Then
            verify(schedulerClient, never()).deleteSchedule(any(DeleteScheduleRequest.class));
        }

        @Test
        void deleteSchedule_WhenScheduleExists_DeletesSuccessfully() {
            // Given
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenReturn(DeleteScheduleResponse.builder().build());

            // When
            client.deleteSchedule(SCHEDULE_NAME);

            // Then
            ArgumentCaptor<DeleteScheduleRequest> captor = ArgumentCaptor.forClass(DeleteScheduleRequest.class);
            verify(schedulerClient).deleteSchedule(captor.capture());

            assertThat(captor.getValue().name()).isEqualTo(SCHEDULE_NAME);
            assertThat(captor.getValue().groupName()).isEqualTo(GROUP_NAME);
        }

        @Test
        void deleteSchedule_WhenScheduleNotFound_DoesNotThrow() {
            // Given
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenThrow(ResourceNotFoundException.builder().message("Schedule not found").build());

            // When / Then - no exception thrown
            client.deleteSchedule(SCHEDULE_NAME);

            verify(schedulerClient).deleteSchedule(any(DeleteScheduleRequest.class));
        }

        @Test
        void deleteSchedule_WhenOtherError_PropagatesException() {
            // Given
            InternalServerException internalServerException = InternalServerException.builder()
                    .message("Internal server error")
                    .build();
            when(schedulerClient.deleteSchedule(any(DeleteScheduleRequest.class)))
                    .thenThrow(internalServerException);

            // When / Then
            assertThatThrownBy(() -> client.deleteSchedule(SCHEDULE_NAME))
                    .isInstanceOf(InternalServerException.class)
                    .hasMessageContaining("Internal server error");
        }
    }
}
