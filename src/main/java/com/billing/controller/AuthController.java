package com.billing.controller;

import com.billing.backend.dto.*;
import com.billing.backend.entity.User;
import com.billing.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * AuthController — handles all authentication HTTP endpoints.
 *
 * CONTROLLER ROLE IN MVC:
 * ───────────────────────
 * Controller receives the HTTP request → calls Service → returns HTTP response.
 * It does NOT contain business logic — that's in the Service.
 *
 * @RestController  = @Controller + @ResponseBody
 *   Means every method returns JSON directly (no HTML templates).
 *
 * @RequestMapping("/api/auth") → all methods in this class start with /api/auth
 *
 * @RequiredArgsConstructor → Lombok injects AuthService via constructor.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── POST /api/auth/login ──────────────────────────────────────────────────
    /**
     * Authenticate a user and return a JWT token + user object.
     *
     * @Valid → triggers @NotBlank validation on LoginRequest fields.
     *          If validation fails, Spring returns 422 before this method runs.
     *
     * @RequestBody → Spring deserializes the JSON body into LoginRequest.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // Step 1: Authenticate (throws BadRequestException if wrong credentials)
        User user = authService.login(request.getUsername(), request.getPassword());

        // Step 2: Generate JWT token
        String token = authService.generateToken(user);

        // Step 3: Build response (never include passwordHash!)
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();

        return ResponseEntity.ok(response);  // HTTP 200
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(
                request.getName(),
                request.getEmail(),
                request.getCompany(),
                request.getPassword()
        );

        String token = authService.generateToken(user);

        AuthResponse response = AuthResponse.builder()
                .token(token)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .build())
                .build();

        // HTTP 201 Created for new resources
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // In a stateless JWT setup, logout is handled by the frontend
        // (it deletes the token from sessionStorage).
        // For true server-side invalidation, add Redis token blacklist here.
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── POST /api/auth/forgot-password ────────────────────────────────────────
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        // Always return 200 — never reveal if email exists (security practice)
        Optional<String> token = authService.createPasswordResetToken(
                request.getEmailOrUsername());

        // TODO: Send the reset email here using NotificationService
        // notificationService.sendPasswordResetEmail(token.orElse(""));

        return ResponseEntity.ok(Map.of(
                "message", "If an account exists, reset instructions have been sent"));
    }

    // ── POST /api/auth/reset-password ─────────────────────────────────────────
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    // ── POST /api/auth/resend-activation ─────────────────────────────────────
    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, String>> resendActivation(
            @RequestBody Map<String, String> body) {
        // Stub — implement if you add email verification flow
        return ResponseEntity.ok(Map.of("message", "Activation email sent if account exists"));
    }

    // ── GET /api/auth/account-status?email=john@example.com ──────────────────
    @GetMapping("/account-status")
    public ResponseEntity<Map<String, String>> accountStatus(@RequestParam String email) {
        User.UserStatus status = authService.getAccountStatus(email);
        return ResponseEntity.ok(Map.of("status", status.name()));
    }
}
