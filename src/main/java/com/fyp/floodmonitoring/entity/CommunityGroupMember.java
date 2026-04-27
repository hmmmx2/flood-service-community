package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "community_group_members")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@IdClass(CommunityGroupMember.MemberId.class)
public class CommunityGroupMember {

    @Id
    @Column(name = "group_id", columnDefinition = "uuid")
    private UUID groupId;

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "joined_at", updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onCreate() { joinedAt = Instant.now(); }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberId implements Serializable {
        private UUID groupId;
        private UUID userId;
    }
}
