package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notification entity — maps to the "notifications" table.
 *
 * Tracks every email or SMS sent by the system.
 * Auto-triggered events:
 *   - Bill marked PAID   → email to customer
 *   - Bill OVERDUE       → email reminder
 *   - 7 days before due  → reminder email
 *   - New customer added → email to admin (if setting enabled)
 */
@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    // ── Primary Key ──────────────────────────────────────────────────────────
    // Format: NOTIF-001, NOTIF-002
    @Id
    @Column(name = "id", length = 20)
    private String id;

    // ── Notification Type ─────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private NotificationType type;

    // Email address OR phone number of the recipient
    @Column(name = "recipient", nullable = false, length = 150)
    private String recipient;

    // The actual message body sent
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    // ── Delivery Status ───────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    // Human-readable sent timestamp, e.g. "4 Oct 2026, 12:30 am"
    @Column(name = "sent_at", length = 80)
    private String sentAt;

    // ── Timestamps ───────────────────────────────────────────────────────────
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Enums ─────────────────────────────────────────────────────────────────
    public enum NotificationType {
        EMAIL,
        SMS
    }

    public enum NotificationStatus {
        SENT,
        FAILED,
        PENDING
    }
}
