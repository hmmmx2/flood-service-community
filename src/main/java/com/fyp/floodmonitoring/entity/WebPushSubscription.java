package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists a browser Web Push subscription for a user.
 * One user may have multiple subscriptions (multiple devices/browsers).
 */
@Entity
@Table(
    name = "web_push_subscriptions",
    uniqueConstraints = @UniqueConstraint(name = "uq_web_push_endpoint", columnNames = "endpoint")
)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class WebPushSubscription {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 512)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 256)
    private String p256dh;

    @Column(name = "auth_key", nullable = false, length = 128)
    private String authKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
