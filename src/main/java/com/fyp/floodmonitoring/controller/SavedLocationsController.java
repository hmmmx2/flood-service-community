package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.CreateSavedLocationRequest;
import com.fyp.floodmonitoring.dto.SavedLocationDto;
import com.fyp.floodmonitoring.dto.UpdateSavedLocationRequest;
import com.fyp.floodmonitoring.service.SavedLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 *   GET    /saved-locations         list mine
 *   POST   /saved-locations         create a new pin
 *   PATCH  /saved-locations/{id}    update label / lat / lng / radius
 *   DELETE /saved-locations/{id}    delete a pin
 *
 * Every route requires authentication; the auth filter populates the
 * principal whose username is the user UUID. Ownership is enforced in
 * the service layer for PATCH / DELETE.
 */
@RestController
@RequestMapping("/saved-locations")
@RequiredArgsConstructor
public class SavedLocationsController {

    private final SavedLocationService service;

    @GetMapping
    public ResponseEntity<List<SavedLocationDto>> list(
            @AuthenticationPrincipal UserDetails principal) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(service.listForUser(userId));
    }

    @PostMapping
    public ResponseEntity<SavedLocationDto> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateSavedLocationRequest req) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SavedLocationDto> update(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSavedLocationRequest req) {
        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(principal.getUsername());
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
