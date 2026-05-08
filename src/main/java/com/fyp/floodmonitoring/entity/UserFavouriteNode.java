package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Maps to the {@code user_favourite_nodes} join table.
 *
 * <p>Per-favourite channel preferences ({@code email_enabled},
 * {@code sms_enabled}, {@code whatsapp_enabled}, {@code push_enabled})
 * let the user pick exactly which channels deliver alerts for THIS sensor,
 * overriding their global notification preferences. All four default to
 * {@code true} when a favourite is first created so existing rows after
 * the Hibernate auto-migration keep working.</p>
 */
@Entity
@Table(name = "user_favourite_nodes")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class UserFavouriteNode {

    @EmbeddedId
    private UserFavouriteNodeId id;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled = Boolean.TRUE;

    @Column(name = "sms_enabled", nullable = false)
    private Boolean smsEnabled = Boolean.TRUE;

    @Column(name = "whatsapp_enabled", nullable = false)
    private Boolean whatsappEnabled = Boolean.TRUE;

    @Column(name = "push_enabled", nullable = false)
    private Boolean pushEnabled = Boolean.TRUE;

    public UserFavouriteNode(UserFavouriteNodeId id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.emailEnabled = Boolean.TRUE;
        this.smsEnabled = Boolean.TRUE;
        this.whatsappEnabled = Boolean.TRUE;
        this.pushEnabled = Boolean.TRUE;
    }
}
