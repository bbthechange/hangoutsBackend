package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
public class EventParseException extends RuntimeException {
    public EventParseException(String message) {
        super(message);
    }

    public EventParseException(String message, Throwable cause) {
        super(message, cause);
    }
}