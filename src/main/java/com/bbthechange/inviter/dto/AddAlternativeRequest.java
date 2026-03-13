package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for submitting an alternative value to an existing attribute proposal.
 */
public class AddAlternativeRequest {

    @NotBlank(message = "Alternative value is required")
    @Size(max = 2000, message = "Alternative value must not exceed 2000 characters")
    private String value;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
