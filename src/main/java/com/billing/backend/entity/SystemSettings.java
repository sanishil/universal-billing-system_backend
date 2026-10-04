package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SystemSettings entity — maps to the "system_settings" table.
 *
 * This is a SINGLETON table — it always has exactly ONE row (id = 1).
 * Think of it as a config file stored in the database.
 *
 * Covers:
 *  - Billing & Tax defaults (currency, GST rate, invoice prefix)
 *  - Company Info (name, email, address, GSTIN)
 *  - Regional settings (date format, timezone, language)
 *  - Notification triggers (when to auto-send emails)
 */
@Entity
@Table(name = "system_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettings {

    // Always id = 1 (singleton)
    @Id
    @Column(name = "id")
    private Integer id;

    // ── Billing & Tax ─────────────────────────────────────────────────────────
    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("18.00");

    @Column(name = "tax_label", length = 20)
    @Builder.Default
    private String taxLabel = "GST";

    // Invoice number prefix, e.g. "INV" → generates INV-2026-001
    @Column(name = "invoice_prefix", length = 10)
    @Builder.Default
    private String invoicePrefix = "INV";

    // Starting number for invoice sequence
    @Column(name = "invoice_start_number")
    @Builder.Default
    private int invoiceStartNumber = 1001;

    // Default days until invoice is due
    @Column(name = "due_days")
    @Builder.Default
    private int dueDays = 30;

    // ── Company Info ──────────────────────────────────────────────────────────
    @Column(name = "company_name", length = 150)
    @Builder.Default
    private String companyName = "Universal Billing Pvt. Ltd.";

    @Column(name = "company_email", length = 150)
    @Builder.Default
    private String companyEmail = "billing@universalbilling.in";

    @Column(name = "company_phone", length = 30)
    @Builder.Default
    private String companyPhone = "+91 98765 43210";

    @Column(name = "company_address", columnDefinition = "TEXT")
    @Builder.Default
    private String companyAddress = "12, Tech Park, Pune, Maharashtra - 411014";

    @Column(name = "company_gstin", length = 15)
    @Builder.Default
    private String companyGstin = "29AABCU9603R1ZM";

    @Column(name = "company_website", length = 100)
    @Builder.Default
    private String companyWebsite = "https://universalbilling.in";

    // ── Appearance & Regional ─────────────────────────────────────────────────
    @Column(name = "date_format", length = 20)
    @Builder.Default
    private String dateFormat = "DD/MM/YYYY";

    @Column(name = "time_zone", length = 50)
    @Builder.Default
    private String timeZone = "Asia/Kolkata";

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "en";

    // ── Notification Trigger Flags ────────────────────────────────────────────
    // Send email when a bill is marked PAID
    @Column(name = "email_on_payment")
    @Builder.Default
    private boolean emailOnPayment = true;

    // Send email reminder when a bill becomes OVERDUE
    @Column(name = "email_on_overdue")
    @Builder.Default
    private boolean emailOnOverdue = true;

    // Send alert to admin when a new customer is added
    @Column(name = "email_on_new_client")
    @Builder.Default
    private boolean emailOnNewClient = false;

    // Send weekly summary report email
    @Column(name = "weekly_report")
    @Builder.Default
    private boolean weeklyReport = true;

    // ── Timestamps ───────────────────────────────────────────────────────────
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
