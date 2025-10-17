package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.function.Consumer;

/**
 * Shared service for updating HangoutPointer records with optimistic locking retry logic.
 *
 * This service provides a centralized implementation of the read-modify-write pattern
 * with automatic retry on version conflicts. All pointer updates should go through this
 * service to ensure consistent behavior and data integrity.
 */
@Service
public class PointerUpdateService {

    private static final Logger logger = LoggerFactory.getLogger(PointerUpdateService.class);

    // Optimistic locking retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 50;
    private static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

    private final GroupRepository groupRepository;

    @Autowired
    public PointerUpdateService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    /**
     * Update a single pointer with optimistic locking retry logic.
     * Handles version conflicts by re-fetching the pointer and retrying.
     *
     * This method implements the read-modify-write pattern:
     * 1. Fetch the pointer (includes current version)
     * 2. Apply updates via the provided lambda function
     * 3. Save the pointer (DynamoDB checks version automatically)
     * 4. If version conflict occurs, retry with exponential backoff
     *
     * @param groupId The group ID
     * @param hangoutId The hangout ID
     * @param updateFunction The function that applies updates to the pointer
     * @param updateType Description of what's being updated (for logging)
     */
    public void updatePointerWithRetry(String groupId, String hangoutId,
                                       Consumer<HangoutPointer> updateFunction,
                                       String updateType) {
        int attempt = 0;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                // Fetch pointer with current version
                HangoutPointer pointer = groupRepository.findHangoutPointer(groupId, hangoutId)
                    .orElse(null);

                if (pointer == null) {
                    logger.warn("Pointer not found for group {} and hangout {} during {} update",
                        groupId, hangoutId, updateType);
                    return; // No pointer to update
                }

                // Apply updates via lambda function
                updateFunction.accept(pointer);

                // Save with optimistic locking (version checked automatically by DynamoDB)
                groupRepository.saveHangoutPointer(pointer);

                logger.debug("Updated {} on pointer for group {} and hangout {} (attempt {})",
                    updateType, groupId, hangoutId, attempt + 1);
                return; // Success

            } catch (ConditionalCheckFailedException e) {
                // Version conflict - someone else updated the pointer concurrently
                attempt++;
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    logger.error("Failed to update pointer {} for group {} and hangout {} after {} attempts due to version conflict",
                        updateType, groupId, hangoutId, MAX_RETRY_ATTEMPTS);
                    // Continue with other pointers even if this one fails
                    return;
                }

                logger.debug("Version conflict updating pointer {} for group {} and hangout {}, retrying (attempt {})",
                    updateType, groupId, hangoutId, attempt);

                // Exponential backoff before retry
                try {
                    Thread.sleep(delayMs);
                    delayMs = (long) (delayMs * RETRY_BACKOFF_MULTIPLIER);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted during retry backoff for pointer update");
                    return;
                }

            } catch (Exception e) {
                // Non-version error - give up immediately
                logger.error("Failed to update pointer {} for group {} and hangout {}: {}",
                    updateType, groupId, hangoutId, e.getMessage());
                // Continue with other pointers even if one fails
                return;
            }
        }
    }
}
