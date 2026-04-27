package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "community_groups")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class CommunityGroup {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** URL-safe slug, e.g. "kuching-floods" */
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Single letter shown as group avatar when no icon image is set */
    @Column(name = "icon_letter", length = 2)
    private String iconLetter;

    @Column(name = "icon_color", length = 20)
    @Builder.Default
    private String iconColor = "#ed1c24";

    @Column(name = "members_count", nullable = false)
    @Builder.Default
    private int membersCount = 0;

    @Column(name = "posts_count", nullable = false)
    @Builder.Default
    private int postsCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (iconLetter == null && name != null && !name.isEmpty()) {
            iconLetter = name.substring(0, 1).toUpperCase();
        }
    }
}
