package com.billing.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LoginRequest — the request body for POST /api/auth/login
 *
 * DTO = Data Transfer Object.
 * It's a simple class that holds the data coming in from the HTTP request body.
 * It is NOT stored in the database — it's only used to carry data between
 * the client (Angular) and the controller.
 *
 * @NotBlank → validation annotation: field cannot be null, empty, or whitespace.
 *             If violated, Spring returns a 422 error automatically.
 *
 * The "username" field accepts BOTH a username ("admin") OR an email ("admin@ubs.io").
 * The AuthService checks whether it contains "@" to decide which one it is.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Please enter your username or email")
    private String username;   // Can be username OR email — matches frontend field name

    @NotBlank(message = "Please enter your password")
    private String password;
}
