package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.InviteCode;

import java.util.List;
import java.util.Optional;

/**
 * Repository for InviteCode entities using DynamoDB Enhanced Client.
 */
public interface InviteCodeRepository {

    /**
     * Save or update an invite code.
     */
    void save(InviteCode inviteCode);

    /**
     * Find invite code by code string (uses InviteCodeIndex GSI).
     *
     * @param code The invite code to search for
     * @return Optional containing the InviteCode if found, empty otherwise
     */
    Optional<InviteCode> findByCode(String code);

    /**
     * Find invite code by ID (canonical record lookup).
     *
     * @param inviteCodeId The invite code ID
     * @return Optional containing the InviteCode if found, empty otherwise
     */
    Optional<InviteCode> findById(String inviteCodeId);

    /**
     * List all invite codes for a group (uses UserGroupIndex GSI).
     * Results are sorted by creation time.
     *
     * @param groupId The group ID
     * @return List of invite codes for the group
     */
    List<InviteCode> findAllByGroupId(String groupId);

    /**
     * Find the first active invite code for a group.
     * Used for idempotent code generation - reuse existing active code.
     *
     * @param groupId The group ID
     * @return Optional containing the first active InviteCode if found
     */
    Optional<InviteCode> findActiveCodeForGroup(String groupId);

    /**
     * Check if a code string is already in use (for collision detection).
     *
     * @param code The code to check
     * @return true if the code already exists, false otherwise
     */
    boolean codeExists(String code);

    /**
     * Delete an invite code (rarely used - prefer deactivation).
     *
     * @param inviteCodeId The invite code ID to delete
     */
    void delete(String inviteCodeId);
}
