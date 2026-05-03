package com.fyp.floodmonitoring.dto.response;

import com.fyp.floodmonitoring.entity.FloodAlert;

/**
 * Matches the mobile FloodAlertPayload TypeScript interface exactly.
 * waterLevelFeet is derived at serialization time so the mobile app
 * never needs to do unit conversion.
 */
public record FloodAlertDto(
        Long   id,
        String type,            // always "flood_alert" — mobile checks this to dispatch the alarm
        String nodeId,
        String nodeName,
        double waterLevelFeet,
        String severity,        // "WATCH" | "WARNING" | "CRITICAL"
        String zone,
        String timestamp,       // ISO-8601 LocalDateTime
        boolean acknowledged
) {
    public static FloodAlertDto from(FloodAlert a) {
        return new FloodAlertDto(
                a.getId(),
                "flood_alert",
                a.getNodeId(),
                a.getNodeName(),
                a.getWaterLevelMeters() * 3.28084,
                a.getSeverity().name(),
                a.getZone(),
                a.getCreatedAt() != null ? a.getCreatedAt().toString() : null,
                a.isAcknowledged()
        );
    }
}
