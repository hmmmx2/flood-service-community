package com.fyp.floodmonitoring.controller;

import com.fyp.floodmonitoring.dto.SubmitUatSurveyRequest;
import com.fyp.floodmonitoring.dto.UatSurveyResponseDto;
import com.fyp.floodmonitoring.service.UatSurveyService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * UAT survey endpoints — submission is open to any authenticated user
 * (community + CRM); listing and export are admin-only.
 *
 * <pre>
 *   POST /surveys/uat              submit a survey response   (any user)
 *   GET  /admin/surveys/uat        paginated list             (admin)
 *   GET  /admin/surveys/uat/export CSV download for Excel     (admin)
 * </pre>
 */
@RestController
@RequiredArgsConstructor
public class UatSurveyController {

    private final UatSurveyService service;

    @PostMapping("/surveys/uat")
    public ResponseEntity<UatSurveyResponseDto> submit(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody SubmitUatSurveyRequest req
    ) {
        UUID userId = principal != null ? UUID.fromString(principal.getUsername()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(req, userId));
    }

    @GetMapping("/admin/surveys/uat")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS_MANAGER')")
    public ResponseEntity<Page<UatSurveyResponseDto>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false)    String role,
            @RequestParam(required = false)    String source
    ) {
        return ResponseEntity.ok(service.list(role, source, page, size));
    }

    @GetMapping(value = "/admin/surveys/uat/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS_MANAGER')")
    public void exportCsv(HttpServletResponse response) throws IOException {
        // Stamp the filename so re-downloads don't overwrite each other.
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(ZonedDateTime.now(ZoneOffset.UTC));
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"floodwatch-uat-surveys-" + stamp + ".csv\"");
        try (PrintWriter w = response.getWriter()) {
            service.exportCsv(w);
        }
    }
}
