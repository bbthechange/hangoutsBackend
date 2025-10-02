package com.bbthechange.inviter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO representing the owner of a place (user or group).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDto {

    @NotBlank(message = "Owner ID is required")
    private String id;

    @NotBlank(message = "Owner type is required")
    @Pattern(regexp = "USER|GROUP", message = "Owner type must be USER or GROUP")
    private String type;
}
