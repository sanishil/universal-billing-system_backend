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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public User login(String usernameOrEmail, String password) {
        Optional<User> userOpt;
        if (usernameOrEmail.contains("@")) {
            userOpt = userRepository.findByEmail(usernameOrEmail);
        } else {
            userOpt = userRepository.findByUsername(usernameOrEmail);
        }

        if (userOpt.isEmpty()) {
            if (usernameOrEmail.contains("@")) {
                throw new BadRequestException(
                    "No account found with email \"" + usernameOrEmail + "\". Please check your email or register.");
            } else {
                throw new BadRequestException(
                    "No account found with username \"" + usernameOrEmail + "\". Please check your username or register.");
            }
        }

        User user = userOpt.get();

        if (user.getStatus() == User.UserStatus.LOCKED) {
            throw new BadRequestException(
                "Your account has been locked after too many failed attempts. Please contact support.");
        }
        if (user.getStatus() == User.UserStatus.INACTIVE) {
            throw new BadRequestException(
                "Your account is not activated yet. Please check your email for the activation link.");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getLoginAttempts() + 1;
            user.setLoginAttempts(attempts);

            if (attempts >= 5) {
                user.setStatus(User.UserStatus.LOCKED);
                userRepository.save(user);
                throw new BadRequestException(
                    "Your account has been locked after 5 failed attempts. Please contact support.");
            }

            int remaining = 5 - attempts;
            userRepository.save(user);
            throw new BadRequestException(
                "Incorrect password. " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining before your account is locked.");
        }

        user.setLoginAttempts(0);
        userRepository.save(user);

        return user;
    }

    public User register(String name, String email, String company, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException(
                "An account with email \"" + email + "\" already exists. Please login or use a different email.");
        }

        if (password == null || password.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters long.");
        }

        long count = userRepository.count();
        String userId = "USR-" + String.format("%02d", count + 1);

        String hashedPassword = passwordEncoder.encode(password);

        User newUser = User.builder()
                .id(userId)
                .name(name)
                .email(email)
                .username(email.split("@")[0])
                .passwordHash(hashedPassword)
                .role("Administrator")
                .status(User.UserStatus.ACTIVE)
                .loginAttempts(0)
                .build();

        return userRepository.save(newUser);
    }

    public String generateToken(User user) {
        return jwtUtil.generateToken(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    public Optional<String> createPasswordResetToken(String emailOrUsername) {
        Optional<User> userOpt = emailOrUsername.contains("@")
                ? userRepository.findByEmail(emailOrUsername)
                : userRepository.findByUsername(emailOrUsername);

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();

        SecureRandom random = new SecureRandom();
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .id(token)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(1))
                .used(false)
                .build();

        tokenRepository.save(resetToken);
        return Optional.of(token);
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findById(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        if (!resetToken.isValid()) {
            throw new BadRequestException("Invalid or expired reset token");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new BadRequestException("Password must be at least 8 characters");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", resetToken.getUserId()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLoginAttempts(0);
        user.setStatus(User.UserStatus.ACTIVE);
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    public User.UserStatus getAccountStatus(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(User::getStatus)
                .orElseThrow(() -> new ResourceNotFoundException("No account found for email: " + email));
    }
}
