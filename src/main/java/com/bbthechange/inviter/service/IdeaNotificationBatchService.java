package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.bbthechange.inviter.listener.ScheduledEventListener;
import com.bbthechange.inviter.model.IdeaNotificationBatch;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.repository.IdeaNotificationBatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages batched idea addition notifications.
 *
 * When a user adds ideas to a list, notifications are batched using a quiet window:
 * - First idea: 90-second window opened, EventBridge schedule created
 * - Subsequent ideas: window extended by 90 seconds (capped at 5 minutes from first idea)
 * - When schedule fires: SQS message triggers notification to all group members
 *
 * This prevents notification spam when a user adds multiple ideas in quick succession.
 */
@Service
public class IdeaNotificationBatchService implements ScheduledEventListener.IdeaAddBatchHandler {

    private static final Logger logger = LoggerFactory.getLogger(IdeaNotificationBatchService.class);

    private static final long WINDOW_DURATION_MS = 90_000;       // 90 seconds
    private static final long MAX_WINDOW_DURATION_MS = 300_000;  // 5 minutes

    private static final DateTimeFormatter SCHEDULE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final IdeaNotificationBatchRepository batchRepository;
    private final IdeaListRepository ideaListRepository;
    private final NotificationService notificationService;
    private final EventBridgeSchedulerClient eventBridgeClient;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    @Autowired
    public IdeaNotificationBatchService(IdeaNotificationBatchRepository batchRepository,
                                         IdeaListRepository ideaListRepository,
                                         NotificationService notificationService,
                                         EventBridgeSchedulerClient eventBridgeClient,
                                         MeterRegistry meterRegistry,
                                         ObjectMapper objectMapper,
                                         @Autowired(required = false) ScheduledEventListener listener) {
        this.batchRepository = batchRepository;
        this.ideaListRepository = ideaListRepository;
        this.notificationService = notificationService;
        this.eventBridgeClient = eventBridgeClient;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;

        // Register as handler for IDEA_ADD_BATCH messages
        if (listener != null) {
            listener.setIdeaAddBatchHandler(this);
            logger.info("Registered IdeaNotificationBatchService as IDEA_ADD_BATCH handler");
        }
    }

    /**
     * Record that an idea was added and manage the batch notification window.
     * Called from IdeaListServiceImpl.addIdeaToList() in fire-and-forget fashion.
     */
    public void recordIdeaAdded(String groupId, String listId, String listName,
                                 String adderId, String ideaName) {
        Optional<IdeaNotificationBatch> existingOpt = batchRepository.findBatch(groupId, listId, adderId);

        if (existingOpt.isEmpty()) {
            // First idea — create batch + schedule
            long now = Instant.now().toEpochMilli();
            long fireAt = now + WINDOW_DURATION_MS;
            long capAt = now + MAX_WINDOW_DURATION_MS;

            IdeaNotificationBatch batch = new IdeaNotificationBatch(groupId, listId, adderId);
            batch.setIdeaNames(List.of(ideaName));
            batch.setListName(listName);
            batch.setWindowStart(now);
            batch.setFireAt(fireAt);
            batch.setCapAt(capAt);

            String scheduleName = generateBatchScheduleName(groupId, listId, adderId);
            batch.setScheduleName(scheduleName);
            batch.setExpiryDate((capAt / 1000) + 3600); // capAt + 1 hour, in epoch seconds for DynamoDB TTL

            batchRepository.save(batch);

            try {
                String scheduleExpression = buildScheduleExpression(fireAt);
                String inputJson = buildInputJson(groupId, listId, adderId);
                eventBridgeClient.createOrUpdateSchedule(scheduleName, scheduleExpression, inputJson, false);
            } catch (Exception e) {
                logger.error("Failed to create EventBridge schedule for idea batch: group={}, list={}, adder={}, schedule={}",
                        groupId, listId, adderId, scheduleName, e);
            }

            logger.info("Created idea notification batch: group={}, list={}, adder={}, schedule={}",
                    groupId, listId, adderId, scheduleName);
        } else {
            // Subsequent idea — append + extend window
            IdeaNotificationBatch batch = existingOpt.get();
            List<String> names = new ArrayList<>(batch.getIdeaNames());
            names.add(ideaName);
            batch.setIdeaNames(names);

            long now = Instant.now().toEpochMilli();
            long newFireAt = Math.min(now + WINDOW_DURATION_MS, batch.getCapAt());
            batch.setFireAt(newFireAt);

            batchRepository.save(batch);

            try {
                String scheduleExpression = buildScheduleExpression(newFireAt);
                String inputJson = buildInputJson(groupId, listId, adderId);
                eventBridgeClient.createOrUpdateSchedule(
                        batch.getScheduleName(), scheduleExpression, inputJson, true);
            } catch (Exception e) {
                logger.error("Failed to update EventBridge schedule for idea batch: group={}, list={}, adder={}, schedule={}",
                        groupId, listId, adderId, batch.getScheduleName(), e);
            }

            logger.info("Updated idea notification batch: group={}, list={}, adder={}, ideas={}, schedule={}",
                    groupId, listId, adderId, names.size(), batch.getScheduleName());
        }
    }

    /**
     * Handle an IDEA_ADD_BATCH message from SQS when the batch window fires.
     * Called by ScheduledEventListener.
     */
    @Override
    public void handleIdeaAddBatch(String groupId, String listId, String adderId) {
        try {
            // 1. Load batch record
            Optional<IdeaNotificationBatch> batchOpt = batchRepository.findBatch(groupId, listId, adderId);
            if (batchOpt.isEmpty()) {
                logger.info("Batch record not found (TTL expired or already processed): group={}, list={}, adder={}",
                        groupId, listId, adderId);
                meterRegistry.counter("idea_batch_notification_total", "status", "not_found").increment();
                return;
            }

            IdeaNotificationBatch batch = batchOpt.get();

            // 2. Validate list still exists
            if (!ideaListRepository.ideaListExists(groupId, listId)) {
                logger.info("List no longer exists, discarding batch: group={}, list={}", groupId, listId);
                batchRepository.delete(groupId, listId, adderId);
                meterRegistry.counter("idea_batch_notification_total", "status", "list_deleted").increment();
                return;
            }

            // 3. Send notification
            notificationService.notifyIdeasAdded(
                    groupId, listId, batch.getListName(), adderId, batch.getIdeaNames());

            // 4. Clean up batch record
            batchRepository.delete(groupId, listId, adderId);

            meterRegistry.counter("idea_batch_notification_total", "status", "sent").increment();
            logger.info("Sent batched idea notification: group={}, list={}, adder={}, ideaCount={}",
                    groupId, listId, adderId, batch.getIdeaNames().size());

        } catch (Exception e) {
            logger.error("Error processing idea add batch: group={}, list={}, adder={}",
                    groupId, listId, adderId, e);
            meterRegistry.counter("idea_batch_notification_total", "status", "error").increment();
        }
    }

    private String generateBatchScheduleName(String groupId, String listId, String adderId) {
        String input = groupId + "|" + listId + "|" + adderId;
        String hash = UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
        return "idea-batch-" + hash.substring(0, 32);
    }

    private String buildScheduleExpression(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        String formatted = SCHEDULE_TIME_FORMATTER.format(instant);
        return "at(" + formatted + ")";
    }

    private String buildInputJson(String groupId, String listId, String adderId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "IDEA_ADD_BATCH");
        node.put("groupId", groupId);
        node.put("listId", listId);
        node.put("adderId", adderId);
        return node.toString();
    }
}
