package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * In-app notification row — drives the bell icon + dropdown in the
 * Community / CRM web frontends. Rows are inserted by the
 * {@link com.fyp.floodmonitoring.service.notifications.NotificationDispatcher}
 * when a flood alert fans out, and trimmed periodically (last 200 per user).
 *
 * `kind` examples: "flood.alert.watch", "flood.alert.warning",
 * "flood.alert.critical". Front-end keys off this for icon + tone.
 */
@Entity
@Table(name = "user_notifications", indexes = {
    @Index(name = "idx_user_notifications_user_created",
           columnList = "user_id, created_at DESC"),
    @Index(name = "idx_user_notifications_user_unread",
           columnList = "user_id, read_at"),
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class UserNotification {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Machine-readable type, e.g. "flood.alert.warning". */
    @Column(nullable = false, length = 64)
    private String kind;

    /** Short headline. */
    @Column(nullable = false, length = 200)
    private String title;

    /** Optional body / message — can be a one-line summary or longer. */
    @Column(length = 1000)
    private String body;

    /** Optional deep-link path, e.g. "/flood-map" or "/flood-map?focus=NODE_ID". */
    @Column(length = 500)
    private String link;

    /** "info" | "warning" | "critical" — drives styling on the frontend. */
    @Column(nullable = false, length = 16)
    private String severity;

    /** Null until the user opens the bell dropdown / clicks the row. */
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
