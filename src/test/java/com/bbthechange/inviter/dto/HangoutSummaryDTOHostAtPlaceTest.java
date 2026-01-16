package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.testutil.HangoutPointerTestBuilder;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HangoutSummaryDTO Host At Place fields.
 *
 * Coverage:
 * - Constructor copies hostAtPlaceUserId from HangoutPointer
 * - Null handling for hostAtPlaceUserId
 * - Builder sets all host at place fields correctly
 */
class HangoutSummaryDTOHostAtPlaceTest {

    @Test
    void constructor_WithPointerHavingHostAtPlaceUserId_CopiesUserId() {
        // Given: HangoutPointer with hostAtPlaceUserId = "user123"
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String requestingUserId = UUID.randomUUID().toString();
        String hostAtPlaceUserId = "user123";

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Test Hangout")
            .build();
        pointer.setHostAtPlaceUserId(hostAtPlaceUserId);

        // When
        HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

        // Then: dto.hostAtPlaceUserId = "user123", displayName and imagePath are null
        // (displayName and imagePath are set by service enrichment, not constructor)
        assertThat(dto.getHostAtPlaceUserId()).isEqualTo("user123");
        assertThat(dto.getHostAtPlaceDisplayName()).isNull();
        assertThat(dto.getHostAtPlaceImagePath()).isNull();
    }

    @Test
    void constructor_WithPointerHavingNullHostAtPlaceUserId_SetsNullUserId() {
        // Given: HangoutPointer with hostAtPlaceUserId = null
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String requestingUserId = UUID.randomUUID().toString();

        HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
            .forGroup(groupId)
            .forHangout(hangoutId)
            .withTitle("Test Hangout")
            .build();
        // hostAtPlaceUserId is null by default

        // When
        HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

        // Then: dto.hostAtPlaceUserId = null
        assertThat(dto.getHostAtPlaceUserId()).isNull();
        assertThat(dto.getHostAtPlaceDisplayName()).isNull();
        assertThat(dto.getHostAtPlaceImagePath()).isNull();
    }

    @Test
    void builder_WithAllHostAtPlaceFields_SetsAllFields() {
        // Given: Builder with withHostAtPlaceUserId, withHostAtPlaceDisplayName, withHostAtPlaceImagePath
        String hostUserId = UUID.randomUUID().toString();
        String displayName = "John Doe";
        String imagePath = "users/john/profile.jpg";

        // When
        HangoutSummaryDTO dto = HangoutSummaryDTO.builder()
            .withHangoutId("hangout-1")
            .withTitle("Test Hangout")
            .withHostAtPlaceUserId(hostUserId)
            .withHostAtPlaceDisplayName(displayName)
            .withHostAtPlaceImagePath(imagePath)
            .build();

        // Then: All three fields populated correctly
        assertThat(dto.getHostAtPlaceUserId()).isEqualTo(hostUserId);
        assertThat(dto.getHostAtPlaceDisplayName()).isEqualTo(displayName);
        assertThat(dto.getHostAtPlaceImagePath()).isEqualTo(imagePath);
    }
}
