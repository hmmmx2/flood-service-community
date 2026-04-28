package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.request.IngestRequest;
import com.fyp.floodmonitoring.dto.response.IngestResponse;
import com.fyp.floodmonitoring.service.IngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /ingest — IoT sensor data ingestion endpoint.
 *
 * Validates the X-API-Key header against {@code app.ingest.api-key}.
 * Permitted in SecurityConfig via .requestMatchers(POST, "/ingest").permitAll()
 * (key validation is handled here, not via JWT).
 */
@RestController
@RequestMapping("/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final IngestService ingestService;

    @Value("${app.ingest.api-key:}")
    private String apiKey;

    @PostMapping
    public ResponseEntity<?> ingest(
            @RequestHeader(value = "X-API-Key", required = false) String requestApiKey,
            @Valid @RequestBody IngestRequest request) {

        if (apiKey.isBlank() || !apiKey.equals(requestApiKey)) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", "UNAUTHORIZED", "message", "Invalid or missing API key"));
        }
        return ResponseEntity.accepted().body(ingestService.ingest(request));
    }
}
