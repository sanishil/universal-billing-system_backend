package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Customer entity — maps to the "customers" table in PostgreSQL.
 *
 * Key fields:
 *  - gstin: India's GST Identification Number (15 chars)
 *  - pan: Permanent Account Number (10 chars)
 *  - totalSpent: Updated automatically when bills are paid
 *  - billsCount: Incremented when a new bill is created for this customer
 */
@Entity
@Table(name = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    // ── Primary Key ──────────────────────────────────────────────────────────
    // Format: CUST-001, CUST-002
    @Id
    @Column(name = "id", length = 20)
    private String id;

    // ── Required Contact Info ─────────────────────────────────────────────────
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone", nullable = false, length = 30)
    private String phone;

    // columnDefinition = "TEXT" → unlimited length (for long addresses)
    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    // ── Optional Business Info ────────────────────────────────────────────────
    @Column(name = "company", length = 150)
    private String company;

    // GSTIN = Goods and Services Tax Identification Number
    // Format: 2 digits + 5 alpha + 4 digits + 1 alpha + 1Z + 1 alphanumeric
    // Example: 29AAACI4321A1ZG
    @Column(name = "gstin", length = 15)
    private String gstin;

    // PAN = Permanent Account Number
    // Format: AAAAA9999A (5 letters + 4 digits + 1 letter)
    @Column(name = "pan", length = 10)
    private String pan;

    // Indian state name, e.g. "Karnataka", "Maharashtra"
    @Column(name = "state", length = 50)
    private String state;

    // 2-digit state code used for GST inter-state detection
    // Karnataka = "29", Maharashtra = "27", etc.
    @Column(name = "state_code", length = 5)
    @Builder.Default
    private String stateCode = "29";

    // ── Computed/Aggregate Fields ─────────────────────────────────────────────
    // BigDecimal is used for money — never use float/double for currency!
    // precision=15 → up to 15 digits, scale=2 → 2 decimal places
    @Column(name = "total_spent", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    // Count of bills created for this customer
    @Column(name = "bills_count", nullable = false)
    @Builder.Default
    private int billsCount = 0;

    // ── Status ───────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    // ── Timestamps ───────────────────────────────────────────────────────────
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum CustomerStatus {
        ACTIVE,
        INACTIVE
    }
}
