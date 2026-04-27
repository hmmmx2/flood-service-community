package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "community_posts")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CommunityPost {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** Nullable — posts not in any group appear in the global feed only */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private CommunityGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** base64 data URL or external URL — stored as TEXT */
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private int likesCount = 0;

    @Column(name = "comments_count", nullable = false)
    @Builder.Default
    private int commentsCount = 0;

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
