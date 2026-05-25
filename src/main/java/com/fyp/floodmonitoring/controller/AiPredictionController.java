package com.fyp.floodmonitoring.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/ai-predict")
public class AiPredictionController {

    private final RestClient restClient;
    private final String apiKey;

    public AiPredictionController(
            @Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${ai.service.api-key:}") String apiKey) {
        this.restClient = RestClient.builder().baseUrl(aiServiceUrl).build();
        this.apiKey = apiKey;
    }

    @GetMapping
    public ResponseEntity<?> predict(
            @RequestParam(defaultValue = "monthly") String scale,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String date) {
        String endpoint = endpointFor(scale, year, date);
        if (endpoint == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid prediction query"));
        }
        return proxyGet(endpoint);
    }

    @PostMapping("/node")
    public ResponseEntity<?> predictNode(@RequestBody Map<String, Object> body) {
        return restClient.post()
                .uri("/api/v1/predict/node")
                .headers(this::addServiceKey)
                .body(body)
                .retrieve()
                .toEntity(Object.class);
    }

    private ResponseEntity<?> proxyGet(String endpoint) {
        return restClient.get()
                .uri(endpoint)
                .headers(this::addServiceKey)
                .retrieve()
                .toEntity(Object.class);
    }

    private void addServiceKey(HttpHeaders headers) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-AI-Service-Key", apiKey);
        }
    }

    private String endpointFor(String scale, Integer year, String date) {
        int safeYear = year == null ? LocalDate.now().getYear() : year;
        if (safeYear < 2020 || safeYear > 2100) return null;
        return switch (scale) {
            case "daily", "weekly", "monthly" ->
                    "/api/v1/predict/" + scale + "?year=" + safeYear;
            case "hourly" -> {
                if (date == null || date.isBlank()) yield null;
                try {
                    yield "/api/v1/predict/hourly?date=" + LocalDate.parse(date);
                } catch (DateTimeParseException ex) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
