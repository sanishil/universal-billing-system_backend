package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One email of a bill to its customer; shown in the bill's email history */
@Entity
@Table(name = "bill_email_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, length = 30)
    private String billId;

    @Column(name = "to_email", nullable = false, length = 150)
    private String toEmail;

    /** BILL, or BILL_WITH_INVITE when the customer was also given a login */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    /** SENT or FAILED */
    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
