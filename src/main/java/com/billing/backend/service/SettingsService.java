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

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingsRepository settingsRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String[] parts = user.getName().split(" ", 2);
        String firstName = parts[0];
        String lastName  = parts.length > 1 ? parts[1] : "";

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

    public Map<String, Object> updateProfile(String userId, Map<String, Object> profileData) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String firstName = (String) profileData.getOrDefault("firstName", "");
        String lastName  = (String) profileData.getOrDefault("lastName", "");
        if (StringUtils.hasText(firstName) || StringUtils.hasText(lastName)) {
            user.setName((firstName + " " + lastName).trim());
        }

        if (profileData.containsKey("email") &&
            StringUtils.hasText((String) profileData.get("email"))) {
            user.setEmail((String) profileData.get("email"));
        }

        userRepository.save(user);
        return getProfile(userId);
    }

    public SystemSettings getSystemSettings() {
        return settingsRepository.findById(1)
                .orElseGet(this::createDefaultSettings);
    }

    public SystemSettings updateSystemSettings(SystemSettings updatedSettings) {
        SystemSettings existing = getSystemSettings();

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

        existing.setEmailOnPayment(updatedSettings.isEmailOnPayment());
        existing.setEmailOnOverdue(updatedSettings.isEmailOnOverdue());
        existing.setEmailOnNewClient(updatedSettings.isEmailOnNewClient());
        existing.setWeeklyReport(updatedSettings.isWeeklyReport());

        return settingsRepository.save(existing);
    }

    private SystemSettings createDefaultSettings() {
        SystemSettings defaults = SystemSettings.builder()
                .id(1)
                .build();
        return settingsRepository.save(defaults);
    }
}
