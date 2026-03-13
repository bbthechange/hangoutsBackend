package com.bbthechange.inviter.task;

import com.bbthechange.inviter.model.TimeSuggestion;
import com.bbthechange.inviter.model.TimeSuggestionStatus;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.impl.TimeSuggestionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scheduled task that runs hourly to auto-adopt time suggestions that have
 * passed their adoption window without a competing suggestion.
 *
 * Disabled by default — enable with:
 *   time-suggestion.auto-adoption.enabled=true
 *
 * Silence = consent rules (spec section 8):
 *   1. Single suggestion + ≥1 supporter + no competition → adopt after shortWindowHours (default 24h).
 *   2. Single suggestion + 0 votes      + no competition → adopt after longWindowHours  (default 48h).
 *   3. Multiple competing suggestions                    → leave as poll; skip.
 */
@Component
@ConditionalOnProperty(name = "time-suggestion.auto-adoption.enabled", havingValue = "true")
public class TimeSuggestionAutoAdoptionTask {

    private static final Logger logger = LoggerFactory.getLogger(TimeSuggestionAutoAdoptionTask.class);

    private static final String TABLE_NAME = "InviterTable";

    /** Hours before a supported (≥1 vote) suggestion with no competition is auto-adopted. */
    @Value("${time-suggestion.auto-adoption.short-window-hours:24}")
    private int shortWindowHours;

    /** Hours before a zero-vote suggestion with no competition is auto-adopted. */
    @Value("${time-suggestion.auto-adoption.long-window-hours:48}")
    private int longWindowHours;

    private final DynamoDbClient dynamoDbClient;
    private final TimeSuggestionServiceImpl timeSuggestionService;

    @Autowired
    public TimeSuggestionAutoAdoptionTask(DynamoDbClient dynamoDbClient,
                                          TimeSuggestionServiceImpl timeSuggestionService) {
        this.dynamoDbClient = dynamoDbClient;
        this.timeSuggestionService = timeSuggestionService;
    }

    /**
     * Run hourly. Scan for ACTIVE time suggestions and evaluate each hangout for adoption.
     */
    @Scheduled(fixedDelayString = "${time-suggestion.auto-adoption.interval-ms:3600000}")
    public void runAutoAdoption() {
        logger.info("TimeSuggestionAutoAdoptionTask starting (shortWindow={}h, longWindow={}h)",
                shortWindowHours, longWindowHours);

        try {
            Set<String> hangoutIds = scanForHangoutsWithActiveSuggestions();
            if (hangoutIds.isEmpty()) {
                logger.debug("No hangouts with active time suggestions found");
                return;
            }

            logger.info("Found {} hangout(s) with active time suggestions", hangoutIds.size());
            for (String hangoutId : hangoutIds) {
                try {
                    timeSuggestionService.adoptForHangout(hangoutId, shortWindowHours, longWindowHours);
                } catch (Exception e) {
                    logger.warn("Error evaluating time suggestions for hangout {}: {}", hangoutId, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("TimeSuggestionAutoAdoptionTask failed: {}", e.getMessage(), e);
        }

        logger.info("TimeSuggestionAutoAdoptionTask completed");
    }

    /**
     * Scan InviterTable for items where itemType = TIME_SUGGESTION and status = ACTIVE.
     * Returns the set of hangout IDs (extracted from pk = EVENT#{hangoutId}).
     *
     * This scan is acceptable because:
     *   a) The task only runs when explicitly enabled (disabled by default).
     *   b) Active time suggestions are expected to be small in number and short-lived.
     *
     * A production-scale alternative would use a GSI on (itemType, status).
     */
    private Set<String> scanForHangoutsWithActiveSuggestions() {
        Set<String> hangoutIds = new HashSet<>();

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":itemType", AttributeValue.builder().s("TIME_SUGGESTION").build());
        expressionValues.put(":status", AttributeValue.builder().s(TimeSuggestionStatus.ACTIVE.name()).build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(TABLE_NAME)
                .filterExpression("itemType = :itemType AND #status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(expressionValues)
                .build();

        ScanResponse response = dynamoDbClient.scan(scanRequest);
        for (Map<String, AttributeValue> item : response.items()) {
            AttributeValue pkAttr = item.get("pk");
            if (pkAttr != null && pkAttr.s() != null && pkAttr.s().startsWith("EVENT#")) {
                String hangoutId = pkAttr.s().substring("EVENT#".length());
                hangoutIds.add(hangoutId);
            }
        }

        // Handle pagination
        while (response.hasLastEvaluatedKey()) {
            scanRequest = ScanRequest.builder()
                    .tableName(TABLE_NAME)
                    .filterExpression("itemType = :itemType AND #status = :status")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(expressionValues)
                    .exclusiveStartKey(response.lastEvaluatedKey())
                    .build();

            response = dynamoDbClient.scan(scanRequest);
            for (Map<String, AttributeValue> item : response.items()) {
                AttributeValue pkAttr = item.get("pk");
                if (pkAttr != null && pkAttr.s() != null && pkAttr.s().startsWith("EVENT#")) {
                    String hangoutId = pkAttr.s().substring("EVENT#".length());
                    hangoutIds.add(hangoutId);
                }
            }
        }

        return hangoutIds;
    }
}
