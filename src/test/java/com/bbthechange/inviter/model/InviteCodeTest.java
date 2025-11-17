package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InviteCode entity.
 * Tests business logic methods without database dependencies.
 */
class InviteCodeTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String CODE = "abc123xy";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String GROUP_NAME = "Test Group";

    /**
     * Helper method to create a valid InviteCode for testing.
     */
    private InviteCode createValidInviteCode() {
        return new InviteCode(GROUP_ID, CODE, USER_ID, GROUP_NAME);
    }

    /**
     * Helper method to create an expired InviteCode.
     */
    private InviteCode createExpiredInviteCode() {
        InviteCode code = createValidInviteCode();
        code.setExpiresAt(Instant.now().minusSeconds(3600)); // 1 hour ago
        return code;
    }

    /**
     * Helper method to create a single-use InviteCode.
     */
    private InviteCode createSingleUseInviteCode() {
        InviteCode code = createValidInviteCode();
        code.setSingleUse(true);
        return code;
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructor_SetsAllRequiredFieldsCorrectly() {
            // When
            InviteCode inviteCode = new InviteCode(GROUP_ID, CODE, USER_ID, GROUP_NAME);

            // Then
            assertThat(inviteCode.getPk()).startsWith("INVITE_CODE#");
            assertThat(inviteCode.getSk()).isEqualTo("METADATA");
            assertThat(inviteCode.getGsi3pk()).isEqualTo("CODE#" + CODE);
            assertThat(inviteCode.getGsi1pk()).isEqualTo("GROUP#" + GROUP_ID);
            assertThat(inviteCode.getGsi1sk()).startsWith("CREATED#");
            assertThat(inviteCode.isActive()).isTrue();
            assertThat(inviteCode.isSingleUse()).isFalse();
            assertThat(inviteCode.getUsages()).isNotNull().isEmpty();
            assertThat(inviteCode.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(inviteCode.getCode()).isEqualTo(CODE);
            assertThat(inviteCode.getCreatedBy()).isEqualTo(USER_ID);
            assertThat(inviteCode.getGroupName()).isEqualTo(GROUP_NAME);
            assertThat(inviteCode.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    class RecordUsageTests {

        @Test
        void recordUsage_AddsUserIdToList() {
            // Given
            InviteCode code = createValidInviteCode();
            assertThat(code.getUsages()).isEmpty();

            // When
            code.recordUsage(USER_ID);

            // Then
            assertThat(code.getUsages()).containsExactly(USER_ID);
            assertThat(code.getUsages().size()).isEqualTo(1);
        }

        @Test
        void recordUsage_MultipleUsagesAccumulate() {
            // Given
            InviteCode code = createValidInviteCode();
            String userId1 = "user-1";
            String userId2 = "user-2";
            String userId3 = "user-3";

            // When
            code.recordUsage(userId1);
            code.recordUsage(userId2);
            code.recordUsage(userId3);

            // Then
            assertThat(code.getUsages()).containsExactly(userId1, userId2, userId3);
            assertThat(code.getUsages().size()).isEqualTo(3);
        }

        @Test
        void recordUsage_SingleUseCodeAutoDeactivatesAfterFirstUsage() {
            // Given
            InviteCode code = createSingleUseInviteCode();
            assertThat(code.isActive()).isTrue();
            assertThat(code.getUsages()).isEmpty();

            // When
            code.recordUsage(USER_ID);

            // Then
            assertThat(code.isActive()).isFalse();
            assertThat(code.getDeactivationReason()).contains("Single-use");
            assertThat(code.getUsages()).containsExactly(USER_ID);
        }

        @Test
        void recordUsage_SingleUseCodeDoesNotDeactivateOnSubsequentCalls() {
            // Given
            InviteCode code = createSingleUseInviteCode();
            code.recordUsage("user-1");
            assertThat(code.isActive()).isFalse();

            // When
            code.recordUsage("user-2");

            // Then
            assertThat(code.isActive()).isFalse(); // Remains false
            assertThat(code.getUsages()).containsExactly("user-1", "user-2");
        }

        @Test
        void recordUsage_RegularCodeDoesNotAutoDeactivate() {
            // Given
            InviteCode code = createValidInviteCode();
            assertThat(code.isSingleUse()).isFalse();

            // When
            code.recordUsage("user-1");
            code.recordUsage("user-2");
            code.recordUsage("user-3");

            // Then
            assertThat(code.isActive()).isTrue();
            assertThat(code.getUsages()).hasSize(3);
        }

        @Test
        void recordUsage_OnNullListInitializesList() {
            // Given
            InviteCode code = createValidInviteCode();
            code.setUsages(null);

            // When
            code.recordUsage(USER_ID);

            // Then
            assertThat(code.getUsages()).isNotNull().containsExactly(USER_ID);
        }
    }

    @Nested
    class DeactivateTests {

        @Test
        void deactivate_SetsAllRequiredFields() {
            // Given
            InviteCode code = createValidInviteCode();
            assertThat(code.isActive()).isTrue();
            String deactivatorId = "admin-789";
            String reason = "Code leaked";

            // When
            code.deactivate(deactivatorId, reason);

            // Then
            assertThat(code.isActive()).isFalse();
            assertThat(code.getDeactivatedBy()).isEqualTo(deactivatorId);
            assertThat(code.getDeactivatedAt()).isNotNull();
            assertThat(code.getDeactivationReason()).isEqualTo(reason);
        }

        @Test
        void deactivate_OnAlreadyInactiveCodeUpdatesFields() {
            // Given
            InviteCode code = createValidInviteCode();
            code.deactivate("user-1", "Old reason");
            Instant firstDeactivation = code.getDeactivatedAt();

            // Small delay to ensure timestamp changes
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // When
            code.deactivate("user-2", "New reason");

            // Then
            assertThat(code.getDeactivatedBy()).isEqualTo("user-2");
            assertThat(code.getDeactivationReason()).isEqualTo("New reason");
            assertThat(code.getDeactivatedAt()).isAfter(firstDeactivation);
        }
    }

    @Nested
    class IsUsableTests {

        @Test
        void isUsable_ActiveNonExpiredCodeIsUsable() {
            // Given
            InviteCode code = createValidInviteCode();
            assertThat(code.isActive()).isTrue();
            assertThat(code.getExpiresAt()).isNull();

            // When/Then
            assertThat(code.isUsable()).isTrue();
        }

        @Test
        void isUsable_InactiveCodeIsNotUsable() {
            // Given
            InviteCode code = createValidInviteCode();
            code.setActive(false);

            // When/Then
            assertThat(code.isUsable()).isFalse();
        }

        @Test
        void isUsable_ExpiredCodeIsNotUsable() {
            // Given
            InviteCode code = createExpiredInviteCode();
            assertThat(code.isActive()).isTrue();

            // When/Then
            assertThat(code.isUsable()).isFalse();
        }

        @Test
        void isUsable_ActiveCodeWithFutureExpirationIsUsable() {
            // Given
            InviteCode code = createValidInviteCode();
            code.setExpiresAt(Instant.now().plusSeconds(3600)); // 1 hour from now
            assertThat(code.isActive()).isTrue();

            // When/Then
            assertThat(code.isUsable()).isTrue();
        }

        @Test
        void isUsable_InactiveAndExpiredCodeIsNotUsable() {
            // Given
            InviteCode code = createExpiredInviteCode();
            code.setActive(false);

            // When/Then
            assertThat(code.isUsable()).isFalse();
        }

        @Test
        void isUsable_CodeWithNoExpirationDateIsUsableIfActive() {
            // Given
            InviteCode code = createValidInviteCode();
            code.setExpiresAt(null);
            assertThat(code.isActive()).isTrue();

            // When/Then
            assertThat(code.isUsable()).isTrue();
        }
    }

    @Nested
    class GetUsageCountTests {

        @Test
        void getUsageCount_ReturnsZeroForEmptyList() {
            // Given
            InviteCode code = createValidInviteCode();
            assertThat(code.getUsages()).isEmpty();

            // When/Then
            assertThat(code.getUsageCount()).isEqualTo(0);
        }

        @Test
        void getUsageCount_ReturnsCorrectCountForMultipleUsages() {
            // Given
            InviteCode code = createValidInviteCode();
            code.setUsages(new ArrayList<>());
            code.getUsages().add("user-1");
            code.getUsages().add("user-2");
            code.getUsages().add("user-3");
            code.getUsages().add("user-4");
            code.getUsages().add("user-5");

            // When/Then
            assertThat(code.getUsageCount()).isEqualTo(5);
        }

        @Test
        void getUsageCount_ReturnsZeroForNullList() {
            // Given
            InviteCode code = createValidInviteCode();
            code.setUsages(null);

            // When/Then
            assertThat(code.getUsageCount()).isEqualTo(0);
        }
    }
}
