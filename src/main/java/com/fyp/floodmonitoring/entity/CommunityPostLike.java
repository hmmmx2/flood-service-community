package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "community_post_likes")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@IdClass(CommunityPostLike.LikeId.class)
public class CommunityPostLike {

    @Id
    @Column(name = "post_id", columnDefinition = "uuid")
    private UUID postId;

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LikeId implements Serializable {
        private UUID postId;
        private UUID userId;
    }
}
