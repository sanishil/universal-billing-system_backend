package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A business owned by a user. One user can run several businesses;
 * each customer is created under one of them.
 */
@Entity
@Table(name = "businesses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Business {

    @Id
    @Column(name = "id", length = 20)
    private String id;

    /** Owner — the logged-in user's id from the JWT */
    @Column(name = "user_id", length = 20, nullable = false)
    private String userId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    /** Supplier state for GST on bills issued by this business */
    @Column(name = "state_code", length = 5)
    private String stateCode;

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
}
