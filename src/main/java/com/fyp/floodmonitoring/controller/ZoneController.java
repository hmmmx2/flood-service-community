package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.ZoneDto;
import com.fyp.floodmonitoring.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Flood risk zone endpoints (SCRUM-106).
 *
 * <pre>
 *   GET /zones      — list all zones (any auth user)
 *   GET /zones/{id} — get single zone by UUID
 * </pre>
 */
@RestController
@RequestMapping("/zones")
@RequiredArgsConstructor
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    public ResponseEntity<List<ZoneDto>> getAll() {
        return ResponseEntity.ok(zoneService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ZoneDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(zoneService.getById(id));
    }
}
