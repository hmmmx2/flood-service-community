package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateReportRequest(
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude,
        String severity,
        String description,
        String photoUrl
) {}
