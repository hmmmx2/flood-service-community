package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Per-user notification and app settings (push, SMS, email, low-data mode).
 * Unique constraint on (user_id, key) prevents duplicate setting entries.
 */
@Entity
@Table(name = "user_settings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "key"}))
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class UserSetting {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String key;

    @Builder.Default
    private Boolean enabled = false;
}
