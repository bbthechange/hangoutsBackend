package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Place responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceDto {

    private String placeId;
    private String nickname;
    private Address address;
    private String notes;
    private boolean primary;
    private String status;
    private String ownerType;
    private String createdBy;
    private Long createdAt;
    private Long updatedAt;
}
