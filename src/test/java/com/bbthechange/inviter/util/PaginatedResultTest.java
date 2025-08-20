package com.bbthechange.inviter.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PaginatedResult utility class.
 * Tests the basic functionality for paginated database query results.
 */
class PaginatedResultTest {

    @Test
    void constructor_WithValidData_SetsFieldsCorrectly() {
        // Given
        List<String> results = Arrays.asList("item1", "item2", "item3");
        String nextToken = "next-page-token";
        
        // When
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(results, nextToken);
        
        // Then
        assertThat(paginatedResult.getResults()).isEqualTo(results);
        assertThat(paginatedResult.getNextToken()).isEqualTo(nextToken);
    }
    
    @Test
    void hasMore_WithNonNullToken_ReturnsTrue() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(
            Arrays.asList("item1"), "next-token");
        
        // When & Then
        assertThat(paginatedResult.hasMore()).isTrue();
    }
    
    @Test
    void hasMore_WithNullToken_ReturnsFalse() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(
            Arrays.asList("item1"), null);
        
        // When & Then
        assertThat(paginatedResult.hasMore()).isFalse();
    }
    
    @Test
    void size_WithItems_ReturnsCorrectSize() {
        // Given
        List<String> results = Arrays.asList("item1", "item2", "item3");
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(results, null);
        
        // When & Then
        assertThat(paginatedResult.size()).isEqualTo(3);
    }
    
    @Test
    void size_WithEmptyList_ReturnsZero() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(Collections.emptyList(), null);
        
        // When & Then
        assertThat(paginatedResult.size()).isEqualTo(0);
    }
    
    @Test
    void size_WithNullList_ReturnsZero() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(null, null);
        
        // When & Then
        assertThat(paginatedResult.size()).isEqualTo(0);
    }
    
    @Test
    void isEmpty_WithEmptyList_ReturnsTrue() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(Collections.emptyList(), null);
        
        // When & Then
        assertThat(paginatedResult.isEmpty()).isTrue();
    }
    
    @Test
    void isEmpty_WithNullList_ReturnsTrue() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(null, null);
        
        // When & Then
        assertThat(paginatedResult.isEmpty()).isTrue();
    }
    
    @Test
    void isEmpty_WithItems_ReturnsFalse() {
        // Given
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(
            Arrays.asList("item1"), null);
        
        // When & Then
        assertThat(paginatedResult.isEmpty()).isFalse();
    }
    
    @Test
    void constructor_WithNullValues_HandlesGracefully() {
        // Given & When
        PaginatedResult<String> paginatedResult = new PaginatedResult<>(null, null);
        
        // Then
        assertThat(paginatedResult.getResults()).isNull();
        assertThat(paginatedResult.getNextToken()).isNull();
        assertThat(paginatedResult.hasMore()).isFalse();
        assertThat(paginatedResult.isEmpty()).isTrue();
        assertThat(paginatedResult.size()).isEqualTo(0);
    }
}