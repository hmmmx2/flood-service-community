package com.fyp.floodmonitoring.dto.response;

/** Represents a flood risk zone with a GeoJSON polygon boundary. */
public record ZoneDto(
        String id,
        String name,
        String riskLevel,
        String boundary,    // raw GeoJSON string — parsed by the client
        String updatedAt
) {}
