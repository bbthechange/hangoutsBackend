package com.bbthechange.inviter.exception;

/**
 * Exception thrown when an optimistic locking version conflict occurs.
 *
 * This indicates that the resource has been modified by another user/process
 * since it was last read.
 *
 * Note: In ReservationOfferService, version conflicts are handled internally
 * with automatic retries. This exception should rarely be thrown to external callers.
 */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException(String message) {
        super(message);
    }

    public VersionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
