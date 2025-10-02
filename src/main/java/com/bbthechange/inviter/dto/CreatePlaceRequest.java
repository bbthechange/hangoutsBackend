package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new place.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlaceRequest {

    @Valid
    @NotNull(message = "Owner is required")
    private OwnerDto owner;

    @NotBlank(message = "Nickname is required")
    private String nickname;

    @Valid
    @NotNull(message = "Address is required")
    private Address address;

    private String notes;

    private boolean isPrimary; // Only applies to USER-owned places
}
