package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.CreateReportRequest;
import com.fyp.floodmonitoring.dto.request.UpdateReportStatusRequest;
import com.fyp.floodmonitoring.dto.response.ReportDto;
import com.fyp.floodmonitoring.entity.Report;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Manages user-submitted flood incident reports (SCRUM-105). */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    @Transactional(readOnly = true)
    public List<ReportDto> getAll() {
        return reportRepository.findAllByOrderBySubmittedAtDesc()
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto> getByStatus(String status) {
        return reportRepository.findByStatusOrderBySubmittedAtDesc(status)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public ReportDto create(UUID userId, CreateReportRequest req) {
        Report report = Report.builder()
                .userId(userId)
                .latitude(req.latitude())
                .longitude(req.longitude())
                .severity(req.severity() != null ? req.severity() : "warning")
                .description(req.description())
                .photoUrl(req.photoUrl())
                .status("pending")
                .submittedAt(Instant.now())
                .build();
        return toDto(reportRepository.save(report));
    }

    @Transactional
    public ReportDto updateStatus(UUID reportId, UpdateReportStatusRequest req) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> AppException.notFound("Report not found: " + reportId));

        report.setStatus(req.status());
        return toDto(reportRepository.save(report));
    }

    private ReportDto toDto(Report r) {
        return new ReportDto(
                r.getId().toString(),
                r.getUserId().toString(),
                r.getLatitude(),
                r.getLongitude(),
                r.getSeverity(),
                r.getDescription(),
                r.getPhotoUrl(),
                r.getStatus(),
                r.getSubmittedAt() != null ? r.getSubmittedAt().toString() : null);
    }
}
