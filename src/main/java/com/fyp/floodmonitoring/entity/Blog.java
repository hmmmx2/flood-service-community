package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Blog articles shown in the mobile app and managed via the CRM dashboard.
 */
@Entity
@Table(name = "blogs")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Blog {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Bundled image asset key e.g. "blog-1", "blog-2". Used as fallback when imageUrl is absent. */
    @Column(name = "image_key", length = 50)
    private String imageKey;

    /**
     * Hero image — either an external URL or, when uploaded via the CRM/web
     * uploader, a base64 `data:` URL (the image is resized to 1280×720 but
     * still encodes to tens of KB). Must be TEXT: the old VARCHAR(500) cap
     * overflowed on every uploaded image, throwing a "value too long"
     * SQL error that surfaced as a generic 500 ("An unexpected error
     * occurred") and blocked blog creation whenever a Hero image was set.
     * Mirrors the user `avatar_url` TEXT column, which stores the same kind
     * of payload.
     */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** Content category e.g. "Flood Alert", "Safety Tips", "Community", "Updates". */
    @Column(name = "category", length = 100)
    private String category;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_featured")
    private Boolean isFeatured;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
