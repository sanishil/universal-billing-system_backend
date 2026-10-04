package com.billing.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for POST /api/auth/forgot-password */
@Data
public class ForgotPasswordRequest {

    @NotBlank(message = "Email or username is required")
    private String emailOrUsername;
}
