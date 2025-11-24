package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateAttributeRequest;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for pointer update operations with optimistic locking retry logic.
 *
 * Covers:
 * - Pointer updates via PointerUpdateService
 * - Optimistic locking retry scenarios
 * - Interest level denormalization to pointers
 * - Complete hangout pointer resynchronization
 */
class HangoutServicePointerUpdateTest extends HangoutServiceTestBase {

    @Test
    void resyncHangoutPointers_IncludesInterestLevelsInUpdate() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String groupId = "11111111-1111-1111-1111-111111111111";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(List.of(groupId));

        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();

        InterestLevel interest1 = new InterestLevel(hangoutId, userId1, "User One", "GOING");
        InterestLevel interest2 = new InterestLevel(hangoutId, userId2, "User Two", "INTERESTED");

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(interest1, interest2), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        hangoutService.resyncHangoutPointers(hangoutId);

        // Then - verify pointerUpdateService was called with a lambda that sets interest levels
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("complete resync"));
    }

    @Nested
    class OptimisticLockingRetryTests {

        @Test
        void updatePointersWithAttributes_WithNoConflict_ShouldSucceedOnFirstAttempt() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);
            pointer.setVersion(1L);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Retry behavior is now tested in PointerUpdateServiceTest, not here.
        }

        @Test
        void updatePointersWithAttributes_WithConflictThenSuccess_ShouldRetryAndSucceed() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer1 = createTestHangoutPointer(groupId, hangoutId);
            pointer1.setVersion(1L);
            HangoutPointer pointer2 = createTestHangoutPointer(groupId, hangoutId);
            pointer2.setVersion(2L);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Retry behavior with conflicts is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithPersistentConflict_ShouldGiveUpAfterMaxRetries() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);
            pointer.setVersion(1L);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Max retry behavior is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithMultipleGroups_OnlyRetriesFailedPointer() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";
            String groupId3 = "33333333-3333-3333-3333-333333333333";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId1, groupId2, groupId3)));

            GroupMembership membership = createTestMembership(groupId1, userId, "Test Group");
            HangoutPointer pointer1 = createTestHangoutPointer(groupId1, hangoutId);
            HangoutPointer pointer2 = createTestHangoutPointer(groupId2, hangoutId);
            HangoutPointer pointer3 = createTestHangoutPointer(groupId3, hangoutId);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId1, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called for all groups
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(hangoutId), any(), eq("attributes"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(hangoutId), any(), eq("attributes"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(hangoutId), any(), eq("attributes"));

            // Note: Individual group retry behavior is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithNonVersionException_ShouldGiveUpImmediately() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Retry behavior for non-version exceptions is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithInterruptedException_ShouldStopRetrying() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: InterruptedException and retry behavior is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithPointerNotFound_ShouldSkipGracefully() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));
        }
    }
}
