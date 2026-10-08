package com.billing.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Please enter your username or email")
    private String username;

    @NotBlank(message = "Please enter your password")
    private String password;

    private String captchaToken;
}
