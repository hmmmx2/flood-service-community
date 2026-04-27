package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.RegisterPushTokenRequest;
import com.fyp.floodmonitoring.dto.request.UpdateSettingRequest;
import com.fyp.floodmonitoring.dto.response.SettingsDto;
import com.fyp.floodmonitoring.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * <pre>
 * GET   /settings   — retrieve all notification settings for the current user
 * PATCH /settings   — toggle a specific setting on or off
 * </pre>
 */
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<SettingsDto> getSettings(
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(settingsService.getSettings(userId));
    }

    @PatchMapping
    public ResponseEntity<SettingsDto> updateSetting(
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
        settingsService.registerPushToken(userId, req.pushToken());
        return ResponseEntity.noContent().build();
    }
}
