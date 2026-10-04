package com.billing.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for invalid business logic (not just missing fields).
 *
 * Examples:
 *   - Email already registered (409 Conflict)
 *   - Bill already paid (cannot pay again)
 *   - GST rate not in allowed values {0, 5, 12, 18, 28}
 *
 * HTTP 400 Bad Request returned automatically.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
