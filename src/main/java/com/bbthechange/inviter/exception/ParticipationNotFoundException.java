package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a requested participation cannot be found.
 */
public class ParticipationNotFoundException extends ResourceNotFoundException {

    public ParticipationNotFoundException(String message) {
        super(message);
    }

    public ParticipationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
