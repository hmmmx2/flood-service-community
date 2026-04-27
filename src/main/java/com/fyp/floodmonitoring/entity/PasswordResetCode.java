package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores time-limited 6-digit password reset codes.
 * A code must be {@link #verified} before the password can be changed.
 */
@Entity
@Table(name = "password_reset_codes")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class PasswordResetCode {

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
    private Boolean verified = false;

    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
