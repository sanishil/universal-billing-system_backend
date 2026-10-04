package com.billing.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when there's a conflict with existing data.
 *
 * Examples:
 *   - POST /api/auth/register with email that already exists → 409
 *   - Creating a customer with a duplicate email → 409
 *
 * HTTP 409 Conflict returned automatically.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
