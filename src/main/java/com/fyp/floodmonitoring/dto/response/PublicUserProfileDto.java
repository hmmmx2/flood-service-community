package com.fyp.floodmonitoring.dto.response;

import java.time.Instant;

/**
 * Public-facing user profile shown on /u/{id}. Strictly derived from
 * the {@code users} table — no email or phone, no internal flags.
 */
public record PublicUserProfileDto(
        String id,
        String displayName,
        String avatarUrl,
        String role,
        Instant createdAt,
        long postCount,
        long commentCount
) {}
