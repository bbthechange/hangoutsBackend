package com.bbthechange.inviter.exception;

/**
 * Exception thrown when a requested reservation offer cannot be found.
 */
public class ReservationOfferNotFoundException extends ResourceNotFoundException {

    public ReservationOfferNotFoundException(String message) {
        super(message);
    }

    public ReservationOfferNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
