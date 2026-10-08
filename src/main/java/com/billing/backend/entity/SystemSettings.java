package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemSettings {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = new BigDecimal("18.00");

    @Column(name = "tax_label", length = 20)
    @Builder.Default
    private String taxLabel = "GST";

    @Column(name = "invoice_prefix", length = 10)
    @Builder.Default
    private String invoicePrefix = "INV";

    @Column(name = "invoice_start_number")
    @Builder.Default
    private int invoiceStartNumber = 1001;

    @Column(name = "due_days")
    @Builder.Default
    private int dueDays = 30;

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

    @Column(name = "date_format", length = 20)
    @Builder.Default
    private String dateFormat = "DD/MM/YYYY";

    @Column(name = "time_zone", length = 50)
    @Builder.Default
    private String timeZone = "Asia/Kolkata";

    @Column(name = "language", length = 10)
    @Builder.Default
    private String language = "en";

    @Column(name = "email_on_payment")
    @Builder.Default
    private boolean emailOnPayment = true;

    @Column(name = "email_on_overdue")
    @Builder.Default
    private boolean emailOnOverdue = true;

    @Column(name = "email_on_new_client")
    @Builder.Default
    private boolean emailOnNewClient = false;

    @Column(name = "weekly_report")
    @Builder.Default
    private boolean weeklyReport = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
