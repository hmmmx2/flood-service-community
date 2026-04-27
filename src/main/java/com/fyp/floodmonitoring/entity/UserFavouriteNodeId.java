package com.fyp.floodmonitoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

/** Composite primary key for the {@code user_favourite_nodes} join table. */
@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class UserFavouriteNodeId implements Serializable {

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "node_id", columnDefinition = "uuid")
    private UUID nodeId;
}
