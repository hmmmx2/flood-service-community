package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Payload sent by an IoT sensor node to POST /ingest.
 * No authentication required — devices authenticate via a shared API key header (future work).
 */
public record IngestRequest(

        @NotBlank(message = "nodeId is required")
        String nodeId,

        @NotNull(message = "level is required")
        @Min(value = 0, message = "level must be 0–3")
        @Max(value = 3, message = "level must be 0–3")
        Integer level,

        /** ISO-8601 timestamp from device clock. Defaults to now() if null. */
        Instant timestamp,

        Double waterLevelMeters,
        Double temperature,
        Double humidity,
        Double latitude,
        Double longitude
) {}
