package com.bbthechange.inviter.exception;

/**
 * Exception thrown when an event is not found.
 */
public class EventNotFoundException extends RuntimeException {
    
    public EventNotFoundException(String message) {
        super(message);
    }
    
    public EventNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}