package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a requested place cannot be found.
 */
public class PlaceNotFoundException extends ResourceNotFoundException {

    public PlaceNotFoundException(String message) {
        super(message);
    }

    public PlaceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
