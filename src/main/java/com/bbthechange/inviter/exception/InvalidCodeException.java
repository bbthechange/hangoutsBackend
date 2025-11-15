package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a password reset verification code is invalid.
 * This can occur when:
 * - The submitted code doesn't match the expected code
 * - The code has expired
 * - Too many failed verification attempts have been made
 */
public class InvalidCodeException extends RuntimeException {
    public InvalidCodeException(String message) {
        super(message);
    }
}
