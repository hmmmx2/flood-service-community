package com.fyp.floodmonitoring.dto;

import java.time.Instant;

/** Read-shape for a user's saved location pin. */
public record SavedLocationDto(
        String id,
        String label,
        String address,
        double latitude,
        double longitude,
        double alertRadiusKm,
        Instant createdAt,
        Instant updatedAt
) {}
