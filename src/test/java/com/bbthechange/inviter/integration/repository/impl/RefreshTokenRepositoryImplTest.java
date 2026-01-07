package com.bbthechange.inviter.integration.repository.impl;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RefreshTokenRepositoryImpl using TestContainers.
 * Tests all repository methods with real DynamoDB operations.
 */
@Testcontainers
class RefreshTokenRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String testUserId;

    @BeforeEach
    void setUpTest() {
        testUserId = UUID.randomUUID().toString();
    }

    // ===== SAVE TESTS =====

    @Test
    void save_ValidToken_PersistsAndReturnsToken() {
        // Given
        RefreshToken token = createToken(testUserId, "hash1");

        // When
        RefreshToken saved = refreshTokenRepository.save(token);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(testUserId);
        assertThat(saved.getTokenHash()).isEqualTo("hash1");
        assertThat(saved.getPk()).isEqualTo("USER#" + testUserId);
        assertThat(saved.getSk()).startsWith("REFRESH_TOKEN#");
    }

    @Test
    void save_UpdatesTimestamp() {
        // Given
        RefreshToken token = createToken(testUserId, "hash1");
        java.time.Instant originalTimestamp = token.getUpdatedAt();

        // When - small delay to ensure timestamp changes
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        RefreshToken saved = refreshTokenRepository.save(token);

        // Then
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(originalTimestamp);
    }

    // ===== FIND BY TOKEN HASH TESTS =====

    @Test
    void findByTokenHash_ExistingToken_ReturnsToken() {
        // Given
        String uniqueHash = "unique-hash-" + UUID.randomUUID();
        RefreshToken token = createToken(testUserId, uniqueHash);
        refreshTokenRepository.save(token);

        // When
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash(uniqueHash);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(testUserId);
        assertThat(found.get().getTokenHash()).isEqualTo(uniqueHash);
    }

    @Test
    void findByTokenHash_NonExistentToken_ReturnsEmpty() {
        // When
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("non-existent-hash");

        // Then
        assertThat(found).isEmpty();
    }

    // ===== FIND ALL BY USER ID TESTS =====

    @Test
    void findAllByUserId_MultipleTokens_ReturnsAllTokens() {
        // Given
        RefreshToken token1 = createToken(testUserId, "hash1");
        RefreshToken token2 = createToken(testUserId, "hash2");
        RefreshToken token3 = createToken(testUserId, "hash3");
        refreshTokenRepository.save(token1);
        refreshTokenRepository.save(token2);
        refreshTokenRepository.save(token3);

        // When
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(testUserId);

        // Then
        assertThat(tokens).hasSize(3);
        assertThat(tokens).extracting(RefreshToken::getTokenHash)
            .containsExactlyInAnyOrder("hash1", "hash2", "hash3");
    }

    @Test
    void findAllByUserId_NoTokens_ReturnsEmptyList() {
        // Given
        String userWithNoTokens = UUID.randomUUID().toString();

        // When
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(userWithNoTokens);

        // Then
        assertThat(tokens).isEmpty();
    }

    @Test
    void findAllByUserId_DoesNotReturnOtherUserTokens() {
        // Given
        String otherUserId = UUID.randomUUID().toString();
        RefreshToken myToken = createToken(testUserId, "my-hash");
        RefreshToken otherToken = createToken(otherUserId, "other-hash");
        refreshTokenRepository.save(myToken);
        refreshTokenRepository.save(otherToken);

        // When
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserId(testUserId);

        // Then
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getTokenHash()).isEqualTo("my-hash");
    }

    // ===== DELETE BY TOKEN ID TESTS =====

    @Test
    void deleteByTokenId_ExistingToken_RemovesToken() {
        // Given
        RefreshToken token = createToken(testUserId, "hash-to-delete");
        refreshTokenRepository.save(token);

        // Verify it exists
        List<RefreshToken> beforeDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(beforeDelete).hasSize(1);

        // When
        refreshTokenRepository.deleteByTokenId(testUserId, token.getTokenId());

        // Then
        List<RefreshToken> afterDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void deleteByTokenId_NonExistentToken_DoesNotThrow() {
        // When/Then - should not throw
        refreshTokenRepository.deleteByTokenId(testUserId, "non-existent-token-id");
    }

    // ===== DELETE ALL USER TOKENS TESTS =====

    @Test
    void deleteAllUserTokens_MultipleTokens_RemovesAll() {
        // Given
        for (int i = 0; i < 5; i++) {
            RefreshToken token = createToken(testUserId, "hash-" + i);
            refreshTokenRepository.save(token);
        }

        // Verify tokens exist
        List<RefreshToken> beforeDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(beforeDelete).hasSize(5);

        // When
        refreshTokenRepository.deleteAllUserTokens(testUserId);

        // Then
        List<RefreshToken> afterDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void deleteAllUserTokens_NoTokens_DoesNotThrow() {
        // Given
        String userWithNoTokens = UUID.randomUUID().toString();

        // When/Then - should not throw
        refreshTokenRepository.deleteAllUserTokens(userWithNoTokens);
    }

    @Test
    void deleteAllUserTokens_DoesNotAffectOtherUsers() {
        // Given
        String otherUserId = UUID.randomUUID().toString();
        RefreshToken myToken = createToken(testUserId, "my-hash");
        RefreshToken otherToken = createToken(otherUserId, "other-hash");
        refreshTokenRepository.save(myToken);
        refreshTokenRepository.save(otherToken);

        // When
        refreshTokenRepository.deleteAllUserTokens(testUserId);

        // Then - my tokens deleted, other user's tokens remain
        assertThat(refreshTokenRepository.findAllByUserId(testUserId)).isEmpty();
        assertThat(refreshTokenRepository.findAllByUserId(otherUserId)).hasSize(1);
    }

    /**
     * Critical test for the batch fix - verifies that deleting more than 25 tokens works.
     * DynamoDB BatchWriteItem has a 25-item limit per request.
     */
    @Test
    void deleteAllUserTokens_MoreThan25Tokens_DeletesAllInBatches() {
        // Given: Create 30 tokens (exceeds DynamoDB's 25-item batch limit)
        int tokenCount = 30;
        for (int i = 0; i < tokenCount; i++) {
            RefreshToken token = createToken(testUserId, "hash-" + i);
            refreshTokenRepository.save(token);
        }

        // Verify all tokens exist
        List<RefreshToken> beforeDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(beforeDelete).hasSize(tokenCount);

        // When - this should succeed with batching fix
        refreshTokenRepository.deleteAllUserTokens(testUserId);

        // Then - all tokens deleted
        List<RefreshToken> afterDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void deleteAllUserTokens_Exactly25Tokens_DeletesAll() {
        // Given: Exactly 25 tokens (at the batch limit boundary)
        int tokenCount = 25;
        for (int i = 0; i < tokenCount; i++) {
            RefreshToken token = createToken(testUserId, "hash-" + i);
            refreshTokenRepository.save(token);
        }

        List<RefreshToken> beforeDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(beforeDelete).hasSize(tokenCount);

        // When
        refreshTokenRepository.deleteAllUserTokens(testUserId);

        // Then
        List<RefreshToken> afterDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void deleteAllUserTokens_50Tokens_DeletesAllInTwoBatches() {
        // Given: 50 tokens (requires 2 batches of 25)
        int tokenCount = 50;
        for (int i = 0; i < tokenCount; i++) {
            RefreshToken token = createToken(testUserId, "hash-" + i);
            refreshTokenRepository.save(token);
        }

        List<RefreshToken> beforeDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(beforeDelete).hasSize(tokenCount);

        // When
        refreshTokenRepository.deleteAllUserTokens(testUserId);

        // Then
        List<RefreshToken> afterDelete = refreshTokenRepository.findAllByUserId(testUserId);
        assertThat(afterDelete).isEmpty();
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
}
