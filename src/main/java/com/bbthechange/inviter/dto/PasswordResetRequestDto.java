package com.bbthechange.inviter.dto;

/**
 * Request DTO for initiating a password reset.
 * Used by POST /auth/request-password-reset endpoint.
 */
public class PasswordResetRequestDto {
    private String phoneNumber;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
