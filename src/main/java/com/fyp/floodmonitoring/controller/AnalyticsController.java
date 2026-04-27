package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.AnalyticsDataDto;
import com.fyp.floodmonitoring.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /analytics — aggregated statistics and chart data. */
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsDataDto> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getAnalytics());
    }
}
