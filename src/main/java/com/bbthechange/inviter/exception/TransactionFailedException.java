package com.bbthechange.inviter.exception;

/**
 * Exception thrown when DynamoDB transactions fail.
 * Used for atomic operations that must succeed or fail together.
 */
public class TransactionFailedException extends RuntimeException {
    
    public TransactionFailedException(String message) {
        super(message);
    }
    
    public TransactionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}