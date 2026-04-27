package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.UpdateProfileRequest;
import com.fyp.floodmonitoring.dto.response.UserProfileDto;
import com.fyp.floodmonitoring.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Authenticated profile endpoints.
 *
 * <pre>
 * GET   /profile  — fetch the current user's profile
 * PATCH /profile  — update one or more profile fields (partial update)
 * </pre>
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<UserProfileDto> getProfile(
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(profileService.getProfile(userId));
    }

    @PatchMapping
    public ResponseEntity<UserProfileDto> updateProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(profileService.updateProfile(userId, req));
    }
}
