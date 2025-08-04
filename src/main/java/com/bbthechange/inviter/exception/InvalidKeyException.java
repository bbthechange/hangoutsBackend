package com.bbthechange.inviter.exception;

/**
 * Exception thrown when DynamoDB key validation fails.
 * Used by InviterKeyFactory to ensure type safety and valid key patterns.
 */
public class InvalidKeyException extends RuntimeException {
    
    public InvalidKeyException(String message) {
        super(message);
    }
    
    public InvalidKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}