package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /auth/change-password.
 * The caller must be authenticated (Bearer token required).
 * Verifies the current password before applying the new one.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8, message = "New password must be at least 8 characters") String newPassword
) {}
