package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code nodes} table.
 * Represents a physical IoT water-level sensor node in Kuching, Sarawak.
 *
 * <p>{@code currentLevel} (0–3): 0 = dry, 1 = normal, 2 = warning, 3 = critical.</p>
 */
@Entity
@Table(name = "nodes")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Node {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** IoT device identifier (business key, e.g. "102782478", "tet01"). */
    @Column(name = "node_id", unique = true, nullable = false, length = 100)
    private String nodeId;

    @Column(length = 255)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** 0=dry, 1=normal, 2=warning, 3=critical */
    @Column(name = "current_level")
    private Integer currentLevel;

    @Column(name = "is_dead")
    private Boolean isDead;

    @Column(length = 255)
    private String area;

    @Column(length = 255)
    private String location;

    @Column(length = 100)
    private String state;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
