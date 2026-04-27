package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Maps to the {@code broadcasts} table — emergency messages sent by admins. */
@Entity
@Table(name = "broadcasts")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Broadcast {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "target_zone", nullable = false, length = 100)
    private String targetZone;

    /** "warning" | "critical" | "info" */
    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "sent_by", columnDefinition = "uuid")
    private UUID sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "recipient_count")
    private Integer recipientCount;
}
