package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Maps to the {@code user_favourite_nodes} join table. */
@Entity
@Table(name = "user_favourite_nodes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserFavouriteNode {

    @EmbeddedId
    private UserFavouriteNodeId id;

    @Column(name = "created_at")
    private Instant createdAt;
}
