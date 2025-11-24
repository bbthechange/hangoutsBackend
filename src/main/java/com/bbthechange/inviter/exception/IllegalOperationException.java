package com.bbthechange.inviter.exception;

/**
 * Exception thrown when an operation is not allowed due to the current state
 * or configuration of the resource.
 *
 * For example, attempting to claim a spot on an offer with unlimited capacity.
 */
public class IllegalOperationException extends RuntimeException {

    public IllegalOperationException(String message) {
        super(message);
    }

    public IllegalOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
