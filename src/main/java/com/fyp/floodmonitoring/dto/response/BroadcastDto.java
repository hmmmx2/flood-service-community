package com.fyp.floodmonitoring.dto.response;

/** Represents a sent emergency broadcast. */
public record BroadcastDto(
        String id,
        String title,
        String body,
        String targetZone,
        String severity,
        String sentBy,
        String sentAt,
        int recipientCount
) {}
