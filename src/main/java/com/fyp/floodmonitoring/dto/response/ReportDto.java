package com.fyp.floodmonitoring.dto.response;

import java.math.BigDecimal;

/** Represents a user-submitted flood incident report. */
public record ReportDto(
        String id,
        String userId,
        BigDecimal latitude,
        BigDecimal longitude,
        String severity,
        String description,
        String photoUrl,
        String status,
        String submittedAt
) {}
