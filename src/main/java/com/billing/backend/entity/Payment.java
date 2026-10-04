package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment entity — maps to the "payments" table.
 *
 * Supported payment methods: UPI, NET_BANKING, RUPAY_CARD, NEFT_RTGS,
 *                            CREDIT_CARD, BANK_TRANSFER, PAYPAL, STRIPE
 *
 * After a successful payment:
 *   → bill.status is set to PAID
 *   → customer.totalSpent += bill.total
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    // ── Primary Key ──────────────────────────────────────────────────────────
    // Format: PAY-8921
    @Id
    @Column(name = "id", length = 20)
    private String id;

    // ── References ───────────────────────────────────────────────────────────
    // Which bill this payment is for (FK stored as string)
    @Column(name = "bill_id", nullable = false, length = 30)
    private String billId;

    // Customer name for display (denormalized — copied from bill)
    @Column(name = "customer_name", length = 150)
    private String customerName;

    // ── Amount ───────────────────────────────────────────────────────────────
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    // ── Payment Method ────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private PaymentMethod method;

    // UPI Virtual Payment Address, e.g. "accounts@oksbi"
    // Required only when method = UPI
    @Column(name = "upi_id", length = 100)
    private String upiId;

    // Bank name for NET_BANKING or NEFT_RTGS
    // e.g. "SBI", "HDFC", "ICICI"
    @Column(name = "bank_name", length = 100)
    private String bankName;

    // UTR = Unique Transaction Reference (auto-generated)
    // e.g. "UTR8421091234"
    @Column(name = "utr_number", length = 50)
    private String utrNumber;

    // ── Status ───────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.SUCCESS;

    // Auto-generated ID: TXN-UPI-842109, TXN-NETB-842110, etc.
    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    // Human-readable date string: "4 Oct 2026, 12:30 am"
    @Column(name = "date", nullable = false, length = 80)
    private String date;

    // ── Timestamps ───────────────────────────────────────────────────────────
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Enums ─────────────────────────────────────────────────────────────────
    public enum PaymentMethod {
        UPI,           // GPay, PhonePe, Paytm
        NET_BANKING,   // SBI, HDFC, ICICI online banking
        RUPAY_CARD,    // RuPay debit/credit card
        NEFT_RTGS,     // Bank wire transfer
        CREDIT_CARD,   // Visa/Mastercard credit card
        BANK_TRANSFER, // Direct bank transfer
        PAYPAL,        // PayPal (international)
        STRIPE         // Stripe (international)
    }

    public enum PaymentStatus {
        SUCCESS,
        FAILED,
        PENDING
    }
}
