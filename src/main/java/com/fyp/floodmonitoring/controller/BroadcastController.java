package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.CreateBroadcastRequest;
import com.fyp.floodmonitoring.dto.response.BroadcastDto;
import com.fyp.floodmonitoring.service.BroadcastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Emergency broadcast endpoints (SCRUM-104).
 *
 * <pre>
 *   GET  /broadcasts  — list all (any authenticated user)
 *   POST /broadcasts  — send new broadcast (admin only)
 * </pre>
 */
@RestController
@RequestMapping("/broadcasts")
@RequiredArgsConstructor
public class BroadcastController {

    private final BroadcastService broadcastService;

    @GetMapping
    public ResponseEntity<List<BroadcastDto>> getAll() {
        return ResponseEntity.ok(broadcastService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BroadcastDto> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateBroadcastRequest req) {

        UUID adminId = UUID.fromString(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(broadcastService.create(adminId, req));
    }
}
