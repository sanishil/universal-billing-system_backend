package com.billing.backend.service;

import com.billing.backend.entity.PasswordResetToken;
import com.billing.backend.entity.User;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ConflictException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.PasswordResetTokenRepository;
import com.billing.backend.repository.UserRepository;
import com.billing.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * AuthService — handles all authentication logic.
 *
 * SERVICE LAYER ROLE IN MVC:
 * ──────────────────────────
 * Controller → receives HTTP request, calls Service
 * Service    → contains the business logic (this file)
 * Repository → talks to the database
 *
 * @Service → tells Spring this is a service component
 * @RequiredArgsConstructor → Lombok generates a constructor for all "final" fields
 *                           (this is how Spring injects dependencies — called DI)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    // Spring injects these automatically (Dependency Injection)
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;  // BCrypt
    private final JwtUtil jwtUtil;

    // ── LOGIN ─────────────────────────────────────────────────────────────────

    /**
     * Authenticate a user by email/username + password.
     * Returns a User entity if login succeeds.
     *
     * @param usernameOrEmail  Can be "admin" OR "admin@ubs.io"
     * @param password         Plain text password from the login form
     * @return Authenticated User entity
     * @throws BadRequestException if credentials are wrong or account is locked
     */
    public User login(String usernameOrEmail, String password) {
        // Step 1: Find user by email or username
        Optional<User> userOpt;
        if (usernameOrEmail.contains("@")) {
            // It's an email address
            userOpt = userRepository.findByEmail(usernameOrEmail);
        } else {
            // It's a username
            userOpt = userRepository.findByUsername(usernameOrEmail);
        }

        // Step 2: If not found → generic error (don't reveal which field is wrong)
        if (userOpt.isEmpty()) {
            throw new BadRequestException("Invalid credentials");
        }

        User user = userOpt.get();

        // Step 3: Check account status
        if (user.getStatus() == User.UserStatus.LOCKED) {
            throw new BadRequestException("Account is locked. Please contact support.");
        }
        if (user.getStatus() == User.UserStatus.INACTIVE) {
            throw new BadRequestException("Account is not activated. Check your email.");
        }

        // Step 4: Verify password using BCrypt
        // passwordEncoder.matches() compares plain text with the stored hash
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // Wrong password — increment failure counter
            int attempts = user.getLoginAttempts() + 1;
            user.setLoginAttempts(attempts);

            // Lock account after 5 failed attempts (Rule 13)
            if (attempts >= 5) {
                user.setStatus(User.UserStatus.LOCKED);
            }
            userRepository.save(user);
            throw new BadRequestException("Invalid credentials");
        }

        // Step 5: Login success — reset failure counter
        user.setLoginAttempts(0);
        userRepository.save(user);

        return user;
    }

    // ── REGISTER ──────────────────────────────────────────────────────────────

    /**
     * Register a new user account.
     *
     * @param name     Full name
     * @param email    Email address (must be unique)
     * @param company  Company name (optional)
     * @param password Plain text password (will be hashed)
     * @return Newly created User entity
     */
    public User register(String name, String email, String company, String password) {
        // Check for duplicate email (Rule: 409 if already registered)
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email address is already registered");
        }

        // Validate password length (min 8 chars)
        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        // Generate user ID: USR-01, USR-02 ... USR-99, USR-100
        long count = userRepository.count();
        String userId = "USR-" + String.format("%02d", count + 1);

        // Hash the password with BCrypt (12 salt rounds per Rule 14)
        String hashedPassword = passwordEncoder.encode(password);

        // Build and save the new user
        User newUser = User.builder()
                .id(userId)
                .name(name)
                .email(email)
                .username(email.split("@")[0])  // e.g. "john" from "john@company.in"
                .passwordHash(hashedPassword)
                .role("Administrator")           // Default role
                .status(User.UserStatus.ACTIVE)
                .loginAttempts(0)
                .build();

        return userRepository.save(newUser);
    }

    // ── GENERATE JWT ──────────────────────────────────────────────────────────

    /**
     * Create a JWT token for an authenticated user.
     * Called after successful login or registration.
     */
    public String generateToken(User user) {
        return jwtUtil.generateToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    // ── FORGOT PASSWORD ───────────────────────────────────────────────────────

    /**
     * Generate a password reset token and prepare an email.
     * Always returns success (don't leak whether email exists — security rule).
     *
     * @param emailOrUsername The email or username submitted by the user
     * @return The reset token string (service caller should send this via email)
     */
    public Optional<String> createPasswordResetToken(String emailOrUsername) {
        // Find user (might not exist — that's OK, we return generic message)
        Optional<User> userOpt = emailOrUsername.contains("@")
                ? userRepository.findByEmail(emailOrUsername)
                : userRepository.findByUsername(emailOrUsername);

        if (userOpt.isEmpty()) {
            // Don't reveal that the email doesn't exist (security best practice)
            return Optional.empty();
        }

        User user = userOpt.get();

        // Generate a secure random 32-byte hex token
        // e.g. "a3f8c2d1e4b5a6f7c8d9e0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1"
        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        // Save token in DB with 1-hour expiry
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(token)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();

        tokenRepository.save(resetToken);
        return Optional.of(token);
    }

    // ── RESET PASSWORD ────────────────────────────────────────────────────────

    /**
     * Apply a new password using a valid reset token.
     *
     * @param token       The reset token from the email link
     * @param newPassword The new password to set
     */
    public void resetPassword(String token, String newPassword) {
        // Step 1: Find the token in DB
        PasswordResetToken resetToken = tokenRepository.findById(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        // Step 2: Check if it's still valid (not expired, not used)
        if (!resetToken.isValid()) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        // Step 3: Validate new password
        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        // Step 4: Update user's password
        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", resetToken.getUserId()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLoginAttempts(0); // Reset lock if applicable
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);

        // Step 5: Mark token as used (can't reuse it)
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    // ── ACCOUNT STATUS CHECK ──────────────────────────────────────────────────

    /**
     * Return the account status for a given email.
     * Used by the frontend "check account" modal.
     */
    public User.UserStatus getAccountStatus(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(User::getStatus)
                .orElseThrow(() -> new ResourceNotFoundException("No account found for email: " + email));
    }
}
