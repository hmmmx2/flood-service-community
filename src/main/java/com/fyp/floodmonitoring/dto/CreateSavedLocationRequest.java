package com.fyp.floodmonitoring.dto;

import jakarta.validation.constraints.*;

public record CreateSavedLocationRequest(
        @NotBlank @Size(max = 80) String label,
        @Size(max = 1024) String address,
        @NotNull @DecimalMin("-90.0")  @DecimalMax("90.0")  Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull @DecimalMin("1.0")  @DecimalMax("50.0") Double alertRadiusKm
) {}
