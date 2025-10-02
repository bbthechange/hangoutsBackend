package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;

/**
 * Request DTO for updating an existing place.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlaceRequest {

    private String nickname;

    @Valid
    private Address address;

    private String notes;

    private Boolean isPrimary; // Nullable - only update if provided
}
