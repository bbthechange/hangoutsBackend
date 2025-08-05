package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a car offer is not found.
 */
public class CarNotFoundException extends RuntimeException {
    
    public CarNotFoundException(String message) {
        super(message);
    }
    
    public CarNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}