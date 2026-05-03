package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.FloodAlertDto;
import com.fyp.floodmonitoring.repository.FloodAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API for flood alerts — consumed by the mobile app's alerts screen.
 *
 * GET  /flood-alerts         public — returns alerts from the last 24 hours (history)
 * GET  /flood-alerts/active  public — returns unacknowledged alerts only (badge count)
 * POST /flood-alerts/{id}/acknowledge  authenticated — user dismisses an alert in-app
 */
@RestController
@RequestMapping("/flood-alerts")
@RequiredArgsConstructor
public class FloodAlertController {

    private final FloodAlertRepository alertRepo;

    @GetMapping
    public List<FloodAlertDto> getRecent() {
        return alertRepo.findRecent(LocalDateTime.now().minusHours(24))
                .stream()
                .map(FloodAlertDto::from)
                .toList();
    }

    @GetMapping("/active")
    public List<FloodAlertDto> getActive() {
        return alertRepo.findByAcknowledgedFalseOrderByCreatedAtDesc()
                .stream()
                .map(FloodAlertDto::from)
                .toList();
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable Long id) {
        alertRepo.findById(id).ifPresent(alert -> {
            alert.setAcknowledged(true);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alertRepo.save(alert);
        });
        return ResponseEntity.noContent().build();
    }
}
