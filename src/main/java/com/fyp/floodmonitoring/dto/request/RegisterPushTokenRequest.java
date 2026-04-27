package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PATCH /settings/push-token.
 * Sent by the mobile app on launch after the user grants notification permission.
 */
public record RegisterPushTokenRequest(
        @NotBlank(message = "pushToken is required")
        String pushToken
) {}
