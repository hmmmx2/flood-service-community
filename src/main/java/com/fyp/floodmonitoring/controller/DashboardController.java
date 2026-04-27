package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.DashboardNodeRowDto;
import com.fyp.floodmonitoring.dto.response.DashboardTimeSeriesDto;
import com.fyp.floodmonitoring.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <pre>
 * GET /dashboard/nodes        — all nodes as status-table rows
 * GET /dashboard/time-series  — monthly + yearly event counts
 * </pre>
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/nodes")
    public ResponseEntity<List<DashboardNodeRowDto>> getDashboardNodes() {
        return ResponseEntity.ok(dashboardService.getDashboardNodes());
    }

    @GetMapping("/time-series")
    public ResponseEntity<DashboardTimeSeriesDto> getTimeSeries() {
        return ResponseEntity.ok(dashboardService.getTimeSeries());
    }
}
