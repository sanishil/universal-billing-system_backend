package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * PasswordResetToken entity — maps to the "password_reset_tokens" table.
 *
 * Flow:
 *   1. User clicks "Forgot Password" → POST /api/auth/forgot-password
 *   2. Server generates a 32-byte random hex token
 *   3. Token is stored here with expiresAt = now + 1 hour
 *   4. Email sent to user with link: /reset-password?token=XYZ
 *   5. User clicks link → POST /api/auth/reset-password
 *   6. Server looks up token, checks not expired, not used
 *   7. Password is updated, token is marked used = true
 */
@Entity
@Table(name = "password_reset_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    // The token itself is the PK (it's unique by nature)
    @Id
    @Column(name = "id", length = 100)
    private String id;

    // Which user this token belongs to
    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    // Token becomes invalid after this time (1 hour from creation)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Once the password is reset, mark this token as used
    // so it can't be reused
    @Column(name = "used", nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Convenience method: check if this token is still valid
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }
}
