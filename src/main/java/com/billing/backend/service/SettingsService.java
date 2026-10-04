package com.billing.backend.service;

import com.billing.backend.entity.SystemSettings;
import com.billing.backend.entity.User;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.SystemSettingsRepository;
import com.billing.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SettingsService — manages profile settings and system configuration.
 *
 * Profile settings: per-user (GET/PUT /api/settings/profile)
 * System settings:  global singleton (GET/PUT /api/settings/system)
 *
 * System settings is a singleton table — always exactly 1 row (id = 1).
 * On first GET, if no row exists, we create the default row.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    // ── PROFILE SETTINGS ──────────────────────────────────────────────────────

    /**
     * Get the profile settings for a user.
     * Returns a map matching the ProfileSettings object in the frontend.
     */
    public Map<String, Object> getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Split name into firstName / lastName for the frontend
        String[] parts = user.getName().split(" ", 2);
        String firstName = parts[0];
        String lastName  = parts.length > 1 ? parts[1] : "";

        // Compute initials (e.g. "JD" for "John Doe")
        String initials = (firstName.isEmpty() ? "" : String.valueOf(firstName.charAt(0)).toUpperCase())
                        + (lastName.isEmpty()  ? "" : String.valueOf(lastName.charAt(0)).toUpperCase());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("firstName", firstName);
        profile.put("lastName",  lastName);
        profile.put("name",      user.getName());
        profile.put("initials",  initials);
        profile.put("email",     user.getEmail());
        profile.put("phone",     user.getUsername() != null ? user.getUsername() : "");
        profile.put("company",   "");
        profile.put("gstin",     "");
        profile.put("avatarUrl", user.getAvatarUrl());
        return profile;
    }

    /**
     * Update profile settings for a user.
     */
    public Map<String, Object> updateProfile(String userId, Map<String, Object> profileData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Build the full name from firstName + lastName
        String firstName = (String) profileData.getOrDefault("firstName", "");
        String lastName  = (String) profileData.getOrDefault("lastName", "");
        if (StringUtils.hasText(firstName) || StringUtils.hasText(lastName)) {
            user.setName((firstName + " " + lastName).trim());
        }

        // Update email if provided
        if (profileData.containsKey("email") &&
            StringUtils.hasText((String) profileData.get("email"))) {
            user.setEmail((String) profileData.get("email"));
        }

        userRepository.save(user);
        return getProfile(userId);
    }

    // ── SYSTEM SETTINGS ───────────────────────────────────────────────────────

    /**
     * Get the global system settings.
     * If no settings row exists yet, creates default settings first.
     */
    public SystemSettings getSystemSettings() {
        return settingsRepository.findById(1)
                .orElseGet(this::createDefaultSettings);
    }

    /**
     * Update the global system settings.
     * Merges submitted values over the existing row (partial update).
     */
    public SystemSettings updateSystemSettings(SystemSettings updatedSettings) {
        SystemSettings existing = getSystemSettings();

        // Update only fields that were submitted (not null)
        if (StringUtils.hasText(updatedSettings.getCurrency())) {
            existing.setCurrency(updatedSettings.getCurrency());
        }
        if (updatedSettings.getTaxRate() != null) {
            existing.setTaxRate(updatedSettings.getTaxRate());
        }
        if (StringUtils.hasText(updatedSettings.getTaxLabel())) {
            existing.setTaxLabel(updatedSettings.getTaxLabel());
        }
        if (StringUtils.hasText(updatedSettings.getInvoicePrefix())) {
            existing.setInvoicePrefix(updatedSettings.getInvoicePrefix());
        }
        if (updatedSettings.getDueDays() > 0) {
            existing.setDueDays(updatedSettings.getDueDays());
        }
        if (StringUtils.hasText(updatedSettings.getCompanyName())) {
            existing.setCompanyName(updatedSettings.getCompanyName());
        }
        if (StringUtils.hasText(updatedSettings.getCompanyEmail())) {
            existing.setCompanyEmail(updatedSettings.getCompanyEmail());
        }
        if (StringUtils.hasText(updatedSettings.getCompanyPhone())) {
            existing.setCompanyPhone(updatedSettings.getCompanyPhone());
        }
        if (StringUtils.hasText(updatedSettings.getCompanyAddress())) {
            existing.setCompanyAddress(updatedSettings.getCompanyAddress());
        }
        if (StringUtils.hasText(updatedSettings.getCompanyGstin())) {
            existing.setCompanyGstin(updatedSettings.getCompanyGstin());
        }
        if (StringUtils.hasText(updatedSettings.getCompanyWebsite())) {
            existing.setCompanyWebsite(updatedSettings.getCompanyWebsite());
        }

        // Boolean fields are always updated if present in payload
        existing.setEmailOnPayment(updatedSettings.isEmailOnPayment());
        existing.setEmailOnOverdue(updatedSettings.isEmailOnOverdue());
        existing.setEmailOnNewClient(updatedSettings.isEmailOnNewClient());
        existing.setWeeklyReport(updatedSettings.isWeeklyReport());

        return settingsRepository.save(existing);
    }

    // ── HELPER: Create default settings row ───────────────────────────────────

    /**
     * Creates the initial system settings row with all defaults.
     * Called on first startup if the table is empty.
     */
    private SystemSettings createDefaultSettings() {
        SystemSettings defaults = SystemSettings.builder()
                .id(1)  // Always ID = 1 (singleton)
                .build();  // @Builder.Default values kick in for everything else
        return settingsRepository.save(defaults);
    }
}
