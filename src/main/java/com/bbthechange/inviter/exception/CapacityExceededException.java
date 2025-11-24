package com.bbthechange.inviter.exception;

/**
 * Exception thrown when attempting to claim a spot in a reservation offer
 * that has reached its maximum capacity.
 *
 * This is a 409 Conflict error indicating the resource state prevents the operation.
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String message) {
        super(message);
    }

    public CapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
