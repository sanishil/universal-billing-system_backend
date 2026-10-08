package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    @Column(name = "bill_id", nullable = false, length = 30)
    private String billId;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 5)
    @Builder.Default
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private PaymentMethod method;

    @Column(name = "upi_id", length = 100)
    private String upiId;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "utr_number", length = 50)
    private String utrNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.SUCCESS;

    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @Column(name = "date", nullable = false, length = 80)
    private String date;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum PaymentMethod {
        UPI,
        NET_BANKING,
        RUPAY_CARD,
        NEFT_RTGS,
        CREDIT_CARD,
        BANK_TRANSFER,
        PAYPAL,
        STRIPE
    }

    public enum PaymentStatus {
        SUCCESS,
        FAILED,
        PENDING
    }
}
