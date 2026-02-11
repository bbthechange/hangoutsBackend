package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.testutil.HangoutPointerTestBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PointerUpdateService.
 * Tests the optimistic locking retry logic for pointer updates.
 */
@ExtendWith(MockitoExtension.class)
class PointerUpdateServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private PointerUpdateService pointerUpdateService;

    private String groupId;
    private String hangoutId;
    private HangoutPointer pointer;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID().toString();
        hangoutId = UUID.randomUUID().toString();

        pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Test Hangout")
            .build();
    }

    // ============================================================================
    // SUCCESS SCENARIO TESTS
    // ============================================================================

    @Test
    void updatePointerWithRetry_WithSuccessOnFirstAttempt_ShouldUpdatePointer() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then
        verify(groupRepository).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            "Updated Title".equals(p.getTitle())
        ));
        assertThat(pointer.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void updatePointerWithRetry_WithUpdateFunction_ShouldApplyUpdatesCorrectly() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When - apply multiple updates via lambda
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, p -> {
            p.setTitle("New Title");
            p.setDescription("New Description");
            p.setParticipantCount(5);
        }, "multiple fields");

        // Then
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            "New Title".equals(p.getTitle()) &&
            "New Description".equals(p.getDescription()) &&
            p.getParticipantCount() == 5
        ));
    }

    // ============================================================================
    // VERSION CONFLICT RETRY TESTS
    // ============================================================================

    @Test
    void updatePointerWithRetry_WithVersionConflictThenSuccess_ShouldRetryAndSucceed() {
        // Given - first attempt fails with version conflict, second succeeds
        HangoutPointer freshPointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Fresh Title")
            .build();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer))
            .thenReturn(Optional.of(freshPointer));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .doNothing()
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should fetch pointer twice (initial + retry)
        verify(groupRepository, times(2)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class));

        // Verify the fresh pointer was updated on retry
        assertThat(freshPointer.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void updatePointerWithRetry_WithMultipleVersionConflicts_ShouldRetryMultipleTimes() {
        // Given - first two attempts fail, third succeeds
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .build();
        HangoutPointer pointer3 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .build();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer))
            .thenReturn(Optional.of(pointer2))
            .thenReturn(Optional.of(pointer3));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .doNothing()
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should fetch pointer three times
        verify(groupRepository, times(3)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(3)).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithPersistentVersionConflict_ShouldGiveUpAfterMaxRetries() {
        // Given - all attempts fail with version conflict
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should try MAX_RETRY_ATTEMPTS times (3)
        verify(groupRepository, times(3)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(3)).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithVersionConflict_ShouldUseExponentialBackoff() throws InterruptedException {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        long startTime = System.currentTimeMillis();
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then - should have delays: 50ms + 100ms = 150ms minimum
        // Allow some margin for test execution time
        assertThat(duration).isGreaterThanOrEqualTo(140);
        verify(groupRepository, times(3)).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithVersionConflict_ShouldRefetchPointerOnEachRetry() {
        // Given - simulate pointer being updated by another process
        HangoutPointer pointer1 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Version 1")
            .build();
        HangoutPointer pointer2 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Version 2")
            .build();
        HangoutPointer pointer3 = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Version 3")
            .build();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer1))
            .thenReturn(Optional.of(pointer2))
            .thenReturn(Optional.of(pointer3));

        AtomicInteger saveCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            int count = saveCount.incrementAndGet();
            if (count < 3) {
                throw ConditionalCheckFailedException.builder().message("Version conflict").build();
            }
            return null;
        }).when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setDescription("Updated Description"),
            "test update");

        // Then - each retry should get the latest version
        verify(groupRepository, times(3)).findHangoutPointer(groupId, hangoutId);
        assertThat(pointer1.getDescription()).isEqualTo("Updated Description");
        assertThat(pointer2.getDescription()).isEqualTo("Updated Description");
        assertThat(pointer3.getDescription()).isEqualTo("Updated Description");
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    void updatePointerWithRetry_WithNonVersionException_ShouldGiveUpImmediately() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(new RuntimeException("Database error"))
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should only try once, no retries
        verify(groupRepository, times(1)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(1)).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithPointerNotFound_ShouldSkipGracefully() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.empty());

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should fetch once and not attempt save
        verify(groupRepository).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, never()).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithInterruptedExceptionDuringBackoff_ShouldStopRetrying() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // Interrupt the thread before calling the method
        Thread.currentThread().interrupt();

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should stop on first retry attempt due to interrupt
        // First attempt + first retry (interrupted during backoff) = 2 fetches
        verify(groupRepository, atMost(2)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, atMost(2)).saveHangoutPointer(any(HangoutPointer.class));

        // Thread interrupt flag should be set
        assertThat(Thread.interrupted()).isTrue(); // Clears the flag
    }

    @Test
    void updatePointerWithRetry_WithFindRepositoryException_ShouldHandleGracefully() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenThrow(new RuntimeException("Database connection error"));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "test update");

        // Then - should catch exception and not attempt save
        verify(groupRepository).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, never()).saveHangoutPointer(any(HangoutPointer.class));
    }

    // ============================================================================
    // UPDATE FUNCTION TESTS
    // ============================================================================

    @Test
    void updatePointerWithRetry_WithComplexUpdateFunction_ShouldApplyAllChanges() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When - apply complex updates
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, p -> {
            p.setTitle("New Title");
            p.setDescription("New Description");
            p.setStatus("ACTIVE");
            p.setStartTimestamp(System.currentTimeMillis());
            p.setParticipantCount(10);
        }, "complex update");

        // Then
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            "New Title".equals(p.getTitle()) &&
            "New Description".equals(p.getDescription()) &&
            "ACTIVE".equals(p.getStatus()) &&
            p.getStartTimestamp() != null &&
            p.getParticipantCount() == 10
        ));
    }

    @Test
    void updatePointerWithRetry_WithNoOpUpdateFunction_ShouldStillSavePointer() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        String originalTitle = pointer.getTitle();

        // When - update function does nothing
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> { /* no-op */ },
            "no-op update");

        // Then - should still save (important for updating version even without changes)
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            originalTitle.equals(p.getTitle())
        ));
    }

    // ============================================================================
    // EDGE CASE TESTS
    // ============================================================================

    @Test
    void updatePointerWithRetry_WithNullGroupId_ShouldHandleGracefully() {
        // Given
        when(groupRepository.findHangoutPointer(null, hangoutId))
            .thenReturn(Optional.empty());

        // When
        pointerUpdateService.updatePointerWithRetry(null, hangoutId,
            p -> p.setTitle("Updated Title"),
            "null group test");

        // Then
        verify(groupRepository).findHangoutPointer(null, hangoutId);
        verify(groupRepository, never()).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithNullHangoutId_ShouldHandleGracefully() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, null))
            .thenReturn(Optional.empty());

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, null,
            p -> p.setTitle("Updated Title"),
            "null hangout test");

        // Then
        verify(groupRepository).findHangoutPointer(groupId, null);
        verify(groupRepository, never()).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void updatePointerWithRetry_WithEmptyUpdateType_ShouldStillWork() {
        // Given
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
            p -> p.setTitle("Updated Title"),
            "");

        // Then
        verify(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
    }

    // ============================================================================
    // UPSERT POINTER WITH RETRY TESTS
    // ============================================================================

    @Test
    void upsertPointerWithRetry_PointerExists_AppliesUpdateFunction() {
        // Given - pointer exists
        Hangout hangout = createTestHangout();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.upsertPointerWithRetry(groupId, hangoutId, hangout,
            p -> p.setTitle("Updated Title"),
            "test upsert");

        // Then - should use existing pointer, not create new
        verify(groupRepository).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            "Updated Title".equals(p.getTitle())
        ));
    }

    @Test
    void upsertPointerWithRetry_PointerMissing_CreatesViaFactory() {
        // Given - pointer does NOT exist
        Hangout hangout = createTestHangout();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.empty());
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.upsertPointerWithRetry(groupId, hangoutId, hangout,
            p -> { /* no additional updates */ },
            "test upsert");

        // Then - should create new pointer via factory and save
        verify(groupRepository).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            "ACTIVE".equals(p.getStatus()) &&
            "Test Hangout Title".equals(p.getTitle()) &&
            p.getParticipantCount() == 0
        ));
    }

    @Test
    void upsertPointerWithRetry_VersionConflict_Retries() {
        // Given - first attempt has version conflict, second succeeds
        Hangout hangout = createTestHangout();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .doNothing()
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.upsertPointerWithRetry(groupId, hangoutId, hangout,
            p -> p.setTitle("Updated Title"),
            "test upsert");

        // Then - should retry
        verify(groupRepository, times(2)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void upsertPointerWithRetry_PointerMissing_AppliesUpdateFunctionAfterCreation() {
        // Given - pointer does NOT exist
        Hangout hangout = createTestHangout();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.empty());
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When - apply additional update to the factory-created pointer
        pointerUpdateService.upsertPointerWithRetry(groupId, hangoutId, hangout,
            p -> p.setDescription("Custom description from lambda"),
            "test upsert");

        // Then - lambda should run on the newly created pointer
        verify(groupRepository).saveHangoutPointer(argThat(p ->
            "Custom description from lambda".equals(p.getDescription()) &&
            "ACTIVE".equals(p.getStatus()) // Factory sets this
        ));
    }

    @Test
    void upsertPointerWithRetry_WithPersistentVersionConflict_ShouldGiveUpAfterMaxRetries() {
        // Given - all attempts fail with version conflict
        Hangout hangout = createTestHangout();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(ConditionalCheckFailedException.builder().message("Version conflict").build())
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.upsertPointerWithRetry(groupId, hangoutId, hangout,
            p -> p.setTitle("Updated Title"),
            "test upsert");

        // Then - should try MAX_RETRY_ATTEMPTS times (3)
        verify(groupRepository, times(3)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(3)).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void upsertPointerWithRetry_WithNonVersionException_ShouldGiveUpImmediately() {
        // Given
        Hangout hangout = createTestHangout();

        when(groupRepository.findHangoutPointer(groupId, hangoutId))
            .thenReturn(Optional.of(pointer));

        doThrow(new RuntimeException("Database error"))
            .when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));

        // When
        pointerUpdateService.upsertPointerWithRetry(groupId, hangoutId, hangout,
            p -> p.setTitle("Updated Title"),
            "test upsert");

        // Then - should only try once, no retries
        verify(groupRepository, times(1)).findHangoutPointer(groupId, hangoutId);
        verify(groupRepository, times(1)).saveHangoutPointer(any(HangoutPointer.class));
    }

    private Hangout createTestHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout Title");
        hangout.setDescription("Test description");
        hangout.setStartTimestamp(1700000000L);
        hangout.setEndTimestamp(1700007200L);
        return hangout;
    }
}
