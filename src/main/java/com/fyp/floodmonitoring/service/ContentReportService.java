package com.fyp.floodmonitoring.service;

import com.fyp.floodmonitoring.dto.request.ReportContentRequest;
import com.fyp.floodmonitoring.dto.request.UpdateContentReportRequest;
import com.fyp.floodmonitoring.dto.response.ContentReportDto;
import com.fyp.floodmonitoring.entity.*;
import com.fyp.floodmonitoring.exception.AppException;
import com.fyp.floodmonitoring.repository.*;
import com.fyp.floodmonitoring.service.notifications.InAppProvider;
import com.fyp.floodmonitoring.service.notifications.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight content-moderation service. Lets community users flag a
 * post or comment for review and exposes the moderation queue + status
 * mutations for the CRM admin UI. New reports notify every staff user
 * via in-app bell so triage is real-time.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentReportService {

    private static final Set<String> VALID_REASONS = Set.of(
            "spam", "harassment", "misinformation", "off-topic", "other");
    private static final Set<String> VALID_STATUSES = Set.of(
            "pending", "reviewed", "actioned", "dismissed");

    private final ContentReportRepository    reportRepo;
    private final CommunityPostRepository    postRepo;
    private final CommunityCommentRepository commentRepo;
    private final UserRepository             userRepo;
    private final InAppProvider              inAppProvider;

    // ── User-facing: file a report ────────────────────────────────────────────

    @Transactional
    public ContentReportDto reportPost(UUID postId, UUID reporterId, ReportContentRequest req) {
        CommunityPost post = postRepo.findById(postId)
                .orElseThrow(() -> AppException.notFound("Post not found"));
        return submit("POST", post.getId(), reporterId, req,
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getId() : null,
                post.getAuthor() != null ? displayName(post.getAuthor()) : "—",
                post.getId(),
                post.getTitle());
    }

    @Transactional
    public ContentReportDto reportComment(UUID postId, UUID commentId, UUID reporterId, ReportContentRequest req) {
        CommunityComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> AppException.notFound("Comment not found"));
        if (!c.getPost().getId().equals(postId)) {
            throw AppException.badRequest("INVALID_POST", "Comment does not belong to this post");
        }
        return submit("COMMENT", c.getId(), reporterId, req,
                c.getContent(),
                c.getAuthor() != null ? c.getAuthor().getId() : null,
                c.getAuthor() != null ? displayName(c.getAuthor()) : "—",
                c.getPost().getId(),
                c.getPost().getTitle());
    }

    private ContentReportDto submit(
            String targetType, UUID targetId, UUID reporterId, ReportContentRequest req,
            String snippet, UUID targetAuthorId, String targetAuthorName,
            UUID parentPostId, String parentPostTitle) {

        String reason = req.reason() == null ? "" : req.reason().trim().toLowerCase();
        if (!VALID_REASONS.contains(reason)) {
            throw AppException.badRequest("INVALID_REASON",
                    "Reason must be one of: " + String.join(", ", VALID_REASONS));
        }

        if (reportRepo.existsByReporterIdAndTargetTypeAndTargetId(reporterId, targetType, targetId)) {
            throw AppException.conflict("You've already reported this " + targetType.toLowerCase() + ".");
        }

        ContentReport row = ContentReport.builder()
                .targetType(targetType)
                .targetId(targetId)
                .reporterId(reporterId)
                .reason(reason)
                .details(req.details() == null ? null : req.details().trim())
                .status("pending")
                .build();
        row = reportRepo.save(row);

        notifyStaff(row, snippet, targetAuthorName);

        User reporter = userRepo.findById(reporterId).orElse(null);
        return toDto(row, snippet, targetAuthorId, targetAuthorName,
                parentPostId, parentPostTitle, reporter);
    }

    // ── Admin-facing: queue + actions ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<ContentReportDto> listForModeration(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<ContentReport> rows = reportRepo.findAllForModeration(PageRequest.of(page, safeSize));
        return rows.map(this::toDtoEnriched);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return reportRepo.countPending();
    }

    @Transactional
    public ContentReportDto updateStatus(UUID reportId, UUID adminId, UpdateContentReportRequest req) {
        String next = req.status() == null ? "" : req.status().trim().toLowerCase();
        if (!VALID_STATUSES.contains(next)) {
            throw AppException.badRequest("INVALID_STATUS",
                    "Status must be one of: " + String.join(", ", VALID_STATUSES));
        }
        ContentReport row = reportRepo.findById(reportId)
                .orElseThrow(() -> AppException.notFound("Report not found"));
        row.setStatus(next);
        if ("pending".equals(next)) {
            row.setResolvedBy(null);
            row.setResolvedAt(null);
        } else {
            row.setResolvedBy(adminId);
            row.setResolvedAt(Instant.now());
        }
        return toDtoEnriched(reportRepo.save(row));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void notifyStaff(ContentReport row, String snippet, String targetAuthorName) {
        try {
            String shortSnippet = trimSnippet(snippet);
            String title = "New report: " + row.getTargetType().toLowerCase()
                    + " by " + targetAuthorName;
            String body = "Reason: " + row.getReason()
                    + (shortSnippet.isEmpty() ? "" : " — \"" + shortSnippet + "\"");
            String link = "/admin/content-reports";
            for (User staff : userRepo.findStaff()) {
                if (staff == null) continue;
                inAppProvider.deliver(staff.getId(), new NotificationPayload(
                        "moderation.report",
                        "warning",
                        title,
                        body,
                        null,
                        link));
            }
        } catch (Exception e) {
            log.warn("[ContentReport] Failed to notify staff: {}", e.getMessage());
        }
    }

    private ContentReportDto toDtoEnriched(ContentReport row) {
        // Hydrate the reported entity even when used by the admin queue
        // — we keep a snapshot of title/snippet/author so a deleted
        // post/comment still shows what was reported.
        String snippet = "(content unavailable)";
        UUID targetAuthorId = null;
        String targetAuthorName = "—";
        UUID parentPostId = null;
        String parentPostTitle = "—";

        if ("POST".equalsIgnoreCase(row.getTargetType())) {
            CommunityPost p = postRepo.findById(row.getTargetId()).orElse(null);
            if (p != null) {
                snippet = p.getTitle();
                if (p.getAuthor() != null) {
                    targetAuthorId = p.getAuthor().getId();
                    targetAuthorName = displayName(p.getAuthor());
                }
                parentPostId = p.getId();
                parentPostTitle = p.getTitle();
            }
        } else if ("COMMENT".equalsIgnoreCase(row.getTargetType())) {
            CommunityComment c = commentRepo.findById(row.getTargetId()).orElse(null);
            if (c != null) {
                snippet = c.getContent();
                if (c.getAuthor() != null) {
                    targetAuthorId = c.getAuthor().getId();
                    targetAuthorName = displayName(c.getAuthor());
                }
                parentPostId = c.getPost().getId();
                parentPostTitle = c.getPost().getTitle();
            }
        }

        User reporter = userRepo.findById(row.getReporterId()).orElse(null);
        return toDto(row, snippet, targetAuthorId, targetAuthorName,
                parentPostId, parentPostTitle, reporter);
    }

    private ContentReportDto toDto(
            ContentReport row, String snippet, UUID targetAuthorId, String targetAuthorName,
            UUID parentPostId, String parentPostTitle, User reporter) {

        User resolver = row.getResolvedBy() != null
                ? userRepo.findById(row.getResolvedBy()).orElse(null)
                : null;

        return new ContentReportDto(
                row.getId().toString(),
                row.getTargetType(),
                row.getTargetId().toString(),
                trimSnippet(snippet),
                targetAuthorId != null ? targetAuthorId.toString() : null,
                targetAuthorName,
                parentPostId != null ? parentPostId.toString() : null,
                parentPostTitle,
                row.getReporterId().toString(),
                reporter != null ? displayName(reporter) : "—",
                row.getReason(),
                row.getDetails(),
                row.getStatus(),
                resolver != null ? resolver.getId().toString() : null,
                resolver != null ? displayName(resolver) : null,
                row.getResolvedAt(),
                row.getCreatedAt());
    }

    private static String displayName(User u) {
        String first = u.getFirstName() == null ? "" : u.getFirstName();
        String last = u.getLastName() == null ? "" : u.getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }

    private static String trimSnippet(String s) {
        if (s == null) return "";
        String collapsed = s.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= 200 ? collapsed : collapsed.substring(0, 199) + "…";
    }
}
