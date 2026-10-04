package com.billing.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ENTITY = A Java class that maps to a database TABLE.
 *
 * @Entity      → Tells JPA "this class is a database table"
 * @Table       → Specifies the exact table name in PostgreSQL
 * @Id          → This field is the PRIMARY KEY
 * @Column      → Maps a field to a specific column name/constraint
 *
 * Lombok annotations:
 * @Data         → Auto-generates: getters, setters, toString, equals, hashCode
 * @Builder      → Lets you create objects like: User.builder().name("John").build()
 * @NoArgsConstructor → Generates empty constructor: new User()
 * @AllArgsConstructor → Generates constructor with all fields
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // ── Primary Key ──────────────────────────────────────────────────────────
    // Format: USR-01, USR-02, USR-03
    @Id
    @Column(name = "id", length = 20)
    private String id;

    // ── Basic Info ───────────────────────────────────────────────────────────
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    // nullable = false means NOT NULL in the database
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    // Optional: user can login with "admin" instead of full email
    @Column(name = "username", unique = true, length = 50)
    private String username;

    // The bcrypt hash of the password (never store plain passwords!)
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    // Role: "Administrator", "Accountant", etc.
    @Column(name = "role", nullable = false, length = 50)
    @Builder.Default
    private String role = "Administrator";

    // Profile picture URL (optional)
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    // ── Account Status ───────────────────────────────────────────────────────
    // @Enumerated(EnumType.STRING) → stores "ACTIVE" as text in DB, not a number
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    // Tracks failed login attempts — locks after 5 failures
    @Column(name = "login_attempts", nullable = false)
    @Builder.Default
    private int loginAttempts = 0;

    // ── Timestamps ───────────────────────────────────────────────────────────
    // updatable = false → createdAt is set once and never changed
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── JPA Lifecycle Callbacks ──────────────────────────────────────────────
    // @PrePersist runs automatically BEFORE inserting a new record into DB
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // @PreUpdate runs automatically BEFORE updating an existing record
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ── Inner Enum: Allowed Status Values ────────────────────────────────────
    public enum UserStatus {
        ACTIVE,    // Can log in normally
        INACTIVE,  // Account not activated yet
        LOCKED     // Too many failed login attempts
    }
}
