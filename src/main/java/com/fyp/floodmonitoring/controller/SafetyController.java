package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.response.SafetyContentDto;
import com.fyp.floodmonitoring.service.SafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /safety — returns community flood safety awareness content (SCRUM-103).
 */
@RestController
@RequestMapping("/safety")
@RequiredArgsConstructor
public class SafetyController {

    private final SafetyService safetyService;

    @GetMapping
    public ResponseEntity<List<SafetyContentDto>> getSafety(
            @RequestParam(defaultValue = "en") String lang) {
        return ResponseEntity.ok(safetyService.getSafety(lang));
    }
}
