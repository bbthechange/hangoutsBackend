package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a password reset request is invalid or not found.
 * This can occur when:
 * - No reset request exists for the provided phone number or user ID
 * - The reset request exists but is in an invalid state (already used, not verified, etc.)
 * - The reset method doesn't match the request
 */
public class InvalidResetRequestException extends RuntimeException {
    public InvalidResetRequestException(String message) {
        super(message);
    }
}
