package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.bbthechange.inviter.model.IdeaNotificationBatch;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.repository.IdeaNotificationBatchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdeaNotificationBatchServiceTest {

    @Mock private IdeaNotificationBatchRepository batchRepository;
    @Mock private IdeaListRepository ideaListRepository;
    @Mock private NotificationService notificationService;
    @Mock private EventBridgeSchedulerClient eventBridgeClient;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    private IdeaNotificationBatchService service;

    private static final String GROUP_ID = "00000000-0000-0000-0000-000000000010";
    private static final String LIST_ID = "00000000-0000-0000-0000-000000000020";
    private static final String LIST_NAME = "Restaurants";
    private static final String ADDER_ID = "00000000-0000-0000-0000-000000000030";
    private static final String IDEA_NAME = "Sushi Place";

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        ObjectMapper objectMapper = new ObjectMapper();
        service = new IdeaNotificationBatchService(
                batchRepository, ideaListRepository, notificationService,
                eventBridgeClient, meterRegistry, objectMapper, null);
    }

    @Nested
    class RecordIdeaAdded {

        @Test
        void recordIdeaAdded_firstIdea_createsBatchAndSchedule() {
            // Given
            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.empty());

            // When
            service.recordIdeaAdded(GROUP_ID, LIST_ID, LIST_NAME, ADDER_ID, IDEA_NAME);

            // Then
            ArgumentCaptor<IdeaNotificationBatch> captor = ArgumentCaptor.forClass(IdeaNotificationBatch.class);
            verify(batchRepository).save(captor.capture());
            IdeaNotificationBatch saved = captor.getValue();

            assertThat(saved.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(saved.getListId()).isEqualTo(LIST_ID);
            assertThat(saved.getAdderId()).isEqualTo(ADDER_ID);
            assertThat(saved.getListName()).isEqualTo(LIST_NAME);
            assertThat(saved.getIdeaNames()).containsExactly(IDEA_NAME);
            assertThat(saved.getWindowStart()).isNotNull();
            assertThat(saved.getFireAt()).isEqualTo(saved.getWindowStart() + 90_000);
            assertThat(saved.getCapAt()).isEqualTo(saved.getWindowStart() + 300_000);
            assertThat(saved.getScheduleName()).isNotNull().startsWith("idea-batch-");

            verify(eventBridgeClient).createOrUpdateSchedule(
                    eq(saved.getScheduleName()), anyString(), anyString(), eq(false));
        }

        @Test
        void recordIdeaAdded_subsequentIdea_appendsToBatchAndUpdatesSchedule() {
            // Given
            long windowStart = System.currentTimeMillis() - 30_000; // started 30s ago
            IdeaNotificationBatch existing = new IdeaNotificationBatch(GROUP_ID, LIST_ID, ADDER_ID);
            existing.setIdeaNames(List.of("First Idea"));
            existing.setListName(LIST_NAME);
            existing.setWindowStart(windowStart);
            existing.setFireAt(windowStart + 90_000);
            existing.setCapAt(windowStart + 300_000);
            existing.setScheduleName("idea-batch-existing");

            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.of(existing));

            // When
            service.recordIdeaAdded(GROUP_ID, LIST_ID, LIST_NAME, ADDER_ID, "Second Idea");

            // Then
            ArgumentCaptor<IdeaNotificationBatch> captor = ArgumentCaptor.forClass(IdeaNotificationBatch.class);
            verify(batchRepository).save(captor.capture());
            IdeaNotificationBatch saved = captor.getValue();

            assertThat(saved.getIdeaNames()).containsExactly("First Idea", "Second Idea");

            verify(eventBridgeClient).createOrUpdateSchedule(
                    eq("idea-batch-existing"), anyString(), anyString(), eq(true));
        }

        @Test
        void recordIdeaAdded_atCap_fireAtClampedToCapAt() {
            // Given - batch started 4.5 minutes ago, so now+90s > capAt
            long windowStart = System.currentTimeMillis() - 270_000; // 4.5 min ago
            long capAt = windowStart + 300_000; // 30s from now

            IdeaNotificationBatch existing = new IdeaNotificationBatch(GROUP_ID, LIST_ID, ADDER_ID);
            existing.setIdeaNames(List.of("First Idea"));
            existing.setListName(LIST_NAME);
            existing.setWindowStart(windowStart);
            existing.setFireAt(capAt);
            existing.setCapAt(capAt);
            existing.setScheduleName("idea-batch-existing");

            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.of(existing));

            // When
            service.recordIdeaAdded(GROUP_ID, LIST_ID, LIST_NAME, ADDER_ID, "Late Idea");

            // Then
            ArgumentCaptor<IdeaNotificationBatch> captor = ArgumentCaptor.forClass(IdeaNotificationBatch.class);
            verify(batchRepository).save(captor.capture());
            IdeaNotificationBatch saved = captor.getValue();

            // fireAt should be clamped to capAt, not now+90s
            assertThat(saved.getFireAt()).isEqualTo(capAt);
        }

        @Test
        void recordIdeaAdded_eventBridgeFails_batchStillSaved() {
            // Given
            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.empty());
            doThrow(new RuntimeException("EventBridge unavailable"))
                    .when(eventBridgeClient).createOrUpdateSchedule(anyString(), anyString(), anyString(), eq(false));

            // When
            service.recordIdeaAdded(GROUP_ID, LIST_ID, LIST_NAME, ADDER_ID, IDEA_NAME);

            // Then - batch should still have been saved before the exception
            verify(batchRepository).save(any(IdeaNotificationBatch.class));
        }
    }

    @Nested
    class HandleIdeaAddBatch {

        @Test
        void handleIdeaAddBatch_validBatch_sendsAndDeletes() {
            // Given
            IdeaNotificationBatch batch = new IdeaNotificationBatch(GROUP_ID, LIST_ID, ADDER_ID);
            batch.setListName(LIST_NAME);
            batch.setIdeaNames(List.of("Idea A", "Idea B"));

            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.of(batch));
            when(ideaListRepository.ideaListExists(GROUP_ID, LIST_ID)).thenReturn(true);

            // When
            service.handleIdeaAddBatch(GROUP_ID, LIST_ID, ADDER_ID);

            // Then
            verify(notificationService).notifyIdeasAdded(
                    GROUP_ID, LIST_ID, LIST_NAME, ADDER_ID, List.of("Idea A", "Idea B"));
            verify(batchRepository).delete(GROUP_ID, LIST_ID, ADDER_ID);
            verify(meterRegistry).counter("idea_batch_notification_total", "status", "sent");
        }

        @Test
        void handleIdeaAddBatch_batchNotFound_acknowledgesGracefully() {
            // Given
            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.empty());

            // When
            service.handleIdeaAddBatch(GROUP_ID, LIST_ID, ADDER_ID);

            // Then
            verify(notificationService, never()).notifyIdeasAdded(anyString(), anyString(), anyString(), anyString(), any());
            verify(batchRepository, never()).delete(anyString(), anyString(), anyString());
            verify(meterRegistry).counter("idea_batch_notification_total", "status", "not_found");
        }

        @Test
        void handleIdeaAddBatch_listDeleted_discardsAndDeletesBatch() {
            // Given
            IdeaNotificationBatch batch = new IdeaNotificationBatch(GROUP_ID, LIST_ID, ADDER_ID);
            batch.setListName(LIST_NAME);
            batch.setIdeaNames(List.of("Idea A"));

            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.of(batch));
            when(ideaListRepository.ideaListExists(GROUP_ID, LIST_ID)).thenReturn(false);

            // When
            service.handleIdeaAddBatch(GROUP_ID, LIST_ID, ADDER_ID);

            // Then
            verify(notificationService, never()).notifyIdeasAdded(anyString(), anyString(), anyString(), anyString(), any());
            verify(batchRepository).delete(GROUP_ID, LIST_ID, ADDER_ID);
            verify(meterRegistry).counter("idea_batch_notification_total", "status", "list_deleted");
        }

        @Test
        void handleIdeaAddBatch_notificationFails_errorMetricFires() {
            // Given
            IdeaNotificationBatch batch = new IdeaNotificationBatch(GROUP_ID, LIST_ID, ADDER_ID);
            batch.setListName(LIST_NAME);
            batch.setIdeaNames(List.of("Idea A"));

            when(batchRepository.findBatch(GROUP_ID, LIST_ID, ADDER_ID)).thenReturn(Optional.of(batch));
            when(ideaListRepository.ideaListExists(GROUP_ID, LIST_ID)).thenReturn(true);
            doThrow(new RuntimeException("Push failed"))
                    .when(notificationService).notifyIdeasAdded(anyString(), anyString(), anyString(), anyString(), any());

            // When
            service.handleIdeaAddBatch(GROUP_ID, LIST_ID, ADDER_ID);

            // Then - the implementation wraps in try/catch so delete is skipped on exception
            // but the error metric should fire
            verify(batchRepository, never()).delete(anyString(), anyString(), anyString());
            verify(meterRegistry).counter("idea_batch_notification_total", "status", "error");
        }
    }
}
