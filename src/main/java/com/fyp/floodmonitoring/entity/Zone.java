package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Maps to the {@code zones} table — flood risk zone polygons stored as GeoJSON JSONB. */
@Entity
@Table(name = "zones")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Zone {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    /** "low" | "medium" | "high" | "extreme" */
    @Column(name = "risk_level", nullable = false, length = 10)
    private String riskLevel;

    /** GeoJSON Polygon stored as raw JSONB string. */
    @Column(nullable = false, columnDefinition = "jsonb")
    private String boundary;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
