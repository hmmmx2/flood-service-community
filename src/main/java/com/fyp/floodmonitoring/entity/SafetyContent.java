package com.fyp.floodmonitoring.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps to the {@code safety_content} table.
 * Stores community flood safety guide text keyed by section and language.
 */
@Entity
@Table(name = "safety_content")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class SafetyContent {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /** One of: before | during | after | contacts | zones */
    @Column(nullable = false, length = 10)
    private String section;

    @Column(nullable = false, length = 5)
    private String lang;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
