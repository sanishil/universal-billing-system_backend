package com.billing.backend.repository;

import com.billing.backend.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PasswordResetTokenRepository — queries for the "password_reset_tokens" table.
 *
 * The primary key IS the token string itself (a 64-char hex string).
 * So findById(token) looks up the token directly.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    // ── Cleanup: Delete Expired Tokens ────────────────────────────────────────
    // @Modifying: required for UPDATE/DELETE @Query methods
    // @Transactional: required when modifying data
    // Runs periodically to keep the table clean
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);

    // ── Find Active Token for a User ──────────────────────────────────────────
    // Checks if user already has a valid (not used, not expired) reset token
    @Query("SELECT COUNT(t) > 0 FROM PasswordResetToken t " +
           "WHERE t.userId = :userId AND t.used = false AND t.expiresAt > :now")
    boolean hasActiveToken(@Param("userId") String userId,
                           @Param("now") LocalDateTime now);

    // ── Delete all tokens for a user (after password reset) ───────────────────
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);
}
