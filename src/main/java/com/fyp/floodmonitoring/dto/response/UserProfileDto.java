package com.fyp.floodmonitoring.dto.response;

/**
 * Full user profile returned from GET /profile and PATCH /profile.
 * Includes separate first/last name fields alongside {@code displayName} for
 * compatibility with both web (uses displayName) and mobile (uses firstName/lastName).
 */
public record UserProfileDto(
        String id,
        String email,
        String firstName,
        String lastName,
        String displayName,     // convenience: firstName + " " + lastName
        String role,
        String phone,
        String locationLabel,
        String avatarUrl
) {}
