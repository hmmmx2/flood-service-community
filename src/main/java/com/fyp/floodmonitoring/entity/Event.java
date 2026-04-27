package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code events} table (200,500 rows).
 *
 * <p>Stores every water-level change emitted by an IoT node.
 * Indexed on {@code node_id}, {@code created_at DESC}, and {@code new_level}
 * for fast analytics and feed queries.</p>
 */
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_events_node_id", columnList = "node_id"),
        @Index(name = "idx_events_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_events_new_level", columnList = "new_level")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Event {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "new_level")
    private Integer newLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
