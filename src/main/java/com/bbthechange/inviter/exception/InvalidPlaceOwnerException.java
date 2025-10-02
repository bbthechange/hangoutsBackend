package com.bbthechange.inviter.exception;

/**
 * Exception thrown when an operation is attempted with an invalid place owner.
 */
public class InvalidPlaceOwnerException extends RuntimeException {

    public InvalidPlaceOwnerException(String message) {
        super(message);
    }

    public InvalidPlaceOwnerException(String message, Throwable cause) {
        super(message, cause);
    }
}
