package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.RegisterPushTokenRequest;
import com.fyp.floodmonitoring.dto.request.UpdateSettingRequest;
import com.fyp.floodmonitoring.dto.request.WebPushSubscriptionRequest;
import com.fyp.floodmonitoring.dto.response.SettingsDto;
import com.fyp.floodmonitoring.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * <pre>
 * GET   /settings   — retrieve all notification settings → [{key, enabled}]
 * PATCH /settings   — toggle a specific setting on or off → [{key, enabled}]
 * </pre>
 */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<SettingsDto.SettingItemDto>> getSettings(
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(settingsService.getSettings(userId));
    }

    @PatchMapping
    public ResponseEntity<List<SettingsDto.SettingItemDto>> updateSetting(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateSettingRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(settingsService.updateSetting(userId, req));
    }

    /** PATCH /settings/push-token — registers or updates the Expo push token for this device. */
    @PatchMapping("/push-token")
    public ResponseEntity<Void> registerPushToken(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody RegisterPushTokenRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        settingsService.registerPushToken(userId, req.token());
        return ResponseEntity.noContent().build();
    }

    /** POST /settings/push-subscription — save a browser Web Push subscription. */
    @PostMapping("/push-subscription")
    public ResponseEntity<Void> saveWebPushSubscription(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody WebPushSubscriptionRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        settingsService.saveWebPushSubscription(userId, req);
        return ResponseEntity.noContent().build();
    }

    /** DELETE /settings/push-subscription — remove a browser Web Push subscription. */
    @DeleteMapping("/push-subscription")
    public ResponseEntity<Void> removeWebPushSubscription(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody java.util.Map<String, String> body) {

        UUID userId = UUID.fromString(principal.getUsername());
        String endpoint = body.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        settingsService.removeWebPushSubscription(userId, endpoint);
        return ResponseEntity.noContent().build();
    }
}
