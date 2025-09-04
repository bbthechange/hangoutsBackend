package com.bbthechange.inviter.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for GroupFeedPaginationToken.
 * Tests encoding and decoding of pagination tokens.
 */
class GroupFeedPaginationTokenTest {

    private static final String EVENT_ID = "event-123-456";
    private static final Long TEST_TIMESTAMP = 1737023400L; // Unix timestamp

    @Test
    void encode_WithValidData_ReturnsBase64String() {
        // Given
        GroupFeedPaginationToken token = new GroupFeedPaginationToken(EVENT_ID, TEST_TIMESTAMP, true);
        
        // When
        String encoded = token.encode();
        
        // Then
        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEmpty();
        assertThat(encoded).doesNotContain(" "); // Base64 should not contain spaces
        assertThat(encoded).matches("^[A-Za-z0-9+/]*={0,2}$"); // Valid base64 pattern
    }
    
    @Test
    void decode_WithValidToken_ReturnsCorrectData() {
        // Given
        GroupFeedPaginationToken originalToken = new GroupFeedPaginationToken(EVENT_ID, TEST_TIMESTAMP, false);
        String encodedToken = originalToken.encode();
        
        // When
        GroupFeedPaginationToken decodedToken = GroupFeedPaginationToken.decode(encodedToken);
        
        // Then
        assertThat(decodedToken).isNotNull();
        assertThat(decodedToken.getLastEventId()).isEqualTo(EVENT_ID);
        assertThat(decodedToken.getLastTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(decodedToken.isForward()).isEqualTo(false);
    }
    
    @Test
    void encodeAndDecode_RoundTrip_PreservesData() {
        // Given
        GroupFeedPaginationToken originalToken = new GroupFeedPaginationToken(EVENT_ID, TEST_TIMESTAMP, true);
        
        // When
        String encoded = originalToken.encode();
        GroupFeedPaginationToken decodedToken = GroupFeedPaginationToken.decode(encoded);
        
        // Then
        assertThat(decodedToken.getLastEventId()).isEqualTo(originalToken.getLastEventId());
        assertThat(decodedToken.getLastTimestamp()).isEqualTo(originalToken.getLastTimestamp());
        assertThat(decodedToken.isForward()).isEqualTo(originalToken.isForward());
    }
    
    @Test
    void decode_WithInvalidBase64_ThrowsRuntimeException() {
        // Given
        String invalidToken = "invalid-base64!@#$";
        
        // When & Then
        assertThatThrownBy(() -> GroupFeedPaginationToken.decode(invalidToken))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to decode pagination token");
    }
    
    @Test
    void decode_WithInvalidJson_ThrowsRuntimeException() {
        // Given
        String invalidJsonToken = java.util.Base64.getEncoder().encodeToString("invalid json".getBytes());
        
        // When & Then
        assertThatThrownBy(() -> GroupFeedPaginationToken.decode(invalidJsonToken))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to decode pagination token");
    }
    
    @Test
    void encode_WithNullEventId_StillWorks() {
        // Given
        GroupFeedPaginationToken token = new GroupFeedPaginationToken(null, TEST_TIMESTAMP, true);
        
        // When
        String encoded = token.encode();
        GroupFeedPaginationToken decoded = GroupFeedPaginationToken.decode(encoded);
        
        // Then
        assertThat(decoded.getLastEventId()).isNull();
        assertThat(decoded.getLastTimestamp()).isEqualTo(TEST_TIMESTAMP);
        assertThat(decoded.isForward()).isEqualTo(true);
    }
    
    @Test
    void encode_WithNullTimestamp_StillWorks() {
        // Given
        GroupFeedPaginationToken token = new GroupFeedPaginationToken(EVENT_ID, null, false);
        
        // When
        String encoded = token.encode();
        GroupFeedPaginationToken decoded = GroupFeedPaginationToken.decode(encoded);
        
        // Then
        assertThat(decoded.getLastEventId()).isEqualTo(EVENT_ID);
        assertThat(decoded.getLastTimestamp()).isNull();
        assertThat(decoded.isForward()).isEqualTo(false);
    }
    
    @Test
    void defaultConstructor_CreatesEmptyToken() {
        // When
        GroupFeedPaginationToken token = new GroupFeedPaginationToken();
        
        // Then
        assertThat(token.getLastEventId()).isNull();
        assertThat(token.getLastTimestamp()).isNull();
        assertThat(token.isForward()).isEqualTo(false); // default value
    }
}