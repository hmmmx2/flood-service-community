package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.CreateReportRequest;
import com.fyp.floodmonitoring.dto.request.UpdateReportStatusRequest;
import com.fyp.floodmonitoring.dto.response.ReportDto;
import com.fyp.floodmonitoring.service.ReportService;
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
 * Flood incident report endpoints (SCRUM-105).
 *
 * <pre>
 *   GET   /reports                — list all reports (admin only)
 *   POST  /reports                — submit new report (any auth user)
 *   PATCH /reports/{id}/status    — update status (admin only)
 * </pre>
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReportDto>> getAll(
            @RequestParam(required = false) String status) {

        List<ReportDto> reports = status != null
                ? reportService.getByStatus(status)
                : reportService.getAll();
        return ResponseEntity.ok(reports);
    }

    @PostMapping
    public ResponseEntity<ReportDto> create(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateReportRequest req) {

        UUID userId = UUID.fromString(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.create(userId, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportDto> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateReportStatusRequest req) {

        return ResponseEntity.ok(reportService.updateStatus(id, req));
    }
}
