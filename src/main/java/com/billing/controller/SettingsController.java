package com.billing.controller;

import com.billing.backend.entity.Business;
import com.billing.backend.entity.SystemSettings;
import com.billing.backend.service.BusinessService;
import com.billing.backend.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    private final BusinessService businessService;

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

    // ── POST /api/settings/password ────────────────────────────────────────────
    // Body: { "currentPassword": "...", "newPassword": "..." }
    @PostMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        settingsService.changePassword(
                authentication.getName(), body.get("currentPassword"), body.get("newPassword"));
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }

    // ── GET /api/settings/businesses ───────────────────────────────────────────
    // The logged-in user's businesses, oldest first (the first one is the default)
    @GetMapping("/businesses")
    public ResponseEntity<List<Business>> getBusinesses(Authentication authentication) {
        return ResponseEntity.ok(businessService.getBusinesses(authentication.getName()));
    }

    // ── POST /api/settings/businesses ──────────────────────────────────────────
    @PostMapping("/businesses")
    public ResponseEntity<Business> createBusiness(
            Authentication authentication,
            @RequestBody Business business) {
        Business created = businessService.createBusiness(authentication.getName(), business);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /api/settings/businesses/:id ───────────────────────────────────────
    @PutMapping("/businesses/{id}")
    public ResponseEntity<Business> updateBusiness(
            Authentication authentication,
            @PathVariable String id,
            @RequestBody Business business) {
        return ResponseEntity.ok(businessService.updateBusiness(id, authentication.getName(), business));
    }

    // ── DELETE /api/settings/businesses/:id ────────────────────────────────────
    @DeleteMapping("/businesses/{id}")
    public ResponseEntity<Map<String, Object>> deleteBusiness(
            Authentication authentication,
            @PathVariable String id) {
        businessService.deleteBusiness(id, authentication.getName());
        return ResponseEntity.ok(Map.of("deleted", true));
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
