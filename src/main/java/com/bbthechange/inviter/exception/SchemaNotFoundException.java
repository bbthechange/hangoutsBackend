package com.bbthechange.inviter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class SchemaNotFoundException extends EventParseException {
    public SchemaNotFoundException(String message) {
        super(message);
    }

    public SchemaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}