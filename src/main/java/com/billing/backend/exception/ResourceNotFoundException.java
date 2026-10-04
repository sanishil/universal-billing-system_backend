package com.billing.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource does not exist in the database.
 *
 * Examples:
 *   GET /api/customers/CUST-999 → customer not found → throw this
 *   GET /api/bills/INV-2026-999  → bill not found     → throw this
 *
 * @ResponseStatus(HttpStatus.NOT_FOUND) → Spring automatically returns
 * HTTP 404 when this exception is thrown from a controller.
 *
 * RuntimeException → we don't need to declare "throws" on every method.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    // Convenience constructor: "Customer with id CUST-999 not found"
    public ResourceNotFoundException(String resource, String id) {
        super(resource + " with id '" + id + "' not found");
    }
}
