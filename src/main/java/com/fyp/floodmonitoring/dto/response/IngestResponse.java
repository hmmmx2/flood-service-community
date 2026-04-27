package com.fyp.floodmonitoring.dto.response;

/**
 * Response returned to the IoT device after a successful ingest.
 */
public record IngestResponse(
        boolean received,
        String nodeId,
        boolean alertFired
) {}
