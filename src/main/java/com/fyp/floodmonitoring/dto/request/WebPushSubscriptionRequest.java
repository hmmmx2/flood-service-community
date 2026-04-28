package com.fyp.floodmonitoring.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Browser Web Push API subscription object sent from the community website.
 * See: https://developer.mozilla.org/en-US/docs/Web/API/PushSubscription
 */
public record WebPushSubscriptionRequest(
        @NotBlank String endpoint,
        String expirationTime,
        Keys keys
) {
    public record Keys(
            @NotBlank String p256dh,
            @NotBlank String auth
    ) {}
}
