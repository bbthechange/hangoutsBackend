package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY)
public class ContentValidationException extends EventParseException {
    public ContentValidationException(String message) {
        super(message);
    }

    public ContentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}