package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial-update request for PATCH /profile.
 * All fields are optional — only non-null values are applied.
 */
public record UpdateProfileRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 50)  String phone,
        @Size(max = 255) String locationLabel,
        @Size(max = 2048) String avatarUrl   // optional — null means no change
) {}
