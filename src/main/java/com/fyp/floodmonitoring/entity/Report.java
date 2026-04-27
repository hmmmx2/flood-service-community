package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Maps to the {@code reports} table — user-submitted flood incident reports. */
@Entity
@Table(name = "reports")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Report {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    /** "warning" | "critical" | "info" */
    @Column(nullable = false, length = 20)
    private String severity;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "photo_url", columnDefinition = "TEXT")
    private String photoUrl;

    /** "pending" | "reviewed" | "resolved" */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
