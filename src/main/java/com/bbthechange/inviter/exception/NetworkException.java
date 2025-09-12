package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
public class NetworkException extends EventParseException {
    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}