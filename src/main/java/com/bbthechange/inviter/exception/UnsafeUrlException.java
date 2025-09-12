package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class UnsafeUrlException extends EventParseException {
    public UnsafeUrlException(String message) {
        super(message);
    }

    public UnsafeUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}