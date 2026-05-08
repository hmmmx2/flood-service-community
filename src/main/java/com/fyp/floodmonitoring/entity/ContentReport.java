package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * User-submitted moderation reports for community content (posts +
 * comments). Distinct from {@link Report} which models flood-incident
 * reports — same word, very different domain.
 *
 * <p>Lifecycle:</p>
 *   pending → reviewed (admin saw it, no action)
 *           → actioned  (admin removed the content)
 *           → dismissed (admin rejected the report as invalid)
 */
@Entity
@Table(name = "content_reports")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class ContentReport {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** "POST" or "COMMENT" — the reported entity's family. */
    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    /** Internal UUID of the reported post or comment. */
    @Column(name = "target_id", nullable = false, columnDefinition = "uuid")
    private UUID targetId;

    /** User who submitted the report. */
    @Column(name = "reporter_id", nullable = false, columnDefinition = "uuid")
    private UUID reporterId;

    /** Short machine code: spam | harassment | misinformation | off-topic | other. */
    @Column(nullable = false, length = 32)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    /** pending | reviewed | actioned | dismissed. */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = "pending";

    /** Admin user who acted on the report (nullable until acted). */
    @Column(name = "resolved_by", columnDefinition = "uuid")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "pending";
    }
}
