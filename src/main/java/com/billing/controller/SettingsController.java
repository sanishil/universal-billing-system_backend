package com.billing.controller;

import com.billing.backend.entity.SystemSettings;
import com.billing.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SettingsController — profile settings and system configuration endpoints.
 *
 * Authentication object:
 *   Spring injects the "Authentication" parameter automatically.
 *   authentication.getName() returns the userId stored in the JWT (the "sub" claim).
 *   We use this to fetch/update the correct user's profile.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    // ── GET /api/settings/profile ──────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        // authentication.getName() = userId from JWT (set by JwtAuthFilter)
        String userId = authentication.getName();
        return ResponseEntity.ok(settingsService.getProfile(userId));
    }

    // ── PUT /api/settings/profile ──────────────────────────────────────────────
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication authentication,
            @RequestBody Map<String, Object> profileData) {
        String userId = authentication.getName();
        return ResponseEntity.ok(settingsService.updateProfile(userId, profileData));
    }

    // ── GET /api/settings/system ───────────────────────────────────────────────
    @GetMapping("/system")
    public ResponseEntity<SystemSettings> getSystemSettings() {
        return ResponseEntity.ok(settingsService.getSystemSettings());
    }

    // ── PUT /api/settings/system ───────────────────────────────────────────────
    @PutMapping("/system")
    public ResponseEntity<SystemSettings> updateSystemSettings(
            @RequestBody SystemSettings updatedSettings) {
        return ResponseEntity.ok(settingsService.updateSystemSettings(updatedSettings));
    }
}
