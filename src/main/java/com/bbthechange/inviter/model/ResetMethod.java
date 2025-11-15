package com.bbthechange.inviter.model;

/**
 * Enum representing the method used for password reset verification.
 */
public enum ResetMethod {
    /**
     * Password reset initiated via phone number with SMS verification code
     */
    PHONE,

    /**
     * Password reset initiated via email with verification link (future use)
     */
    EMAIL
}
