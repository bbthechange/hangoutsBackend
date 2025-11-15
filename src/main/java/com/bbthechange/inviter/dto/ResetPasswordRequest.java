package com.bbthechange.inviter.dto;

/**
 * Request DTO for resetting a password with a verified reset token.
 * Used by POST /auth/reset-password endpoint.
 */
public class ResetPasswordRequest {
    private String resetToken;
    private String newPassword;

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
