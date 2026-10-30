package com.billing.backend.service;

import com.billing.backend.entity.SystemSettings;
import com.billing.backend.entity.User;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ConflictException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.SystemSettingsRepository;
import com.billing.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // GST slabs in force from 22 Sept 2025. Special rates (e.g. 3 for gold) can be added via settings.
    public static final List<BigDecimal> DEFAULT_GST_RATES = List.of(
            new BigDecimal("0"), new BigDecimal("5"), new BigDecimal("18"), new BigDecimal("40"));

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
        profile.put("phone",     user.getPhone() != null ? user.getPhone() : "");
        profile.put("role",      user.getRole());
        profile.put("status",    user.getStatus().name());
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

        String email = (String) profileData.get("email");
        if (StringUtils.hasText(email)) {
            email = email.trim().toLowerCase();
            if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new BadRequestException("Invalid email format");
            }
            if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new ConflictException("Another account already uses this email");
            }
            user.setEmail(email);
        }

        String phone = normalizePhone((String) profileData.get("phone"));
        if (StringUtils.hasText(phone)) {
            if (!phone.matches("^[6-9][0-9]{9}$")) {
                throw new BadRequestException("Phone must be a 10-digit Indian mobile number");
            }
            if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
                throw new ConflictException("Another account already uses this phone number");
            }
            user.setPhone(phone);
        }

        userRepository.save(user);
        return getProfile(userId);
    }

    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("New password must be at least 8 characters");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /** Keeps digits only and drops a leading +91 / 91 / 0 */
    private String normalizePhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        return digits;
    }

    public SystemSettings getSystemSettings() {
        SystemSettings settings = settingsRepository.findById(1)
                .orElseGet(this::createDefaultSettings);
        if (settings.getGstRates() == null || settings.getGstRates().isEmpty()) {
            settings.setGstRates(new ArrayList<>(DEFAULT_GST_RATES));
        }
        return settings;
    }

    public List<BigDecimal> getAllowedGstRates() {
        return getSystemSettings().getGstRates();
    }

    /** Rate used when a new bill doesn't send gstRate at all. */
    public BigDecimal getDefaultGstRate() {
        return getSystemSettings().getTaxRate();
    }

    public SystemSettings updateSystemSettings(SystemSettings updatedSettings) {
        SystemSettings existing = getSystemSettings();

        if (StringUtils.hasText(updatedSettings.getCurrency())) {
            existing.setCurrency(updatedSettings.getCurrency());
        }
        if (updatedSettings.getTaxRate() != null) {
            existing.setTaxRate(updatedSettings.getTaxRate());
        }
        if (updatedSettings.getGstRates() != null && !updatedSettings.getGstRates().isEmpty()) {
            for (BigDecimal rate : updatedSettings.getGstRates()) {
                if (rate == null || rate.compareTo(BigDecimal.ZERO) < 0
                        || rate.compareTo(new BigDecimal("100")) > 0) {
                    throw new BadRequestException("gstRates must be between 0 and 100");
                }
            }
            existing.setGstRates(updatedSettings.getGstRates().stream()
                    .map(BigDecimal::stripTrailingZeros)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toCollection(ArrayList::new)));
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
