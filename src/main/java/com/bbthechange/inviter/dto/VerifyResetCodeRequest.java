package com.bbthechange.inviter.dto;

/**
 * Request DTO for verifying a password reset code.
 * Used by POST /auth/verify-reset-code endpoint.
 */
public class VerifyResetCodeRequest {
    private String phoneNumber;
    private String code;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
