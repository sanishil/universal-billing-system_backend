package com.billing.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler — catches ALL exceptions thrown anywhere in the app
 * and converts them to clean JSON error responses.
 *
 * WITHOUT this, Spring would return an ugly HTML error page or a raw exception.
 * WITH this, every error returns a consistent JSON structure like:
 *
 *   {
 *     "statusCode" : 404,
 *     "error"      : "Not Found",
 *     "message"    : "Customer with id 'CUST-999' not found",
 *     "timestamp"  : "2026-10-04T07:00:00Z"
 *   }
 *
 * @RestControllerAdvice → applies to ALL @RestController classes globally
 * @ExceptionHandler(SomeException.class) → handles that specific exception type
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404: Resource Not Found ───────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), null);
    }

    // ── 400: Bad Request (Business Logic Errors) ──────────────────────────────
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage(), null);
    }

    // ── 422: Validation Errors (@Valid on DTO fields) ─────────────────────────
    // This fires when @NotBlank, @Email, @Size etc. constraints are violated.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {

        // Collect all field-level errors into a list
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> {
                    Map<String, String> error = new HashMap<>();
                    error.put("field", fieldError.getField());
                    error.put("message", fieldError.getDefaultMessage());
                    return error;
                })
                .collect(Collectors.toList());

        Map<String, Object> body = new HashMap<>();
        body.put("statusCode", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("error", "Validation Error");
        body.put("errors", errors);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    // ── 409: Conflict (Duplicate email, duplicate link, etc.) ─────────────────
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), null);
    }

    // ── 500: Any other unexpected exception ───────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        // Log the full stack trace for debugging (use a logger in production)
        ex.printStackTrace();
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                null
        );
    }

    // ── Helper: Build a consistent error response body ────────────────────────
    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status,
            String error,
            String message,
            String field) {

        Map<String, Object> body = new HashMap<>();
        body.put("statusCode", status.value());
        body.put("error", error);
        body.put("message", message);
        if (field != null) {
            body.put("field", field);
        }
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(status).body(body);
    }
}
