package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.service.GeocodeBackfillRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoint to (re)run the Google Maps reverse-geocoding backfill
 * over every node with a NULL address. Idempotent — already-geocoded
 * rows are skipped, so this is safe to call after onboarding new
 * sensors.
 *
 *   POST /admin/nodes/geocode-backfill   ADMIN only
 *   →    { "updated": 42 }
 */
@RestController
@RequestMapping("/admin/nodes")
@RequiredArgsConstructor
public class AdminGeocodingController {

    private final GeocodeBackfillRunner runner;

    @PostMapping("/geocode-backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> backfill() {
        int updated = runner.backfillAll();
        return ResponseEntity.ok(Map.of(
                "updated", updated,
                "message", updated == 0
                        ? "No nodes needed geocoding (already populated, or service unavailable)."
                        : "Geocoded " + updated + " node(s) successfully."
        ));
    }
}
