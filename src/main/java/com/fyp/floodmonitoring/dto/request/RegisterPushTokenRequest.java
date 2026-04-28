package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PATCH /settings/push-token.
 * Sent by the mobile app on launch after the user grants notification permission.
 * Mobile sends: { "token": "ExponentPushToken[...]", "platform": "ios"|"android" }
 */
public record RegisterPushTokenRequest(
        @NotBlank(message = "token is required")
        String token
) {}
