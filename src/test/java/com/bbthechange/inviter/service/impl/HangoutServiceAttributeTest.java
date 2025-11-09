package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateAttributeRequest;
import com.bbthechange.inviter.dto.UpdateAttributeRequest;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutAttribute;
import com.bbthechange.inviter.model.HangoutPointer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout attribute operations (create, update, delete).
 *
 * Covers:
 * - Creating attributes with pointer updates
 * - Updating attributes with pointer propagation
 * - Deleting attributes and updating all pointers
 * - Edge cases like missing pointers and empty attribute lists
 */
class HangoutServiceAttributeTest extends HangoutServiceTestBase {

    @Test
    void createAttribute_WithValidAttribute_ShouldUpdateAllPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateAttributeRequest request = new CreateAttributeRequest();
        request.setAttributeName("dress_code");
        request.setStringValue("casual");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));


        // Mock authorization - user in first group
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute creation
        HangoutAttribute newAttribute = new HangoutAttribute();
        newAttribute.setHangoutId(hangoutId);
        newAttribute.setAttributeName("dress_code");
        newAttribute.setStringValue("casual");
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(newAttribute);

        // Mock current attributes (after save)
        List<HangoutAttribute> updatedAttributes = List.of(newAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId))
            .thenReturn(List.of())  // First call: check for duplicates - empty
            .thenReturn(updatedAttributes);  // Second call: get updated list

        // When
        hangoutService.createAttribute(hangoutId, request, userId);

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify both pointers were updated with new attributes list via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void createAttribute_WhenPointerNotFound_ShouldContinueWithOtherPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateAttributeRequest request = new CreateAttributeRequest();
        request.setAttributeName("music");
        request.setStringValue("jazz");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute creation
        HangoutAttribute newAttribute = new HangoutAttribute();
        newAttribute.setHangoutId(hangoutId);
        newAttribute.setAttributeName("music");
        newAttribute.setStringValue("jazz");
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(newAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId))
            .thenReturn(List.of())  // First call: check for duplicates - empty
            .thenReturn(List.of(newAttribute));  // Second call: get updated list

        // When
        assertThatCode(() -> hangoutService.createAttribute(hangoutId, request, userId))
            .doesNotThrowAnyException();

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify both groups were attempted to be updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void createAttribute_WithNoAssociatedGroups_ShouldNotUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateAttributeRequest request = new CreateAttributeRequest();
        request.setAttributeName("theme");
        request.setStringValue("retro");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>()); // No groups
        hangout.setVisibility(com.bbthechange.inviter.model.EventVisibility.PUBLIC); // Make it public so auth passes


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // Mock attribute creation
        HangoutAttribute newAttribute = new HangoutAttribute();
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(newAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        hangoutService.createAttribute(hangoutId, request, userId);

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify no pointer operations attempted
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void updateAttribute_WithNameAndValueChange_ShouldUpdateAllPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        UpdateAttributeRequest request = new UpdateAttributeRequest();
        request.setAttributeName("dress_code");
        request.setStringValue("formal");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock existing attribute
        HangoutAttribute existingAttribute = new HangoutAttribute();
        existingAttribute.setHangoutId(hangoutId);
        existingAttribute.setAttributeId(attributeId);
        existingAttribute.setAttributeName("dress_code");
        existingAttribute.setStringValue("casual");
        when(hangoutRepository.findAttributeById(hangoutId, attributeId)).thenReturn(Optional.of(existingAttribute));

        // Mock attribute update
        HangoutAttribute updatedAttribute = new HangoutAttribute();
        updatedAttribute.setHangoutId(hangoutId);
        updatedAttribute.setAttributeId(attributeId);
        updatedAttribute.setAttributeName("dress_code");
        updatedAttribute.setStringValue("formal");
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(updatedAttribute);

        // Mock attributes list after update
        List<HangoutAttribute> updatedAttributes = List.of(updatedAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(updatedAttributes);

        // When
        hangoutService.updateAttribute(hangoutId, attributeId, request, userId);

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(argThat(attr ->
            attr.getStringValue().equals("formal")
        ));

        // Verify pointer was updated with attributes
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void updateAttribute_WhenRepositoryFails_ShouldLogAndContinue() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        UpdateAttributeRequest request = new UpdateAttributeRequest();
        request.setAttributeName("theme");
        request.setStringValue("updated");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock existing attribute
        HangoutAttribute existingAttribute = new HangoutAttribute();
        existingAttribute.setHangoutId(hangoutId);
        existingAttribute.setAttributeId(attributeId);
        existingAttribute.setAttributeName("theme");
        existingAttribute.setStringValue("old");
        when(hangoutRepository.findAttributeById(hangoutId, attributeId)).thenReturn(Optional.of(existingAttribute));

        // Mock attribute update
        HangoutAttribute updatedAttribute = new HangoutAttribute();
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(updatedAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of(updatedAttribute));

        // Mock pointer retrieval
        HangoutPointer pointer1 = createTestHangoutPointer("11111111-1111-1111-1111-111111111111", hangoutId);
        HangoutPointer pointer2 = createTestHangoutPointer("22222222-2222-2222-2222-222222222222", hangoutId);

        // When
        assertThatCode(() -> hangoutService.updateAttribute(hangoutId, attributeId, request, userId))
            .doesNotThrowAnyException();

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify both pointers were updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void deleteAttribute_WithValidId_ShouldRemoveFromAllPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333"
        )));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute deletion
        doNothing().when(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Mock attributes list after deletion (one attribute remaining)
        HangoutAttribute remainingAttribute = new HangoutAttribute();
        remainingAttribute.setHangoutId(hangoutId);
        remainingAttribute.setAttributeName("other_attribute");
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of(remainingAttribute));

        // When
        hangoutService.deleteAttribute(hangoutId, attributeId, userId);

        // Then
        // Verify canonical attribute was deleted
        verify(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Verify all 3 pointers were updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("33333333-3333-3333-3333-333333333333"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void deleteAttribute_WithAllAttributesRemoved_ShouldSetEmptyList() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute deletion
        doNothing().when(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Mock empty attributes list after deletion (last attribute was deleted)
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        hangoutService.deleteAttribute(hangoutId, attributeId, userId);

        // Then
        // Verify canonical attribute was deleted
        verify(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Verify pointer was updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
    }
}
