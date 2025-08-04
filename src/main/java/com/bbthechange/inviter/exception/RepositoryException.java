package com.bbthechange.inviter.exception;

/**
 * Exception thrown when repository operations fail.
 * Wraps lower-level database exceptions with meaningful messages.
 */
public class RepositoryException extends RuntimeException {
    
    public RepositoryException(String message) {
        super(message);
    }
    
    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}