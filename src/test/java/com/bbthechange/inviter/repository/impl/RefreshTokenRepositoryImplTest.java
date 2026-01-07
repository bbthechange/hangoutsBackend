package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.RefreshToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefreshTokenRepositoryImpl.
 * Tests all repository methods with mocked DynamoDB operations.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryImplTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<RefreshToken> refreshTokenTable;

    @Mock
    private DynamoDbIndex<RefreshToken> tokenHashIndex;

    private RefreshTokenRepositoryImpl repository;
    private String testUserId;

    // Real schema needed for WriteBatch operations
    private static final TableSchema<RefreshToken> REFRESH_TOKEN_SCHEMA =
        TableSchema.fromBean(RefreshToken.class);

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq("InviterTable"), any(TableSchema.class)))
            .thenReturn(refreshTokenTable);
        when(refreshTokenTable.index("TokenHashIndex")).thenReturn(tokenHashIndex);
        // Provide real schema for WriteBatch operations
        lenient().when(refreshTokenTable.tableSchema()).thenReturn(REFRESH_TOKEN_SCHEMA);

        repository = new RefreshTokenRepositoryImpl(enhancedClient);
        testUserId = UUID.randomUUID().toString();
    }

    // ===== SAVE TESTS =====

    @Test
    void save_ValidToken_CallsPutItem() {
        // Given
        RefreshToken token = createToken(testUserId, "hash1");

        // When
        RefreshToken result = repository.save(token);

        // Then
        verify(refreshTokenTable).putItem(token);
        assertThat(result).isEqualTo(token);
    }

    // ===== FIND ALL BY USER ID TESTS =====

    @Test
    void findAllByUserId_ReturnsTokens() {
        // Given
        List<RefreshToken> tokens = createTokens(3);
        mockQueryResults(tokens);

        // When
        List<RefreshToken> result = repository.findAllByUserId(testUserId);

        // Then
        assertThat(result).hasSize(3);
        verify(refreshTokenTable).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findAllByUserId_EmptyResult_ReturnsEmptyList() {
        // Given
        mockQueryResults(List.of());

        // When
        List<RefreshToken> result = repository.findAllByUserId(testUserId);

        // Then
        assertThat(result).isEmpty();
    }

    // ===== DELETE BY TOKEN ID TESTS =====

    @Test
    void deleteByTokenId_CallsDeleteItem() {
        // Given
        String tokenId = UUID.randomUUID().toString();

        // When
        repository.deleteByTokenId(testUserId, tokenId);

        // Then
        verify(refreshTokenTable).deleteItem(any(Key.class));
    }

    // ===== DELETE ALL USER TOKENS TESTS =====

    @Test
    void deleteAllUserTokens_EmptyList_NoBatchWriteCalled() {
        // Given: No tokens exist for user
        mockQueryResults(List.of());

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: No batch write should be attempted
        verify(enhancedClient, never()).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_LessThan25Tokens_SingleBatchCall() {
        // Given: 10 tokens for user (under the 25 limit)
        List<RefreshToken> tokens = createTokens(10);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Exactly one batch write call
        verify(enhancedClient, times(1)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_Exactly25Tokens_SingleBatchCall() {
        // Given: Exactly 25 tokens (the limit)
        List<RefreshToken> tokens = createTokens(25);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Exactly one batch write call
        verify(enhancedClient, times(1)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_26Tokens_TwoBatchCalls() {
        // Given: 26 tokens (exceeds 25 limit by 1)
        List<RefreshToken> tokens = createTokens(26);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Two batch write calls (25 + 1)
        verify(enhancedClient, times(2)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_50Tokens_TwoBatchCalls() {
        // Given: 50 tokens (exactly 2 full batches)
        List<RefreshToken> tokens = createTokens(50);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Two batch write calls (25 + 25)
        verify(enhancedClient, times(2)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_75Tokens_ThreeBatchCalls() {
        // Given: 75 tokens (3 full batches)
        List<RefreshToken> tokens = createTokens(75);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Three batch write calls (25 + 25 + 25)
        verify(enhancedClient, times(3)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_51Tokens_ThreeBatchCalls() {
        // Given: 51 tokens (25 + 25 + 1)
        List<RefreshToken> tokens = createTokens(51);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Three batch write calls
        verify(enhancedClient, times(3)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    @Test
    void deleteAllUserTokens_100Tokens_FourBatchCalls() {
        // Given: 100 tokens (4 batches: 25 + 25 + 25 + 25)
        List<RefreshToken> tokens = createTokens(100);
        mockQueryResultsWithBatchWrite(tokens);

        // When
        repository.deleteAllUserTokens(testUserId);

        // Then: Four batch write calls
        verify(enhancedClient, times(4)).batchWriteItem(any(BatchWriteItemEnhancedRequest.class));
    }

    // ===== HELPER METHODS =====

    private RefreshToken createToken(String userId, String tokenHash) {
        return new RefreshToken(
            userId,
            tokenHash,
            "security-hash-" + UUID.randomUUID(),
            "device-" + UUID.randomUUID(),
            "127.0.0.1"
        );
    }

    private List<RefreshToken> createTokens(int count) {
        List<RefreshToken> tokens = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> {
            RefreshToken token = createToken(testUserId, "hash" + i);
            tokens.add(token);
        });
        return tokens;
    }

    @SuppressWarnings("unchecked")
    private void mockQueryResults(List<RefreshToken> tokens) {
        Page<RefreshToken> page = mock(Page.class);
        when(page.items()).thenReturn(tokens);

        PageIterable<RefreshToken> pageIterable = mock(PageIterable.class);
        when(pageIterable.stream()).thenReturn(java.util.stream.Stream.of(page));

        when(refreshTokenTable.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
    }

    @SuppressWarnings("unchecked")
    private void mockQueryResultsWithBatchWrite(List<RefreshToken> tokens) {
        mockQueryResults(tokens);

        // Stub batchWriteItem to return a mock response
        BatchWriteResult mockResult = mock(BatchWriteResult.class);
        when(enhancedClient.batchWriteItem(any(BatchWriteItemEnhancedRequest.class))).thenReturn(mockResult);
    }
}
