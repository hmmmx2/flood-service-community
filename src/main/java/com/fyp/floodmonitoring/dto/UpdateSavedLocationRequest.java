package com.fyp.floodmonitoring.dto;

import jakarta.validation.constraints.*;

/** Partial update — every field is optional; nulls are skipped. */
public record UpdateSavedLocationRequest(
        @Size(max = 80) String label,
        @Size(max = 1024) String address,
        @DecimalMin("-90.0")  @DecimalMax("90.0")  Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("1.0")  @DecimalMax("50.0") Double alertRadiusKm
) {}
