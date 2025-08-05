package com.bbthechange.inviter.exception;

/**
 * Exception thrown when trying to reserve a seat but none are available.
 */
public class NoAvailableSeatsException extends RuntimeException {
    
    public NoAvailableSeatsException(String message) {
        super(message);
    }
    
    public NoAvailableSeatsException(String message, Throwable cause) {
        super(message, cause);
    }
}