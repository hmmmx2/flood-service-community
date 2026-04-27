package com.fyp.floodmonitoring.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single item in the activity feed.
 * The {@code kind} field acts as a discriminator:
 * <ul>
 *   <li>{@code "alert"} — high water level detected (severity = warning|critical, waterLevelMeters set)</li>
 *   <li>{@code "update"} — water level returned to normal (notes set)</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedItemDto(
        String  id,
        String  kind,           // "alert" | "update"
        String  title,
        String  summary,
        String  createdAt,
        String  sensorId,
        String  severity,       // "warning" | "critical" | "normal"

        // alert-only fields
        Double  waterLevelMeters,

        // update-only fields
        String  notes
) {

    /** Factory — alert item (new_level >= 2). */
    public static FeedItemDto alert(String id, String sensorId, String createdAt,
                                    String severity, double waterLevelMeters) {
        String title   = "Water Level Alert — Node " + sensorId;
        String summary = String.format("Water level reached %.1fm at node %s.", waterLevelMeters, sensorId);
        return new FeedItemDto(id, "alert", title, summary, createdAt, sensorId,
                severity, waterLevelMeters, null);
    }

    /** Factory — update item (new_level < 2). */
    public static FeedItemDto update(String id, String sensorId, String createdAt,
                                     int level, double waterLevelMeters) {
        String title   = "Water Level Normal — Node " + sensorId;
        String summary = String.format("Water level dropped to %.1fm at node %s.", waterLevelMeters, sensorId);
        String notes   = String.format("Current level: %d (%.1fm). Monitoring continues.", level, waterLevelMeters);
        return new FeedItemDto(id, "update", title, summary, createdAt, sensorId,
                "normal", null, notes);
    }
}
