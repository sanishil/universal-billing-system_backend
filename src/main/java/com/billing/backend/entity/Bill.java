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

/**
 * Bill entity — maps to the "bills" table in PostgreSQL.
 *
 * This is the most complex entity. Key concepts:
 *
 * GST LOGIC:
 *   - isInterState = false (same state) → CGST + SGST (each = tax/2)
 *   - isInterState = true  (diff state) → IGST (= full tax)
 *
 * ITEMS:
 *   - Stored in a separate "bill_items" table
 *   - @OneToMany → one bill has MANY items
 *   - CascadeType.ALL → when bill is saved/deleted, items are too
 *   - orphanRemoval = true → if you remove an item from the list, it's deleted from DB
 *
 * SUPPLIER GSTIN:
 *   - Always "29AABCU9603R1ZM" — injected by the server, never from user input
 */
@Entity
@Table(name = "bills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    // ── Primary Key ──────────────────────────────────────────────────────────
    // Format: INV-2026-842109
    @Id
    @Column(name = "id", length = 30)
    private String id;

    // ── Customer Reference ────────────────────────────────────────────────────
    // Just store the ID string, not a full join — keeps things simple
    @Column(name = "customer_id", nullable = false, length = 20)
    private String customerId;

    // Also store customer name directly (denormalized) for quick display
    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(name = "customer_gstin", length = 15)
    private String customerGstin;

    // ── Supplier Info (always the system's own details) ───────────────────────
    @Column(name = "supplier_gstin", nullable = false, length = 15)
    @Builder.Default
    private String supplierGstin = "29AABCU9603R1ZM";

    // ── GST Location Info ─────────────────────────────────────────────────────
    @Column(name = "pan", length = 10)
    private String pan;

    // e.g. "Karnataka (29)"
    @Column(name = "place_of_supply", length = 80)
    @Builder.Default
    private String placeOfSupply = "Karnataka (29)";

    @Column(name = "state_code", length = 5)
    @Builder.Default
    private String stateCode = "29";

    // true = different state → use IGST
    // false = same state    → use CGST + SGST
    @Column(name = "is_inter_state", nullable = false)
    @Builder.Default
    private boolean isInterState = false;

    // ── Bill Items ────────────────────────────────────────────────────────────
    // @OneToMany: this bill can have many BillItems
    // mappedBy = "bill" → the BillItem.bill field is the FK side
    // CascadeType.ALL → save/delete items together with the bill
    // orphanRemoval → if item removed from list, delete it from DB
    // fetch = EAGER → load items immediately when bill is loaded
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BillItem> items;

    // ── GST Rate ──────────────────────────────────────────────────────────────
    // Must be one of: 0, 5, 12, 18, 28
    @Column(name = "gst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal gstRate = new BigDecimal("18.00");

    // ── Computed Financial Fields (all server-side, never trust client) ────────
    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;

    // CGST = Central GST (half of total tax, for intra-state)
    @Column(name = "cgst", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal cgst = BigDecimal.ZERO;

    // SGST = State GST (half of total tax, for intra-state)
    @Column(name = "sgst", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal sgst = BigDecimal.ZERO;

    // IGST = Integrated GST (full tax, for inter-state)
    @Column(name = "igst", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal igst = BigDecimal.ZERO;

    // Total tax = cgst + sgst OR igst
    @Column(name = "tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal tax;

    // Grand total = subtotal + tax
    @Column(name = "total", nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    // Indian words: "Rupees Seventy Six Thousand Seven Hundred Only"
    @Column(name = "amount_in_words", nullable = false, columnDefinition = "TEXT")
    private String amountInWords;

    // ── Currency & Status ─────────────────────────────────────────────────────
    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BillStatus status = BillStatus.PENDING;

    // ── Public Share Link ─────────────────────────────────────────────────────
    // Auto-generated slug, e.g. "bill-infosysdigitalsystemsltd-842109"
    // UNIQUE: no two bills can share the same link
    @Column(name = "unique_link", unique = true, nullable = false, length = 150)
    private String uniqueLink;

    // ── Dates ────────────────────────────────────────────────────────────────
    @Column(name = "created_at", updatable = false)
    private LocalDate createdAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Optional Notes ────────────────────────────────────────────────────────
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

    // ── Allowed Bill Statuses ─────────────────────────────────────────────────
    public enum BillStatus {
        PENDING,  // Default: awaiting payment
        PAID,     // Payment received
        OVERDUE   // Past due date, not yet paid
    }
}
