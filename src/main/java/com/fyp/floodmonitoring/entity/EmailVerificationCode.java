package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores time-limited 6-digit codes used during the registration flow
 * to prove that a new user owns the email they signed up with. Mirrors
 * {@link PasswordResetCode} — same shape, separate table so the two
 * flows can't collide (a single user might be in both at once).
 */
@Entity
@Table(name = "email_verification_codes")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class EmailVerificationCode {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
