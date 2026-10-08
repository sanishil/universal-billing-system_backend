package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "bills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @Column(name = "id", length = 30)
    private String id;

    @Column(name = "customer_id", nullable = false, length = 20)
    private String customerId;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_gstin", length = 15)
    private String customerGstin;

    @Column(name = "supplier_gstin", nullable = false, length = 15)
    @Builder.Default
    private String supplierGstin = "29AABCU9603R1ZM";

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "place_of_supply", length = 80)
    @Builder.Default
    private String placeOfSupply = "Karnataka (29)";

    @Column(name = "state_code", length = 5)
    @Builder.Default
    private String stateCode = "29";

    @Column(name = "is_inter_state", nullable = false)
    @Builder.Default
    private boolean isInterState = false;

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BillItem> items;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = new BigDecimal("18.00");

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "cgst", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cgst = BigDecimal.ZERO;

    @Column(name = "sgst", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal sgst = BigDecimal.ZERO;

    @Column(name = "igst", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal igst = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal tax;

    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(name = "amount_in_words", nullable = false, columnDefinition = "TEXT")
    private String amountInWords;

    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BillStatus status = BillStatus.PENDING;

    @Column(name = "unique_link", unique = true, nullable = false, length = 150)
    private String uniqueLink;

    @Column(name = "created_at", updatable = false)
    private LocalDate createdAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDate.now();
        }
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum BillStatus {
        PENDING,
        PAID,
        OVERDUE
    }
}
