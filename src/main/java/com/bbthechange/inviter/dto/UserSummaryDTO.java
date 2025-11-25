package com.bbthechange.inviter.dto;

import java.util.UUID;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for caching user profile summary information.
 * Contains only display-related fields needed for UI rendering.
 * Used with Spring Cache to reduce database reads.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {
    private UUID id;
    private String displayName;
    private String mainImagePath;
}
