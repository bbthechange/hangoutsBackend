package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a password reset token is invalid.
 * This can occur when:
 * - The JWT token signature is invalid
 * - The token has expired
 * - The token type claim is not "password_reset"
 * - The token has already been used
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
