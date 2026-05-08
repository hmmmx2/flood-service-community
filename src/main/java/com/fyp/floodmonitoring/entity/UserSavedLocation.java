package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's personal pinned location ("Home", "Workplace", "Parent's House",
 * etc.) with a per-pin alert radius. When any sensor inside the radius
 * crosses Alert / Warning / Critical, the user is notified via the
 * channels they've enabled in user_settings (email / push / SMS /
 * WhatsApp).
 *
 * Independent of {@link UserFavouriteNode} which represents
 * "I've starred a specific sensor for quick reading" — different concept,
 * different surface, kept separate on purpose.
 *
 * Schema: see migrations/006_geocoding_and_saved_locations.sql.
 */
@Entity
@Table(name = "user_saved_locations")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class UserSavedLocation {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    /** Alert radius in kilometres. Constrained 1.0–50.0 by both the
     *  database CHECK and the service-layer validator. */
    @Column(name = "alert_radius_km", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal alertRadiusKm = new BigDecimal("5.0");

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;
}
