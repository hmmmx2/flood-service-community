package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.IngestRequest;
import com.fyp.floodmonitoring.dto.response.IngestResponse;
import com.fyp.floodmonitoring.service.IngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * POST /ingest — IoT sensor data ingestion endpoint.
 *
 * No authentication required (IoT devices do not carry JWT).
 * Future: add X-API-Key header validation for device auth.
 *
 * Permitted in SecurityConfig via .requestMatchers(POST, "/ingest").permitAll()
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;

    @PostMapping
    public ResponseEntity<IngestResponse> ingest(@Valid @RequestBody IngestRequest request) {
        return ResponseEntity.accepted().body(ingestService.ingest(request));
    }
}
