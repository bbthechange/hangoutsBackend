package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.HangoutAttribute;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for generic attribute operations in HangoutRepositoryImpl.
 *
 * Coverage:
 * - Attribute retrieval (by ID, by hangout)
 * - Attribute persistence (save, update, validation)
 * - Attribute deletion
 */
class HangoutRepositoryAttributesTest extends HangoutRepositoryTestBase {

    @Test
    void findAttributeById_WithExistingAttribute_ReturnsOptionalWithAttribute() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();

        HangoutAttribute expectedAttribute = new HangoutAttribute(hangoutId, attributeId, "location", "Test Location");
        when(inviterTable.getItem(any(java.util.function.Consumer.class))).thenReturn(expectedAttribute);

        // When
        Optional<HangoutAttribute> result = repository.findAttributeById(hangoutId, attributeId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAttributeId()).isEqualTo(attributeId);
        assertThat(result.get().getHangoutId()).isEqualTo(hangoutId);
        assertThat(result.get().getAttributeName()).isEqualTo("location");
        assertThat(result.get().getStringValue()).isEqualTo("Test Location");
    }

    @Test
    void findAttributeById_WithNonExistentAttribute_ReturnsEmptyOptional() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();

        when(inviterTable.getItem(any(java.util.function.Consumer.class))).thenReturn(null);

        // When
        Optional<HangoutAttribute> result = repository.findAttributeById(hangoutId, attributeId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findAttributesByHangoutId_WithMultipleAttributes_ReturnsAllAttributes() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String attributeId1 = UUID.randomUUID().toString();
        String attributeId2 = UUID.randomUUID().toString();

        List<HangoutAttribute> expectedAttributes = Arrays.asList(
            new HangoutAttribute(hangoutId, attributeId1, "location", "Test Location"),
            new HangoutAttribute(hangoutId, attributeId2, "maxParticipants", "50")
        );

        PageIterable<HangoutAttribute> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(() -> expectedAttributes.iterator());
        when(inviterTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<HangoutAttribute> result = repository.findAttributesByHangoutId(hangoutId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttributeName()).isEqualTo("location");
        assertThat(result.get(1).getAttributeName()).isEqualTo("maxParticipants");
    }

    @Test
    void findAttributesByHangoutId_WithNoAttributes_ReturnsEmptyList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        PageIterable<HangoutAttribute> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(Collections::emptyIterator);
        when(inviterTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<HangoutAttribute> result = repository.findAttributesByHangoutId(hangoutId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void saveAttribute_WithValidAttribute_SavesSuccessfully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();
        HangoutAttribute attribute = new HangoutAttribute(hangoutId, attributeId, "location", "Test Location");

        // When
        HangoutAttribute result = repository.saveAttribute(attribute);

        // Then
        assertThat(result).isSameAs(attribute);
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(inviterTable).putItem(attribute);
    }

    @Test
    void saveAttribute_WithInvalidAttribute_ThrowsIllegalArgumentException() {
        // Given
        HangoutAttribute invalidAttribute = new HangoutAttribute();
        invalidAttribute.setAttributeName(null); // Invalid

        // When/Then
        assertThatThrownBy(() -> repository.saveAttribute(invalidAttribute))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveAttribute_WithNullStringValue_SavesSuccessfully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();
        HangoutAttribute attribute = new HangoutAttribute(hangoutId, attributeId, "notes", null);

        // When
        HangoutAttribute result = repository.saveAttribute(attribute);

        // Then
        assertThat(result).isSameAs(attribute);
        assertThat(result.getStringValue()).isNull();
        verify(inviterTable).putItem(attribute);
    }

    @Test
    void deleteAttribute_WithValidIds_DeletesSuccessfully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();

        // When
        repository.deleteAttribute(hangoutId, attributeId);

        // Then
        verify(inviterTable).deleteItem(any(java.util.function.Consumer.class));
    }
}
