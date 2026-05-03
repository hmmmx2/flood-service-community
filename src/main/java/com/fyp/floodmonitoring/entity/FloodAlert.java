package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "flood_alerts", indexes = {
        @Index(name = "idx_flood_alerts_node_id",   columnList = "node_id"),
        @Index(name = "idx_flood_alerts_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_flood_alerts_acked",      columnList = "acknowledged")
})
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class FloodAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "node_name", nullable = false, length = 255)
    private String nodeName;

    @Column(name = "water_level_meters", nullable = false)
    private Double waterLevelMeters;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FloodSeverity severity;

    /** Human-readable area / zone name (copied from Node.area at ingest time). */
    @Column(length = 255)
    private String zone;

    @Column(nullable = false)
    @Builder.Default
    private boolean acknowledged = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
}
