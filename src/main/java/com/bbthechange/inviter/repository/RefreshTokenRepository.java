package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.RefreshToken;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository {
    
    /**
     * Save new refresh token
     */
    RefreshToken save(RefreshToken token);
    
    /**
     * Find refresh token by SHA-256 hash (for fast lookup)
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    
    /**
     * Find all tokens for a user (for logout-all)
     */
    List<RefreshToken> findAllByUserId(String userId);
    
    /**
     * Delete specific refresh token
     */
    void deleteByTokenId(String userId, String tokenId);
    
    /**
     * Delete all user tokens (logout from all devices / security response)
     */
    void deleteAllUserTokens(String userId);
}